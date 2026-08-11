import { useRoute, Link } from 'wouter';
import {
  useGetAttempt,
  useGetSession,
  useGetQuiz,
  getGetAttemptQueryKey,
  getGetSessionQueryKey,
  getGetQuizQueryKey
} from '@workspace/api-client-react';
import { EtudiantLayout } from '@/components/layout/etudiant-layout';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Progress } from '@/components/ui/progress';
import {
  ArrowLeft,
  CheckCircle2,
  LayoutDashboard,
  Target,
  Trophy,
  XCircle,
  Minus,
  Brain,
} from 'lucide-react';
import { format } from 'date-fns';
import { fr } from 'date-fns/locale';

// ── Types locaux pour les détails BERTScore ────────────────────────────────────
interface BertScoreDetail {
  questionId: string;
  questionContent: string;
  studentAnswer: string;
  referenceAnswer: string;
  f1: number;
  precision: number;
  recall: number;
  partialScore: number;
  label: 'CORRECT' | 'PARTIEL' | 'INCORRECT';
  model: string;
}

// ── Helpers ────────────────────────────────────────────────────────────────────

function BertScoreBadge({ label, f1 }: { label: string; f1: number }) {
  const cfg = {
    CORRECT:   { cls: 'bg-green-100 border-green-200 text-green-800',  icon: <CheckCircle2 size={14} /> },
    PARTIEL:   { cls: 'bg-amber-100 border-amber-200 text-amber-800',  icon: <Minus size={14} /> },
    INCORRECT: { cls: 'bg-red-100 border-red-200 text-red-800',        icon: <XCircle size={14} /> },
  }[label] ?? { cls: 'bg-slate-100 border-slate-200 text-slate-600', icon: null };

  return (
    <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded border text-xs font-semibold ${cfg.cls}`}>
      {cfg.icon}
      {label} — F1 {(f1 * 100).toFixed(0)}%
    </span>
  );
}

function BertScorePanel({ detail }: { detail: BertScoreDetail }) {
  return (
    <div className="mt-4 p-4 bg-violet-50 border border-violet-100 rounded-lg space-y-3">
      <div className="flex items-center gap-2">
        <Brain size={15} className="text-violet-600 shrink-0" />
        <span className="text-xs font-semibold text-violet-700 uppercase tracking-wider">
          Correction sémantique BERTScore
        </span>
        <BertScoreBadge label={detail.label} f1={detail.f1} />
      </div>

      {/* Score bar */}
      <div className="space-y-1">
        <div className="flex justify-between text-xs text-violet-600 font-medium">
          <span>Score partiel</span>
          <span>{(detail.partialScore * 100).toFixed(0)} / 100</span>
        </div>
        <Progress
          value={detail.partialScore * 100}
          className="h-2 bg-violet-100 [&>div]:bg-violet-500"
        />
      </div>

      {/* F1 detail */}
      <div className="grid grid-cols-3 gap-2 text-center">
        {[
          { label: 'F1',       value: detail.f1 },
          { label: 'Précision', value: detail.precision },
          { label: 'Rappel',   value: detail.recall },
        ].map(({ label, value }) => (
          <div key={label} className="bg-white border border-violet-100 rounded p-2">
            <p className="text-xs text-muted-foreground">{label}</p>
            <p className="font-bold text-violet-700 text-sm">{(value * 100).toFixed(1)}%</p>
          </div>
        ))}
      </div>

      <p className="text-xs text-violet-500 italic">
        Modèle : {detail.model} — Seuil validation : F1 ≥ 70%
      </p>
    </div>
  );
}

// ── Composant principal ────────────────────────────────────────────────────────

export default function Resultats() {
  const [match, params] = useRoute('/etudiant/resultats/:attemptId');
  const attemptId = params?.attemptId || '';

  const { data: attempt, isLoading: isLoadingAttempt } = useGetAttempt(attemptId, {
    query: { enabled: !!attemptId, queryKey: getGetAttemptQueryKey(attemptId) }
  });

  const sessionId = attempt?.sessionId?.toString() || '';
  const { data: session, isLoading: isLoadingSession } = useGetSession(sessionId, {
    query: { enabled: !!sessionId, queryKey: getGetSessionQueryKey(sessionId) }
  });

  const quizId = session?.quizId?.toString() || '';
  const { data: quiz, isLoading: isLoadingQuiz } = useGetQuiz(quizId, {
    query: { enabled: !!quizId, queryKey: getGetQuizQueryKey(quizId) }
  });

  if (!match) return null;

  if (isLoadingAttempt || isLoadingSession || isLoadingQuiz) {
    return (
      <EtudiantLayout>
        <div className="flex justify-center p-12 text-muted-foreground">Chargement des résultats…</div>
      </EtudiantLayout>
    );
  }

  if (!attempt || !quiz || !session) {
    return (
      <EtudiantLayout>
        <div className="text-center p-12 text-muted-foreground">Résultats introuvables.</div>
      </EtudiantLayout>
    );
  }

  const scorePercent = attempt.scorePercent ?? 0;
  const isSuccess    = scorePercent >= 60;
  const questions    = (quiz as any).questions ?? [];
  const answers      = ((attempt as any).answers ?? {}) as Record<string, string>;

  // Map des détails BERTScore par questionId
  const bertMap: Record<string, BertScoreDetail> = {};
  const rawBert = (attempt as any).bertScoreDetails as BertScoreDetail[] | undefined;
  if (Array.isArray(rawBert)) {
    for (const d of rawBert) bertMap[d.questionId] = d;
  }

  return (
    <EtudiantLayout>
      <div className="max-w-4xl mx-auto space-y-8">

        {/* Header */}
        <div className="flex items-center gap-3">
          <Button variant="ghost" size="icon" onClick={() => window.history.back()}>
            <ArrowLeft size={20} />
          </Button>
          <div>
            <h1 className="text-2xl font-bold tracking-tight">Résultats : {quiz.title}</h1>
            <p className="text-sm text-muted-foreground mt-1">
              {attempt.completedAt
                ? `Soumis le ${format(new Date(attempt.completedAt as string), "d MMMM yyyy 'à' HH:mm", { locale: fr })}`
                : ''}
            </p>
          </div>
        </div>

        {/* Score global */}
        <div className="grid sm:grid-cols-3 gap-6">
          <Card className={`sm:col-span-2 border-2 ${isSuccess ? 'border-green-200 bg-green-50/30' : 'border-red-200 bg-red-50/30'}`}>
            <CardContent className="p-8 flex flex-col sm:flex-row items-center justify-between gap-6 text-center sm:text-left">
              <div>
                <Badge
                  variant="outline"
                  className={`mb-3 px-3 py-1 text-sm font-semibold ${
                    isSuccess
                      ? 'bg-green-100 text-green-800 border-green-200'
                      : 'bg-red-100 text-red-800 border-red-200'
                  }`}
                >
                  {isSuccess ? 'Module validé' : 'Non validé'}
                </Badge>
                <h2 className="text-5xl font-extrabold tracking-tight mb-2">
                  {Math.round(scorePercent)}%
                </h2>
                <p className="text-muted-foreground font-medium flex items-center justify-center sm:justify-start gap-1.5">
                  <Target size={16} />
                  Score final : {attempt.score} / {attempt.maxScore} points
                </p>
                {rawBert && rawBert.length > 0 && (
                  <p className="text-xs text-violet-600 mt-2 flex items-center gap-1">
                    <Brain size={12} />
                    Inclut la correction sémantique BERTScore ({rawBert.length} question{rawBert.length > 1 ? 's' : ''} ouverte{rawBert.length > 1 ? 's' : ''})
                  </p>
                )}
              </div>
              <div className={`p-6 rounded-full ${isSuccess ? 'bg-green-100 text-green-600' : 'bg-red-100 text-red-600'}`}>
                {isSuccess ? <Trophy size={48} /> : <XCircle size={48} />}
              </div>
            </CardContent>
          </Card>

          <Card className="flex flex-col justify-center">
            <CardContent className="p-6 space-y-4">
              <Button asChild className="w-full" variant="outline">
                <Link href="/etudiant/historique">Voir l'historique</Link>
              </Button>
              <Button asChild className="w-full">
                <Link href="/etudiant">
                  <LayoutDashboard size={16} className="mr-2" />
                  Tableau de bord
                </Link>
              </Button>
            </CardContent>
          </Card>
        </div>

        {/* Correction détaillée */}
        <div className="space-y-6">
          <h3 className="text-xl font-bold border-b pb-2">Correction détaillée</h3>

          <div className="space-y-6">
            {questions.map((q: any, i: number) => {
              const studentAnswer = answers[q.id] ?? '';
              const isQcm        = q.type === 'QCM';
              const isOpenType   = q.type === 'OUVERTE' || q.type === 'EXERCICE';
              const isQcmCorrect = isQcm && studentAnswer.trim().toLowerCase() === (q.correctAnswer ?? '').trim().toLowerCase();
              const bertDetail   = isOpenType ? bertMap[q.id] : undefined;

              return (
                <Card key={q.id} className="overflow-hidden">
                  <div className="bg-slate-50 border-b px-4 py-3 flex items-center justify-between">
                    <span className="font-semibold text-sm">Question {i + 1}</span>
                    <div className="flex items-center gap-2">
                      <Badge variant="secondary" className="text-xs">{q.type}</Badge>
                      {isQcm && (
                        isQcmCorrect
                          ? <Badge className="bg-green-100 text-green-700 border-green-200 text-xs">Correct</Badge>
                          : <Badge className="bg-red-100 text-red-700 border-red-200 text-xs">Incorrect</Badge>
                      )}
                      {isOpenType && bertDetail && (
                        <BertScoreBadge label={bertDetail.label} f1={bertDetail.f1} />
                      )}
                    </div>
                  </div>

                  <CardContent className="p-4 sm:p-6 space-y-4">
                    <p className="text-base font-medium text-foreground leading-relaxed">{q.content}</p>

                    <div className="grid sm:grid-cols-2 gap-4 pt-2">
                      {/* Réponse étudiant */}
                      <div className="space-y-2">
                        <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">
                          Votre réponse
                        </p>
                        <div className={`p-4 rounded-md border min-h-[60px] ${
                          isQcm
                            ? (isQcmCorrect
                                ? 'bg-green-50 border-green-200 text-green-900'
                                : 'bg-red-50 border-red-200 text-red-900')
                            : 'bg-slate-50 border-slate-200 text-slate-900'
                        }`}>
                          <div className="flex items-start gap-2">
                            {isQcm && (
                              isQcmCorrect
                                ? <CheckCircle2 size={16} className="text-green-600 mt-0.5 shrink-0" />
                                : <XCircle size={16} className="text-red-600 mt-0.5 shrink-0" />
                            )}
                            <span className={!studentAnswer ? 'italic text-muted-foreground text-sm' : 'text-sm'}>
                              {studentAnswer || 'Aucune réponse fournie'}
                            </span>
                          </div>
                        </div>
                      </div>

                      {/* Réponse attendue */}
                      <div className="space-y-2">
                        <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">
                          Réponse attendue
                        </p>
                        <div className="p-4 rounded-md border bg-green-50 border-green-200 text-green-900 min-h-[60px]">
                          <div className="flex items-start gap-2">
                            <CheckCircle2 size={16} className="text-green-600 mt-0.5 shrink-0" />
                            <span className="text-sm font-medium">{q.correctAnswer}</span>
                          </div>
                        </div>
                      </div>
                    </div>

                    {/* Panel BERTScore pour les questions ouvertes */}
                    {isOpenType && bertDetail && (
                      <BertScorePanel detail={bertDetail} />
                    )}

                    {/* Message si question ouverte sans BERTScore (service indisponible) */}
                    {isOpenType && !bertDetail && studentAnswer && (
                      <div className="mt-3 p-3 bg-slate-50 border border-slate-200 rounded-md">
                        <p className="text-xs text-muted-foreground flex items-center gap-1.5">
                          <Brain size={12} />
                          Correction sémantique non disponible pour cette réponse.
                        </p>
                      </div>
                    )}

                    {/* Explication */}
                    {q.explanation && (
                      <div className="mt-4 p-4 bg-blue-50 border border-blue-100 rounded-md text-sm text-blue-900">
                        <p className="font-semibold mb-1">Explication :</p>
                        {q.explanation}
                      </div>
                    )}
                  </CardContent>
                </Card>
              );
            })}
          </div>
        </div>
      </div>
    </EtudiantLayout>
  );
}
