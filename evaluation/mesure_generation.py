#!/usr/bin/env python3
"""
QuizGen — Mesure de la génération (sans questions de référence)
===============================================================

Mesure ce qui ne dépend pas d'un corpus annoté, sur les documents déjà
importés dans l'application :
  - temps de génération (création du quiz → état REVIEWING), moyenne et P95
    (exigence NF-01 : 10 questions en ≤ 15 s au P95) ;
  - nombre de questions produites / demandées ;
  - statistiques du contrôle qualité journalisées par le worker
    (questions brutes, acceptées, rejetées, finales) → taux de rejet réel.

Les métriques BLEU / ROUGE-L / BERTScore exigent des références rédigées par
un humain : elles relèvent de evaluate.py, pas de ce script.

Usage :
  python evaluation/mesure_generation.py --token "$QUIZGEN_API_TOKEN" \
    --llm "Mistral 7B via Ollama (CPU)" --runs 3 \
    --output evaluation/mesure_generation_offline.json
"""
import argparse
import ast
import datetime
import json
import logging
import os
import re
import subprocess
import sys
import time
from dataclasses import asdict, dataclass, field, fields

import requests

from evaluate import THRESHOLDS, QuizGenClient, _git_commit, percentile_nearest_rank

logger = logging.getLogger(__name__)

LOG_TS = re.compile(r"^\[(\d{4}-\d\d-\d\d \d\d:\d\d:\d\d),(\d{3})")
TASK_RECEIVED = re.compile(r"Task quizgen\.generate_quiz\[([^\]]+)\] received")
PIPELINE_START = re.compile(r"Pipeline v2 — document_id=([0-9a-f-]{36})")
PIPELINE_DONE = re.compile(r"Pipeline v2 terminé : (\{.*\})")
TASK_SUCCEEDED = re.compile(r"Task quizgen\.generate_quiz\[([^\]]+)\] succeeded in ([0-9.]+)s")
# Écart maximal toléré entre la création du quiz (base) et la réception de la tâche (worker)
MATCH_WINDOW_S = 15.0
# Temps bout en bout − temps worker au-delà duquel la mesure a attendu en file derrière une
# autre génération : elle est exclue des statistiques. Les horloges de l'hôte et de la VM
# Docker dérivent d'environ ±5 % sous charge, d'où un seuil bien au-dessus de ce bruit.
QUEUE_EXCLUSION_S = 20.0


@dataclass
class GenerationRun:
    """Une génération mesurée."""
    document: str
    document_id: str
    pages: int
    run: int
    requested: int
    generated: int
    time_s: float                 # bout en bout : création du quiz → REVIEWING
    status: str = "OK"
    worker_time_s: float = -1.0   # traitement par le worker (journal « succeeded in »)
    pipeline: dict = field(default_factory=dict)
    quiz_id: str = ""
    created_at: str = ""          # horodatage de création du quiz (API), pour l'association

    @property
    def queue_s(self) -> float:
        """Temps hors traitement worker (file d'attente, scrutation, API)."""
        return self.time_s - self.worker_time_s if self.worker_time_s >= 0 else -1.0


def fetch_token(kc_url: str, realm: str, client_id: str, user: str, password: str) -> str:
    """Jeton par mot de passe (direct grant activé sur le Keycloak local)."""
    resp = requests.post(
        f"{kc_url}/realms/{realm}/protocol/openid-connect/token",
        data={"grant_type": "password", "client_id": client_id,
              "username": user, "password": password},
        timeout=90,
    )
    resp.raise_for_status()
    return resp.json()["access_token"]


def list_documents(client: QuizGenClient) -> list[dict]:
    """Tous les documents de l'enseignant connecté (pagination parcourue)."""
    docs, page = [], 0
    while True:
        resp = client.session.get(f"{client.base_url}/documents",
                                  params={"page": page, "size": 50}, timeout=30)
        resp.raise_for_status()
        body = resp.json()
        docs.extend(body["content"])
        if body["last"]:
            return docs
        page += 1


def worker_tasks(since: str, service: str) -> list[dict]:
    """
    Générations traitées par le worker depuis `since` : identifiant de tâche,
    heure de réception, document, durée de traitement et statistiques du
    contrôle qualité. Le worker a une concurrence de 1 : les lignes de
    traitement d'une tâche se suivent.
    """
    try:
        out = subprocess.run(
            ["docker", "compose", "logs", "--no-log-prefix", "--since", since, service],
            capture_output=True, text=True, encoding="utf-8", errors="replace", check=True,
        ).stdout
    except (OSError, subprocess.CalledProcessError) as exc:
        logger.warning("Journaux du worker indisponibles : %s", exc)
        return []
    received: dict[str, str] = {}
    tasks, current = [], None
    for line in out.splitlines():
        if (m := TASK_RECEIVED.search(line)) and (ts := LOG_TS.search(line)):
            received[m.group(1)] = f"{ts.group(1)}.{ts.group(2)}"
        elif m := PIPELINE_START.search(line):
            current = {"document_id": m.group(1), "duree_worker_s": -1.0, "stats": {}}
        elif current and (m := PIPELINE_DONE.search(line)):
            try:
                current["stats"] = ast.literal_eval(m.group(1))
            except (ValueError, SyntaxError):
                logger.warning("Ligne de statistiques illisible : %.120s", line)
        elif current and (m := TASK_SUCCEEDED.search(line)):
            current.update(task_id=m.group(1), duree_worker_s=float(m.group(2)),
                           recu_le=received.get(m.group(1), ""))
            tasks.append(current)
            current = None
    return tasks


def _parse_ts(value: str) -> datetime.datetime | None:
    try:
        return datetime.datetime.fromisoformat(value.replace("T", " ")[:26])
    except ValueError:
        return None


def attach_worker_tasks(runs: list[GenerationRun], tasks: list[dict]) -> None:
    """
    Associe chaque génération réussie à la tâche worker du même document reçue
    au plus près de la création de son quiz (fenêtre MATCH_WINDOW_S). Une tâche
    orpheline (génération abandonnée côté client) ne peut donc pas être prise
    pour une autre exécution du même document.
    """
    used: set[int] = set()
    for r in runs:
        created = _parse_ts(r.created_at)
        if r.status != "OK" or created is None:
            continue
        best, best_gap = None, MATCH_WINDOW_S
        for i, t in enumerate(tasks):
            recu = _parse_ts(t.get("recu_le", ""))
            if i in used or t["document_id"] != r.document_id or recu is None:
                continue
            gap = abs((recu - created).total_seconds())
            if gap <= best_gap:
                best, best_gap = i, gap
        if best is not None:
            used.add(best)
            r.worker_time_s, r.pipeline = tasks[best]["duree_worker_s"], tasks[best]["stats"]
        else:
            logger.warning("Aucune tâche worker associée à %s #%d", r.document, r.run)


def quiz_index(client: QuizGenClient) -> dict[str, list[dict]]:
    """Quiz de l'enseignant indexés par titre (id, statut, date de création)."""
    index: dict[str, list[dict]] = {}
    page = 0
    while True:
        resp = client.session.get(f"{client.base_url}/quizzes", params={"page": page, "size": 50}, timeout=30)
        resp.raise_for_status()
        body = resp.json()
        for q in body["content"]:
            index.setdefault(q["title"], []).append(q)
        if body["last"]:
            return index
        page += 1


def fill_quiz_refs(runs: list[GenerationRun], index: dict[str, list[dict]]) -> None:
    """Complète quiz_id / created_at des mesures qui ne les ont pas (campagnes antérieures)."""
    for r in runs:
        if r.status != "OK" or r.created_at:
            continue
        candidates = sorted(
            (q for q in index.get(f"Mesure {r.document[:40]} #{r.run}", [])
             if q["quizStatus"] in ("REVIEWING", "PUBLISHED")),
            key=lambda q: q["createdAt"],
        )
        if candidates:
            r.quiz_id, r.created_at = candidates[0]["id"], candidates[0]["createdAt"]


def _time_stats(values: list[float]) -> dict:
    return {
        "moyenne_s": sum(values) / len(values) if values else 0.0,
        "p95_s": percentile_nearest_rank(values, 95),
        "min_s": min(values) if values else 0.0,
        "max_s": max(values) if values else 0.0,
        "n": len(values),
    }


def is_queued(run: GenerationRun) -> bool:
    """La mesure inclut une attente en file derrière une autre génération."""
    return run.worker_time_s >= 0 and run.queue_s > QUEUE_EXCLUSION_S


def summarize(runs: list[GenerationRun]) -> dict:
    ok = [r for r in runs if r.status == "OK" and not is_queued(r)]
    excluded = [f"{r.document} #{r.run}" for r in runs if r.status == "OK" and is_queued(r)]
    times = [r.time_s for r in ok]
    worker = [r.worker_time_s for r in ok if r.worker_time_s >= 0]
    raw = sum(r.pipeline.get("questions_generated_raw", 0) for r in ok)
    rejected = sum(r.pipeline.get("questions_rejected", 0) for r in ok)
    return {
        "generations_ok": len(ok),
        "generations_total": len(runs),
        "generations_exclues_attente": excluded,
        "temps_moyen_s": sum(times) / len(times) if times else 0.0,
        "temps_p95_s": percentile_nearest_rank(times, 95),
        "temps_min_s": min(times) if times else 0.0,
        "temps_max_s": max(times) if times else 0.0,
        "nf01_respectee": bool(times) and percentile_nearest_rank(times, 95) <= THRESHOLDS["p95_s"],
        "temps_worker": _time_stats(worker),
        "attente_max_s": max((r.queue_s for r in ok if r.worker_time_s >= 0), default=0.0),
        "questions_demandees": sum(r.requested for r in runs),
        "questions_produites": sum(r.generated for r in ok),
        "questions_brutes_llm": raw,
        "questions_rejetees_qc": rejected,
        "taux_rejet_qc": rejected / raw if raw else None,
        "stats_pipeline_disponibles": sum(1 for r in ok if r.pipeline),
    }


def main() -> None:
    for stream in (sys.stdout, sys.stderr):
        if hasattr(stream, "reconfigure"):
            stream.reconfigure(encoding="utf-8")
    logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")

    parser = argparse.ArgumentParser(description="QuizGen — mesure de la génération")
    parser.add_argument("--api", default="http://localhost:8080/api/v1")
    parser.add_argument("--token", default=os.environ.get("QUIZGEN_API_TOKEN", ""))
    parser.add_argument("--llm", required=True, help="LLM réellement utilisé (reporté tel quel)")
    parser.add_argument("--n-questions", type=int, default=10)
    parser.add_argument("--difficulty", default="MOYEN", choices=["FACILE", "MOYEN", "DIFFICILE"])
    parser.add_argument("--runs", type=int, default=3)
    parser.add_argument("--max-docs", type=int, default=5, help="Nombre maximal de documents mesurés")
    parser.add_argument("--max-wait", type=int, default=1200)
    parser.add_argument("--poll-interval", type=float, default=1.0)
    parser.add_argument("--worker-service", default="nlp-worker")
    parser.add_argument("--output", required=True, help="Fichier JSON de résultats")
    parser.add_argument("--kc-url", default="http://localhost:8180")
    parser.add_argument("--kc-realm", default="quizgen")
    parser.add_argument("--kc-client", default="quizgen-frontend")
    parser.add_argument("--kc-user", default=os.environ.get("QUIZGEN_KC_USER", ""),
                        help="Compte enseignant : jeton renouvelé avant chaque génération")
    parser.add_argument("--kc-password", default=os.environ.get("QUIZGEN_KC_PASSWORD", ""))
    parser.add_argument("--rerun-failed", action="store_true",
                        help="Relance seulement les générations en échec du fichier --output existant")
    args = parser.parse_args()

    last_refresh = [0.0]

    def refresh_token() -> None:
        # Renouvelé au plus toutes les 60 s (jeton Keycloak de courte durée). Un échec
        # de renouvellement n'interrompt pas la mesure : le jeton courant reste valide
        # quelques minutes, on réessaiera au prochain appel.
        if args.kc_user and time.monotonic() - last_refresh[0] > 60:
            try:
                token = fetch_token(args.kc_url, args.kc_realm, args.kc_client, args.kc_user, args.kc_password)
            except requests.RequestException as exc:
                if "Bearer pending" in client.session.headers.get("Authorization", ""):
                    raise
                logger.warning("Renouvellement du jeton reporté : %s", exc)
                return
            last_refresh[0] = time.monotonic()
            client.session.headers["Authorization"] = f"Bearer {token}"

    if not args.token and not args.kc_user:
        parser.error("--token (ou QUIZGEN_API_TOKEN) ou --kc-user requis")

    client = QuizGenClient(args.api, args.token or "pending")
    refresh_token()
    docs = list_documents(client)[: args.max_docs]
    if not docs:
        logger.error("Aucun document importé pour ce compte enseignant.")
        sys.exit(1)
    logger.info("%d document(s) mesuré(s) : %s", len(docs), ", ".join(d["originalFilename"] for d in docs))

    def measure(doc: dict, run: int) -> GenerationRun:
        name = doc["originalFilename"]
        base = {"document": name, "document_id": doc["id"], "pages": doc.get("pageCount") or 0,
                "run": run, "requested": args.n_questions}
        logger.info("Génération : %s (exécution %d)", name, run)
        try:
            refresh_token()
            t0 = time.perf_counter()
            quiz_id = client.create_quiz(doc["id"], f"Mesure {name[:40]} #{run}",
                                         args.n_questions, args.difficulty)
            questions = client.wait_for_questions(quiz_id, args.max_wait, args.poll_interval,
                                                  before_poll=refresh_token)
            elapsed = time.perf_counter() - t0
            logger.info("  %d/%d questions en %.1f s", len(questions), args.n_questions, elapsed)
            created = client.session.get(f"{client.base_url}/quizzes/{quiz_id}", timeout=30).json()["createdAt"]
            return GenerationRun(**base, generated=len(questions), time_s=elapsed,
                                 quiz_id=quiz_id, created_at=created)
        except (requests.RequestException, RuntimeError, TimeoutError, KeyError) as exc:
            # Échec consigné tel quel dans les résultats ; la campagne continue
            logger.error("  Échec : %s", exc)
            return GenerationRun(**base, generated=0, time_s=-1.0, status=f"ÉCHEC : {exc}")

    started = datetime.datetime.now(datetime.UTC)
    if args.rerun_failed:
        # Reprise : seules les générations en échec d'une campagne précédente sont relancées
        with open(args.output, encoding="utf-8") as f:
            previous = json.load(f)
        keys = {fl.name for fl in fields(GenerationRun)}
        runs = [GenerationRun(**{k: v for k, v in g.items() if k in keys}) for g in previous["generations"]]
        since = previous["meta"].get("since_utc") or datetime.datetime.strptime(
            previous["meta"]["date"], "%Y-%m-%d %H:%M").astimezone(datetime.UTC).strftime("%Y-%m-%dT%H:%M:%SZ")
        by_id = {d["id"]: d for d in docs}
        for i, r in enumerate(runs):
            if r.status != "OK" and r.document_id in by_id:
                runs[i] = measure(by_id[r.document_id], r.run)
        meta = {**previous["meta"], "repris_le": started.astimezone().strftime("%Y-%m-%d %H:%M")}
    else:
        since = started.strftime("%Y-%m-%dT%H:%M:%SZ")
        runs = [measure(doc, run) for run in range(1, args.runs + 1) for doc in docs]
        meta = {
            "date": started.astimezone().strftime("%Y-%m-%d %H:%M"),
            "since_utc": since,
            "git_commit": _git_commit(),
            "llm": args.llm,
            "runs": args.runs,
            "n_questions": args.n_questions,
            "difficulty": args.difficulty,
            "simulated": False,
            "mesure": "temps de la création du quiz à l'état REVIEWING, scrutation toutes les "
                      f"{args.poll_interval:g} s ; temps worker lu dans son journal",
        }

    refresh_token()
    fill_quiz_refs(runs, quiz_index(client))
    tasks = worker_tasks(since, args.worker_service)
    attach_worker_tasks(runs, tasks)

    result = {
        "meta": meta,
        "seuils": THRESHOLDS,
        "resume": summarize(runs),
        "generations": [{**asdict(r), "queue_s": r.queue_s} for r in runs],
        "taches_worker": tasks,
    }
    with open(args.output, "w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False, indent=2)

    res = result["resume"]
    print("\n" + "=" * 60)
    print(f"  {meta['llm']} — {res['generations_ok']}/{res['generations_total']} générations réussies")
    print(f"  Bout en bout : moyenne {res['temps_moyen_s']:.1f} s | P95 {res['temps_p95_s']:.1f} s "
          f"| NF-01 {'respectée' if res['nf01_respectee'] else 'NON respectée'}")
    tw = res["temps_worker"]
    print(f"  Worker seul  : moyenne {tw['moyenne_s']:.1f} s | P95 {tw['p95_s']:.1f} s (n={tw['n']}) "
          f"| attente max {res['attente_max_s']:.1f} s")
    print(f"  Questions : {res['questions_produites']}/{res['questions_demandees']}")
    if res["taux_rejet_qc"] is not None:
        print(f"  Taux de rejet QC : {res['taux_rejet_qc']:.0%} "
              f"({res['questions_rejetees_qc']}/{res['questions_brutes_llm']})")
    print(f"  → {args.output}")
    print("=" * 60)


if __name__ == "__main__":
    main()
