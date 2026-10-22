#!/usr/bin/env python3
"""
QuizGen — Script d'évaluation qualité IA
========================================

Évalue la qualité des questions générées par le pipeline NLP sur un corpus
de PDFs annotés manuellement (questions de référence rédigées AVANT génération).

Protocole :
  1. Upload du PDF, puis POST /quizzes (génération asynchrone).
  2. Attente de l'état REVIEWING (ou FAILED) ; le temps de génération est
     mesuré de la soumission du quiz à cet état (upload exclu).
  3. Appariement 1-1 de chaque question générée à la référence la plus proche
     (matrice BERTScore + affectation optimale).
  4. Calcul de BLEU-4, ROUGE-L et BERTScore F1 sur les paires appariées.
  5. Répétition --runs fois (génération non déterministe) : moyenne et écart-type.

Métriques :
  - BLEU-4  : recouvrement de n-grammes jusqu'à l'ordre 4 (sacrebleu)
  - ROUGE-L : plus longue sous-séquence commune, F1 (rouge-score)
  - BERTScore F1 : similarité sémantique (bert-score, modèle multilingue)

Usage :
  pip install sacrebleu rouge-score bert-score requests scipy
  python evaluation/evaluate.py \
    --corpus evaluation/corpus/ \
    --api    http://localhost:8080/api/v1 \
    --token  <keycloak_access_token> \
    --runs   3 \
    --output evaluation/rapport_qualite_ia.md \
    --json   evaluation/resultats_evaluation.json
"""
import argparse
import datetime
import json
import logging
import math
import os
import platform
import statistics
import subprocess
import sys
import time
from collections.abc import Callable
from dataclasses import asdict, dataclass, field
from pathlib import Path

# ── Dépendances optionnelles ────────────────────────────────────────────────

try:
    import requests
    HAS_REQUESTS = True
except ImportError:
    HAS_REQUESTS = False
    print("⚠ pip install requests", file=sys.stderr)

try:
    from sacrebleu.metrics import BLEU
    HAS_SACREBLEU = True
except ImportError:
    HAS_SACREBLEU = False
    print("⚠ pip install sacrebleu", file=sys.stderr)

try:
    from rouge_score import rouge_scorer
    HAS_ROUGE = True
except ImportError:
    HAS_ROUGE = False
    print("⚠ pip install rouge-score", file=sys.stderr)

try:
    import bert_score as bs
    HAS_BERTSCORE = True
except ImportError:
    HAS_BERTSCORE = False
    print("⚠ pip install bert-score", file=sys.stderr)

try:
    from scipy.optimize import linear_sum_assignment
    HAS_SCIPY = True
except ImportError:
    HAS_SCIPY = False

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
)
logger = logging.getLogger(__name__)

# Seuils d'acceptation fixés a priori (cahier des charges, NF-01)
THRESHOLDS = {"bleu4": 0.10, "rouge_l": 0.20, "bert_f1": 0.60, "p95_s": 15.0}

# États terminaux du quiz côté backend (QuizStatus)
DONE_STATUSES = {"REVIEWING", "PUBLISHED"}
FAILED_STATUS = "FAILED"


# ── Structures de données ───────────────────────────────────────────────────

@dataclass(frozen=True)
class AnnotatedQuestion:
    """Question de référence annotée manuellement."""
    question_id:       str
    pdf_name:          str
    question_text:     str
    reference_type:    str          # QCM | OUVERTE | EXERCICE
    reference_answers: tuple[str, ...]
    bloom_level:       str = ""


@dataclass(frozen=True)
class GeneratedQuestion:
    """Question générée par le pipeline QuizGen."""
    question_text:  str
    question_type:  str
    correct_answer: str
    options:        tuple[str, ...] | None = None


@dataclass(frozen=True)
class MatchedPair:
    """Une question générée appariée à sa référence la plus proche."""
    reference_id: str
    reference:    str
    generated:    str
    bert_f1:      float


@dataclass
class DocumentResult:
    """Résultat d'évaluation d'un document pour une exécution."""
    pdf_name:          str
    run:               int
    reference_count:   int
    requested_count:   int
    generated_count:   int
    bleu4:             float
    rouge_l:           float
    bert_f1:           float
    generation_time_s: float
    status:            str = "OK"
    pairs:             list[MatchedPair] = field(default_factory=list)


@dataclass
class RunMetadata:
    """Conditions de l'expérience, à reporter dans le mémoire."""
    date:        str
    git_commit:  str
    api:         str
    llm:         str
    bert_model:  str
    machine:     str
    runs:        int
    n_questions: int
    simulated:   bool


# ── API Client ──────────────────────────────────────────────────────────────

class QuizGenClient:
    """Client HTTP pour l'API QuizGen Spring Boot."""

    def __init__(self, base_url: str, token: str):
        self.base_url = base_url.rstrip("/")
        self.session  = requests.Session()
        self.session.headers.update({"Authorization": f"Bearer {token}"})

    def upload_pdf(self, pdf_path: Path) -> str:
        """Upload un PDF et retourne son ID document."""
        with pdf_path.open("rb") as f:
            resp = self.session.post(
                f"{self.base_url}/documents/upload",
                files={"file": (pdf_path.name, f, "application/pdf")},
                timeout=120,
            )
        resp.raise_for_status()
        return resp.json()["id"]

    def create_quiz(self, document_id: str, title: str, n_questions: int,
                    difficulty: str) -> str:
        """Crée un quiz (déclenche la génération asynchrone) et retourne son ID."""
        resp = self.session.post(
            f"{self.base_url}/quizzes",
            json={
                "documentId":    document_id,
                "title":         title,
                "nbQuestions":   n_questions,
                "questionTypes": ["QCM", "OUVERTE"],
                "difficulty":    difficulty,
            },
            timeout=60,
        )
        resp.raise_for_status()
        return resp.json()["id"]

    def wait_for_questions(self, quiz_id: str, max_wait_s: int, interval_s: float,
                           before_poll: Callable[[], None] | None = None) -> list[GeneratedQuestion]:
        """Attend l'état REVIEWING (ou FAILED) et retourne les questions.
        `before_poll` permet de renouveler le jeton pendant une longue attente."""
        start = time.perf_counter()
        while time.perf_counter() - start < max_wait_s:
            if before_poll:
                before_poll()
            resp = self.session.get(f"{self.base_url}/quizzes/{quiz_id}", timeout=30)
            resp.raise_for_status()
            data = resp.json()
            status = data.get("quizStatus", "DRAFT")
            if status in DONE_STATUSES:
                return [_to_generated(q) for q in data.get("questions", [])]
            if status == FAILED_STATUS:
                raise RuntimeError(f"Génération en échec pour le quiz {quiz_id}")
            time.sleep(interval_s)
        raise TimeoutError(f"Quiz {quiz_id} non terminé après {max_wait_s}s")


def _to_generated(q: dict) -> GeneratedQuestion:
    options = q.get("options")
    return GeneratedQuestion(
        question_text=q.get("content", "") or "",
        question_type=q.get("type", "QCM"),
        correct_answer=q.get("correctAnswer", "") or "",
        options=tuple(options) if options else None,
    )


# ── Métriques ────────────────────────────────────────────────────────────────

def bertscore_f1(cands: list[str], refs: list[str], model: str) -> list[float]:
    """BERTScore F1 paire à paire (cands[i] vs refs[i])."""
    if not HAS_BERTSCORE:
        raise RuntimeError("bert-score requis : pip install bert-score")
    _, _, f1 = bs.score(cands=cands, refs=refs, model_type=model, verbose=False)
    return [float(x) for x in f1]


def match_questions(generated: list[GeneratedQuestion],
                    references: list[AnnotatedQuestion],
                    model: str) -> list[MatchedPair]:
    """
    Apparie chaque question générée à une référence distincte en maximisant
    la somme des BERTScore F1 (affectation optimale si scipy est installé,
    sinon gloutonne). L'ordre de génération est arbitraire : comparer
    position par position fausserait les scores.
    """
    gens = [g.question_text for g in generated if g.question_text.strip()]
    if not gens or not references:
        return []
    refs = [r.question_text for r in references]
    flat_c = [g for g in gens for _ in refs]
    flat_r = [r for _ in gens for r in refs]
    flat = bertscore_f1(flat_c, flat_r, model)
    matrix = [flat[i * len(refs):(i + 1) * len(refs)] for i in range(len(gens))]

    if HAS_SCIPY:
        cost = [[-v for v in row] for row in matrix]
        rows, cols = linear_sum_assignment(cost)
        assignment = list(zip(rows.tolist(), cols.tolist()))
    else:
        logger.warning("scipy absent : appariement glouton (pip install scipy)")
        candidates = sorted(
            ((matrix[i][j], i, j) for i in range(len(gens)) for j in range(len(refs))),
            reverse=True,
        )
        used_g: set[int] = set()
        used_r: set[int] = set()
        assignment = []
        for _, i, j in candidates:
            if i not in used_g and j not in used_r:
                assignment.append((i, j))
                used_g.add(i)
                used_r.add(j)

    return [
        MatchedPair(
            reference_id=references[j].question_id,
            reference=refs[j],
            generated=gens[i],
            bert_f1=matrix[i][j],
        )
        for i, j in sorted(assignment)
    ]


def compute_bleu4(pairs: list[MatchedPair]) -> float:
    """BLEU-4 corpus (sacrebleu) sur les paires appariées, normalisé dans [0, 1]."""
    if not HAS_SACREBLEU or not pairs:
        return 0.0
    bleu = BLEU(max_ngram_order=4, smooth_method="exp")
    result = bleu.corpus_score([p.generated for p in pairs], [[p.reference for p in pairs]])
    return float(result.score) / 100.0


def compute_rouge_l(pairs: list[MatchedPair]) -> float:
    """ROUGE-L F1 moyen sur les paires appariées."""
    if not HAS_ROUGE or not pairs:
        return 0.0
    scorer = rouge_scorer.RougeScorer(["rougeL"], use_stemmer=False)
    scores = [scorer.score(p.reference, p.generated)["rougeL"].fmeasure for p in pairs]
    return sum(scores) / len(scores)


def percentile_nearest_rank(values: list[float], pct: float) -> float:
    """Centile par la méthode du rang le plus proche (sur n petit, P95 ≈ max)."""
    if not values:
        return 0.0
    ordered = sorted(values)
    rank = max(1, math.ceil(pct / 100.0 * len(ordered)))
    return ordered[rank - 1]


# ── Corpus ────────────────────────────────────────────────────────────────────

def load_corpus(corpus_dir: Path) -> dict[str, list[AnnotatedQuestion]]:
    """
    Charge les annotations depuis corpus_dir.
    Chaque document est un répertoire avec :
      - document.pdf — le PDF source
      - annotations.json — les questions de référence (cf. annotations.modele.json)
    Les questions vides (modèle non rempli) sont ignorées.
    """
    corpus: dict[str, list[AnnotatedQuestion]] = {}
    if not corpus_dir.exists():
        return corpus

    for doc_dir in sorted(corpus_dir.iterdir()):
        if not doc_dir.is_dir():
            continue
        pdf_path   = doc_dir / "document.pdf"
        annot_path = doc_dir / "annotations.json"
        if not pdf_path.exists() or not annot_path.exists():
            logger.warning("Dossier %s incomplet (document.pdf ou annotations.json manquant)", doc_dir)
            continue

        with annot_path.open(encoding="utf-8") as f:
            raw = json.load(f)

        questions = [
            AnnotatedQuestion(
                question_id=q.get("id", f"q{i}"),
                pdf_name=doc_dir.name,
                question_text=q["question"].strip(),
                reference_type=q.get("type", "QCM"),
                reference_answers=tuple(q.get("correct_answers", [])),
                bloom_level=q.get("bloom_level", ""),
            )
            for i, q in enumerate(raw.get("questions", []))
            if q.get("question", "").strip()
        ]
        if questions:
            corpus[doc_dir.name] = questions
            logger.info("Corpus : %s — %d questions de référence", doc_dir.name, len(questions))
        else:
            logger.warning("Corpus : %s ignoré (aucune question de référence remplie)", doc_dir.name)

    return corpus


# ── Évaluation d'un document ──────────────────────────────────────────────────

def evaluate_document(client: QuizGenClient, doc_name: str, pdf_path: Path,
                      annotations: list[AnnotatedQuestion], args: argparse.Namespace,
                      run: int) -> DocumentResult:
    """Évalue la génération sur un PDF (une exécution)."""
    logger.info("Évaluation : %s (exécution %d/%d)", doc_name, run, args.runs)
    base = {"pdf_name": doc_name, "run": run, "reference_count": len(annotations),
            "requested_count": args.n_questions}
    try:
        doc_id = client.upload_pdf(pdf_path)
        t0 = time.perf_counter()
        quiz_id = client.create_quiz(doc_id, f"Évaluation {doc_name} #{run}",
                                     args.n_questions, args.difficulty)
        generated = client.wait_for_questions(quiz_id, args.max_wait, args.poll_interval)
        elapsed = time.perf_counter() - t0
    except (requests.RequestException, RuntimeError, TimeoutError, KeyError) as exc:
        # Une génération en échec est consignée dans le rapport, sans interrompre la campagne
        logger.error("Erreur génération %s : %s", doc_name, exc)
        return DocumentResult(**base, generated_count=0, bleu4=0.0, rouge_l=0.0,
                              bert_f1=0.0, generation_time_s=-1.0, status=f"ÉCHEC : {exc}")

    logger.info("  %d questions générées en %.1f s", len(generated), elapsed)
    pairs = match_questions(generated, annotations, args.bert_model)
    bert = sum(p.bert_f1 for p in pairs) / len(pairs) if pairs else 0.0
    result = DocumentResult(
        **base,
        generated_count=len(generated),
        bleu4=compute_bleu4(pairs),
        rouge_l=compute_rouge_l(pairs),
        bert_f1=bert,
        generation_time_s=elapsed,
        pairs=pairs,
    )
    logger.info("  BLEU-4=%.3f ROUGE-L=%.3f BERTScore-F1=%.3f",
                result.bleu4, result.rouge_l, result.bert_f1)
    return result


# ── Agrégation ────────────────────────────────────────────────────────────────

def _mean_std(values: list[float]) -> tuple[float, float]:
    if not values:
        return 0.0, 0.0
    return statistics.fmean(values), (statistics.stdev(values) if len(values) > 1 else 0.0)


def summarize(results: list[DocumentResult]) -> dict:
    """Moyennes globales et par document (sur les exécutions réussies)."""
    ok = [r for r in results if r.status == "OK"]
    times = [r.generation_time_s for r in ok]
    summary = {
        "executions_ok": len(ok),
        "executions_total": len(results),
        "questions_generees": sum(r.generated_count for r in ok),
        "questions_demandees": sum(r.requested_count for r in results),
        "temps_moyen_s": _mean_std(times)[0],
        "temps_p95_s": percentile_nearest_rank(times, 95),
        "temps_max_s": max(times) if times else 0.0,
        "n_mesures_temps": len(times),
    }
    for key in ("bleu4", "rouge_l", "bert_f1"):
        mean, std = _mean_std([getattr(r, key) for r in ok])
        summary[key] = {"moyenne": mean, "ecart_type": std}

    per_doc = {}
    for name in sorted({r.pdf_name for r in results}):
        rs = [r for r in ok if r.pdf_name == name]
        per_doc[name] = {
            "executions_ok": len(rs),
            "questions_generees_moy": _mean_std([float(r.generated_count) for r in rs])[0],
            **{k: _mean_std([getattr(r, k) for r in rs])[0]
               for k in ("bleu4", "rouge_l", "bert_f1", "generation_time_s")},
        }
    summary["par_document"] = per_doc
    return summary


# ── Rapport Markdown ──────────────────────────────────────────────────────────

def generate_markdown_report(meta: RunMetadata, summary: dict, output_path: Path) -> None:
    """Génère le rapport qualité en Markdown."""
    def status(ok: bool) -> str:
        return "✅ Atteint" if ok else "❌ Non atteint"

    def ms(key: str) -> str:
        m = summary[key]
        return f"{m['moyenne']:.4f} ± {m['ecart_type']:.4f}"

    lines = ["# Rapport Qualité IA — QuizGen", ""]
    if meta.simulated:
        lines += [
            "> ⚠️ **DONNÉES SIMULÉES (--dry-run) — NE PAS REPORTER DANS LE MÉMOIRE.**",
            "> Ces valeurs sont tirées au hasard et ne mesurent rien.",
            "",
        ]
    lines += [
        "## Conditions de l'expérience",
        "",
        "| Élément | Valeur |",
        "| --- | --- |",
        f"| Date | {meta.date} |",
        f"| Commit Git | `{meta.git_commit}` |",
        f"| API | {meta.api} |",
        f"| Modèle de génération (LLM) | {meta.llm} |",
        f"| Modèle BERTScore | {meta.bert_model} |",
        f"| Machine | {meta.machine} |",
        f"| Exécutions par document | {meta.runs} |",
        f"| Questions demandées par document | {meta.n_questions} |",
        "",
        "## Résumé global (moyenne ± écart-type sur les exécutions)",
        "",
        "| Métrique | Valeur | Seuil fixé a priori | Statut |",
        "| --- | --- | --- | --- |",
        f"| BLEU-4 | {ms('bleu4')} | ≥ {THRESHOLDS['bleu4']} | {status(summary['bleu4']['moyenne'] >= THRESHOLDS['bleu4'])} |",
        f"| ROUGE-L | {ms('rouge_l')} | ≥ {THRESHOLDS['rouge_l']} | {status(summary['rouge_l']['moyenne'] >= THRESHOLDS['rouge_l'])} |",
        f"| BERTScore F1 | {ms('bert_f1')} | ≥ {THRESHOLDS['bert_f1']} | {status(summary['bert_f1']['moyenne'] >= THRESHOLDS['bert_f1'])} |",
        f"| Temps de génération P95 | {summary['temps_p95_s']:.1f} s | ≤ {THRESHOLDS['p95_s']:.0f} s | {status(summary['temps_p95_s'] <= THRESHOLDS['p95_s'])} |",
        "",
        (f"- Temps moyen : {summary['temps_moyen_s']:.1f} s ; maximum : {summary['temps_max_s']:.1f} s "
         f"(P95 par rang le plus proche sur {summary['n_mesures_temps']} mesures)."),
        (f"- Questions générées : {summary['questions_generees']} sur {summary['questions_demandees']} demandées "
         f"({summary['executions_ok']}/{summary['executions_total']} exécutions réussies)."),
        "",
        "## Résultats par document (moyenne des exécutions)",
        "",
        "| Document | Exéc. OK | Q générées (moy.) | BLEU-4 | ROUGE-L | BERTScore F1 | Temps (s) |",
        "| --- | --- | --- | --- | --- | --- | --- |",
    ]
    for name, d in summary["par_document"].items():
        lines.append(
            f"| {name} | {d['executions_ok']} | {d['questions_generees_moy']:.1f} | {d['bleu4']:.4f} "
            f"| {d['rouge_l']:.4f} | {d['bert_f1']:.4f} | {d['generation_time_s']:.1f} |"
        )
    lines += [
        "",
        "## Méthode",
        "",
        "- Références rédigées à la main avant toute génération (une notion par question).",
        ("- Chaque question générée est appariée à une référence distincte en maximisant la somme des BERTScore F1 "
         "(affectation optimale) ; l'ordre de génération n'intervient pas."),
        "- Temps mesuré de la création du quiz à l'état REVIEWING (upload exclu), par scrutation de l'API.",
        ("- Limites : corpus réduit, un seul annotateur, métriques de forme (BLEU, ROUGE) sévères pour une tâche "
         "où plusieurs formulations sont correctes ; la justesse pédagogique relève du protocole d'évaluation humaine."),
        "",
        "---",
        "_Rapport généré par `evaluation/evaluate.py`._",
    ]
    output_path.write_text("\n".join(lines), encoding="utf-8")
    logger.info("Rapport écrit : %s", output_path)


# ── Main ──────────────────────────────────────────────────────────────────────

def _git_commit() -> str:
    try:
        return subprocess.run(["git", "rev-parse", "--short", "HEAD"], capture_output=True,
                              text=True, check=True).stdout.strip()
    except (OSError, subprocess.CalledProcessError):
        return "inconnu"


def _simulated_results(args: argparse.Namespace) -> list[DocumentResult]:
    """Données aléatoires pour tester la mise en forme du rapport uniquement."""
    import random
    random.seed(42)
    return [
        DocumentResult(
            pdf_name=f"document_{i:02d}", run=run, reference_count=10,
            requested_count=args.n_questions, generated_count=args.n_questions,
            bleu4=random.uniform(0.08, 0.25), rouge_l=random.uniform(0.18, 0.40),
            bert_f1=random.uniform(0.60, 0.82), generation_time_s=random.uniform(8.5, 22.0),
        )
        for i in range(1, 6) for run in range(1, args.runs + 1)
    ]


def main() -> None:
    # Console Windows (cp1252) : forcer l'UTF-8 pour les accents et symboles
    for stream in (sys.stdout, sys.stderr):
        if hasattr(stream, "reconfigure"):
            stream.reconfigure(encoding="utf-8")

    parser = argparse.ArgumentParser(description="QuizGen — Évaluation qualité IA")
    parser.add_argument("--corpus", type=Path, default=Path("evaluation/corpus"),
                        help="Dossier du corpus annoté")
    parser.add_argument("--api", type=str, default="http://localhost:8080/api/v1",
                        help="URL de base de l'API QuizGen")
    parser.add_argument("--token", type=str, default=os.environ.get("QUIZGEN_API_TOKEN", ""),
                        help="Jeton d'accès Keycloak (ou variable QUIZGEN_API_TOKEN)")
    parser.add_argument("--output", type=Path, default=Path("evaluation/rapport_qualite_ia.md"),
                        help="Rapport Markdown")
    parser.add_argument("--json", type=Path, default=Path("evaluation/resultats_evaluation.json"),
                        help="Résultats bruts JSON (paires appariées incluses)")
    parser.add_argument("--n-questions", type=int, default=10,
                        help="Questions demandées par document (3 à 25)")
    parser.add_argument("--difficulty", default="MOYEN", choices=["FACILE", "MOYEN", "DIFFICILE"])
    parser.add_argument("--runs", type=int, default=3,
                        help="Exécutions par document (génération non déterministe)")
    parser.add_argument("--bert-model", default="xlm-roberta-large",
                        help="Modèle BERTScore (multilingue pour le français)")
    parser.add_argument("--llm", default=os.environ.get("QUIZGEN_LLM_LABEL", "Mistral 7B via Ollama"),
                        help="Libellé du LLM réellement utilisé (reporté dans le rapport)")
    parser.add_argument("--max-wait", type=int, default=900, help="Attente maximale par quiz (s)")
    parser.add_argument("--poll-interval", type=float, default=1.0, help="Intervalle de scrutation (s)")
    parser.add_argument("--dry-run", action="store_true",
                        help="Données SIMULÉES pour tester le rapport (jamais pour le mémoire)")
    args = parser.parse_args()

    if not 3 <= args.n_questions <= 25:
        parser.error("--n-questions doit être compris entre 3 et 25")

    if args.dry_run:
        logger.warning("Mode dry-run : données SIMULÉES, rapport marqué comme tel")
        results = _simulated_results(args)
    else:
        if not HAS_REQUESTS or not HAS_BERTSCORE:
            logger.error("Dépendances manquantes : pip install requests bert-score sacrebleu rouge-score scipy")
            sys.exit(1)
        if not args.token:
            logger.error("--token ou variable QUIZGEN_API_TOKEN requis")
            sys.exit(1)
        corpus = load_corpus(args.corpus)
        if not corpus:
            logger.error("Corpus vide ou incomplet dans %s — voir GUIDE_EVALUATION_REELLE.md", args.corpus)
            sys.exit(1)
        client = QuizGenClient(args.api, args.token)
        results = [
            evaluate_document(client, name, args.corpus / name / "document.pdf", annots, args, run)
            for run in range(1, args.runs + 1)
            for name, annots in corpus.items()
        ]

    meta = RunMetadata(
        date=datetime.datetime.now().strftime("%Y-%m-%d %H:%M"),
        git_commit=_git_commit(),
        api=args.api,
        llm=args.llm,
        bert_model=args.bert_model,
        machine=f"{platform.system()} {platform.machine()} — {platform.processor() or 'CPU inconnu'}",
        runs=args.runs,
        n_questions=args.n_questions,
        simulated=args.dry_run,
    )
    summary = summarize(results)

    args.output.parent.mkdir(parents=True, exist_ok=True)
    generate_markdown_report(meta, summary, args.output)
    args.json.write_text(
        json.dumps({"meta": asdict(meta), "seuils": THRESHOLDS, "resume": summary,
                    "executions": [asdict(r) for r in results]},
                   ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    logger.info("Résultats JSON écrits : %s", args.json)

    print("\n" + "=" * 60)
    print(f"  {'SIMULÉ — ' if args.dry_run else ''}{summary['executions_ok']} exécutions réussies")
    print("=" * 60)
    for key, label in (("bleu4", "BLEU-4"), ("rouge_l", "ROUGE-L"), ("bert_f1", "BERTScore F1")):
        print(f"  {label:<13}: {summary[key]['moyenne']:.4f} ± {summary[key]['ecart_type']:.4f}")
    print(f"  Temps moyen  : {summary['temps_moyen_s']:.1f} s")
    print(f"  Temps P95    : {summary['temps_p95_s']:.1f} s")
    print(f"\n  Rapport → {args.output}\n  JSON    → {args.json}")
    print("=" * 60)


if __name__ == "__main__":
    main()
