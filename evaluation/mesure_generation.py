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
from dataclasses import asdict, dataclass, field

import requests
from evaluate import THRESHOLDS, QuizGenClient, _git_commit, percentile_nearest_rank

logger = logging.getLogger(__name__)

PIPELINE_LOG = re.compile(r"Pipeline v2 terminé : (\{.*\})")


@dataclass
class GenerationRun:
    """Une génération mesurée."""
    document: str
    document_id: str
    pages: int
    run: int
    requested: int
    generated: int
    time_s: float
    status: str = "OK"
    pipeline: dict = field(default_factory=dict)


def fetch_token(kc_url: str, realm: str, client_id: str, user: str, password: str) -> str:
    """Jeton par mot de passe (direct grant activé sur le Keycloak local)."""
    resp = requests.post(
        f"{kc_url}/realms/{realm}/protocol/openid-connect/token",
        data={"grant_type": "password", "client_id": client_id,
              "username": user, "password": password},
        timeout=30,
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


def worker_pipeline_stats(since: str, service: str) -> list[dict]:
    """Statistiques « Pipeline v2 terminé » du worker depuis `since` (ordre chronologique)."""
    try:
        out = subprocess.run(
            ["docker", "compose", "logs", "--no-log-prefix", "--since", since, service],
            capture_output=True, text=True, encoding="utf-8", errors="replace", check=True,
        ).stdout
    except (OSError, subprocess.CalledProcessError) as exc:
        logger.warning("Journaux du worker indisponibles : %s", exc)
        return []
    stats = []
    for line in out.splitlines():
        match = PIPELINE_LOG.search(line)
        if match:
            try:
                stats.append(ast.literal_eval(match.group(1)))
            except (ValueError, SyntaxError):
                logger.warning("Ligne de statistiques illisible : %.120s", line)
    return stats


def summarize(runs: list[GenerationRun]) -> dict:
    ok = [r for r in runs if r.status == "OK"]
    times = [r.time_s for r in ok]
    raw = sum(r.pipeline.get("questions_generated_raw", 0) for r in ok)
    rejected = sum(r.pipeline.get("questions_rejected", 0) for r in ok)
    return {
        "generations_ok": len(ok),
        "generations_total": len(runs),
        "temps_moyen_s": sum(times) / len(times) if times else 0.0,
        "temps_p95_s": percentile_nearest_rank(times, 95),
        "temps_min_s": min(times) if times else 0.0,
        "temps_max_s": max(times) if times else 0.0,
        "nf01_respectee": bool(times) and percentile_nearest_rank(times, 95) <= THRESHOLDS["p95_s"],
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
    args = parser.parse_args()

    last_refresh = [0.0]

    def refresh_token() -> None:
        # Renouvelé au plus toutes les 60 s (jeton Keycloak de courte durée)
        if args.kc_user and time.monotonic() - last_refresh[0] > 60:
            last_refresh[0] = time.monotonic()
            token = fetch_token(args.kc_url, args.kc_realm, args.kc_client, args.kc_user, args.kc_password)
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

    started = datetime.datetime.now(datetime.UTC)
    runs: list[GenerationRun] = []
    for run in range(1, args.runs + 1):
        for doc in docs:
            name = doc["originalFilename"]
            base = {"document": name, "document_id": doc["id"], "pages": doc.get("pageCount") or 0,
                    "run": run, "requested": args.n_questions}
            logger.info("Génération : %s (exécution %d/%d)", name, run, args.runs)
            try:
                refresh_token()
                t0 = time.perf_counter()
                quiz_id = client.create_quiz(doc["id"], f"Mesure {name[:40]} #{run}",
                                             args.n_questions, args.difficulty)
                questions = client.wait_for_questions(quiz_id, args.max_wait, args.poll_interval,
                                                      before_poll=refresh_token)
                elapsed = time.perf_counter() - t0
                runs.append(GenerationRun(**base, generated=len(questions), time_s=elapsed))
                logger.info("  %d/%d questions en %.1f s", len(questions), args.n_questions, elapsed)
            except (requests.RequestException, RuntimeError, TimeoutError, KeyError) as exc:
                # Échec consigné tel quel dans les résultats ; la campagne continue
                logger.error("  Échec : %s", exc)
                runs.append(GenerationRun(**base, generated=0, time_s=-1.0, status=f"ÉCHEC : {exc}"))

    # Associer les statistiques du worker aux générations réussies, dans l'ordre
    stats = worker_pipeline_stats(started.strftime("%Y-%m-%dT%H:%M:%SZ"), args.worker_service)
    ok_runs = [r for r in runs if r.status == "OK"]
    if len(stats) == len(ok_runs):
        for r, st in zip(ok_runs, stats):
            r.pipeline = st
    else:
        logger.warning("Statistiques worker : %d lignes pour %d générations réussies — non associées",
                       len(stats), len(ok_runs))

    result = {
        "meta": {
            "date": started.astimezone().strftime("%Y-%m-%d %H:%M"),
            "git_commit": _git_commit(),
            "llm": args.llm,
            "runs": args.runs,
            "n_questions": args.n_questions,
            "difficulty": args.difficulty,
            "simulated": False,
            "mesure": "temps de la création du quiz à l'état REVIEWING, scrutation toutes les "
                      f"{args.poll_interval:g} s",
        },
        "seuils": THRESHOLDS,
        "resume": summarize(runs),
        "generations": [asdict(r) for r in runs],
        "stats_worker_brutes": stats,
    }
    with open(args.output, "w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False, indent=2)

    res = result["resume"]
    print("\n" + "=" * 60)
    print(f"  {args.llm} — {res['generations_ok']}/{res['generations_total']} générations réussies")
    print(f"  Temps moyen : {res['temps_moyen_s']:.1f} s | P95 : {res['temps_p95_s']:.1f} s "
          f"| NF-01 {'respectée' if res['nf01_respectee'] else 'NON respectée'}")
    print(f"  Questions : {res['questions_produites']}/{res['questions_demandees']}")
    if res["taux_rejet_qc"] is not None:
        print(f"  Taux de rejet QC : {res['taux_rejet_qc']:.0%} "
              f"({res['questions_rejetees_qc']}/{res['questions_brutes_llm']})")
    print(f"  → {args.output}")
    print("=" * 60)


if __name__ == "__main__":
    main()
