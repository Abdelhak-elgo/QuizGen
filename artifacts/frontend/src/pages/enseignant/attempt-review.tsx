import { useRoute, Link } from 'wouter';
import { useGetAttemptReview, getGetAttemptReviewQueryKey, QuestionReview } from '@workspace/api-client-react';
import { EnseignantLayout } from '@/components/layout/enseignant-layout';
import { RecommendationPanel } from '@/components/session/recommendation-panel';
import { Card, CardContent, CardHeader } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Progress } from '@/components/ui/progress';
import { ArrowLeft, Loader2 } from 'lucide-react';
import { format } from 'date-fns';
import { fr } from 'date-fns/locale';

const PASSING_PERCENT = 60;

const OUTCOMES: Record<string, { label: string; className: string }> = {
  CORRECT:    { label: 'Juste',              className: 'bg-green-50 text-green-700 border-green-200' },
  PARTIAL:    { label: 'Partiellement juste', className: 'bg-amber-50 text-amber-700 border-amber-200' },
  INCORRECT:  { label: 'Fausse',             className: 'bg-red-50 text-red-700 border-red-200' },
  UNANSWERED: { label: 'Sans réponse',       className: 'bg-slate-100 text-slate-600 border-slate-200' },
  UNGRADED:   { label: 'À corriger',         className: 'bg-blue-50 text-blue-700 border-blue-200' },
};

function QuestionCard({ q, index }: { q: QuestionReview; index: number }) {
  const outcome = OUTCOMES[q.outcome] ?? { label: q.outcome, className: '' };
  return (
    <Card>
      <CardHeader className="pb-2">
        <div className="flex items-center justify-between gap-2 flex-wrap">
          <div className="flex items-center gap-2">
            <span className="text-sm font-semibold text-muted-foreground">Q{index + 1}</span>
            <Badge variant="outline" className="text-xs">{q.type}</Badge>
          </div>
          <Badge variant="outline" className={outcome.className}>
            {outcome.label}
            {q.partialScore != null && ` · ${Math.round(q.partialScore * 100)}%`}
          </Badge>
        </div>
      </CardHeader>
      <CardContent className="space-y-3 text-sm">
        <p className="text-base font-medium leading-relaxed">{q.content}</p>

        {q.type === 'QCM' && q.options && (
          <div className="grid gap-1.5">
            {q.options.map((opt, i) => {
              const isCorrect = opt.trim().toLowerCase() === q.correctAnswer.trim().toLowerCase();
              const isChosen = !!q.studentAnswer && opt.trim().toLowerCase() === q.studentAnswer.trim().toLowerCase();
              const tone = isCorrect
                ? 'bg-green-50 border-green-200 text-green-900 font-medium'
                : isChosen ? 'bg-red-50 border-red-200 text-red-900' : 'bg-slate-50 border-slate-200 text-slate-700';
              return (
                <div key={i} className={`px-3 py-2 rounded-md border flex items-center justify-between gap-2 ${tone}`}>
                  <span>{opt}</span>
                  <span className="text-xs shrink-0">
                    {isChosen && 'Choix de l\'étudiant'}{isChosen && isCorrect && ' · '}{isCorrect && 'Bonne réponse'}
                  </span>
                </div>
              );
            })}
          </div>
        )}

        {q.type !== 'QCM' && (
          <div className="grid md:grid-cols-2 gap-3">
            <div className="rounded-md border p-3 bg-slate-50">
              <p className="text-xs font-semibold text-muted-foreground mb-1">Réponse de l'étudiant</p>
              <p className="whitespace-pre-wrap">{q.studentAnswer || <em className="text-muted-foreground">Aucune réponse</em>}</p>
            </div>
            <div className="rounded-md border p-3 bg-green-50 border-green-200">
              <p className="text-xs font-semibold text-green-800 mb-1">Réponse attendue</p>
              <p className="whitespace-pre-wrap text-green-900">{q.correctAnswer}</p>
            </div>
          </div>
        )}

        {q.explanation && <p className="text-muted-foreground italic">{q.explanation}</p>}
      </CardContent>
    </Card>
  );
}

/** Copie d'un étudiant corrigée question par question, avec la recommandation IA. */
export default function AttemptReviewPage() {
  const [match, params] = useRoute('/enseignant/copies/:attemptId');
  const attemptId = params?.attemptId || '';

  const { data: review, isLoading } = useGetAttemptReview(attemptId, {
    query: { enabled: !!attemptId, queryKey: getGetAttemptReviewQueryKey(attemptId) },
  });

  if (!match) return null;

  if (isLoading) {
    return (
      <EnseignantLayout>
        <div className="flex items-center justify-center p-12 text-muted-foreground">
          <Loader2 className="animate-spin mr-2" size={18} /> Chargement de la copie…
        </div>
      </EnseignantLayout>
    );
  }

  if (!review) {
    return <EnseignantLayout><div className="text-center p-12 text-muted-foreground">Copie introuvable.</div></EnseignantLayout>;
  }

  const submitted = review.attemptStatus === 'SUBMITTED';
  const percent = review.percent ?? null;

  return (
    <EnseignantLayout>
      <div className="max-w-4xl mx-auto space-y-6">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <Button variant="ghost" size="icon" asChild>
              <Link href={`/enseignant/sessions/${review.sessionId}`}><ArrowLeft size={20} /></Link>
            </Button>
            <div>
              <h1 className="text-2xl font-bold tracking-tight">{review.studentName || review.studentEmail}</h1>
              <p className="text-sm text-muted-foreground">
                {review.quizTitle}
                {review.completedAt && ` · rendu le ${format(new Date(review.completedAt), "d MMM yyyy 'à' HH:mm", { locale: fr })}`}
              </p>
            </div>
          </div>
          {percent != null && (
            <div className="w-48 space-y-1">
              <div className="flex justify-between text-sm">
                <span className={`text-xl font-bold ${percent >= PASSING_PERCENT ? 'text-green-700' : 'text-red-700'}`}>
                  {Math.round(percent)}%
                </span>
                <span className="text-muted-foreground self-end">{review.score} / {review.maxScore}</span>
              </div>
              <Progress value={percent} className="h-2" />
            </div>
          )}
        </div>

        <RecommendationPanel
          attemptId={review.attemptId}
          sessionId={review.sessionId}
          submitted={submitted}
          recommendation={review.recommendation}
        />

        <div className="space-y-4">
          {review.questions.map((q, i) => <QuestionCard key={q.questionId} q={q} index={i} />)}
        </div>
      </div>
    </EnseignantLayout>
  );
}
