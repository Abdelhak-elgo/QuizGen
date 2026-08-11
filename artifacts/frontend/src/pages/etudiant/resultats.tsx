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
import { ArrowLeft, CheckCircle2, ChevronRight, LayoutDashboard, Target, Trophy, XCircle } from 'lucide-react';
import { format } from 'date-fns';
import { fr } from 'date-fns/locale';

export default function Resultats() {
  const [match, params] = useRoute('/etudiant/resultats/:attemptId');
  const attemptId = params?.attemptId || '';

  const { data: attempt, isLoading: isLoadingAttempt } = useGetAttempt(attemptId, {
    query: { enabled: !!attemptId, queryKey: getGetAttemptQueryKey(attemptId) }
  });

  const sessionId = attempt?.sessionId || '';
  const { data: session, isLoading: isLoadingSession } = useGetSession(sessionId, {
    query: { enabled: !!sessionId, queryKey: getGetSessionQueryKey(sessionId) }
  });

  const quizId = session?.quizId || '';
  const { data: quiz, isLoading: isLoadingQuiz } = useGetQuiz(quizId, {
    query: { enabled: !!quizId, queryKey: getGetQuizQueryKey(quizId) }
  });

  if (!match) return null;

  if (isLoadingAttempt || isLoadingSession || isLoadingQuiz) {
    return <EtudiantLayout><div className="flex justify-center p-12">Chargement des résultats...</div></EtudiantLayout>;
  }

  if (!attempt || !quiz || !session) {
    return <EtudiantLayout><div className="text-center p-12 text-muted-foreground">Résultats introuvables.</div></EtudiantLayout>;
  }

  const isSuccess = (attempt.scorePercent || 0) >= 0.6;
  const questions = quiz.questions || [];
  const answers = (attempt.answers || {}) as Record<string, string>;

  return (
    <EtudiantLayout>
      <div className="max-w-4xl mx-auto space-y-8">
        <div className="flex items-center gap-3">
          <Button variant="ghost" size="icon" onClick={() => window.history.back()}>
            <ArrowLeft size={20} />
          </Button>
          <div>
            <h1 className="text-2xl font-bold tracking-tight">Résultats : {quiz.title}</h1>
            <p className="text-sm text-muted-foreground mt-1">
              Soumis le {attempt.completedAt ? format(new Date(attempt.completedAt), "d MMMM yyyy 'à' HH:mm", { locale: fr }) : ''}
            </p>
          </div>
        </div>

        <div className="grid sm:grid-cols-3 gap-6">
          <Card className={`sm:col-span-2 border-2 ${isSuccess ? 'border-green-200 bg-green-50/30' : 'border-red-200 bg-red-50/30'}`}>
            <CardContent className="p-8 flex flex-col sm:flex-row items-center justify-between gap-6 text-center sm:text-left">
              <div>
                <Badge variant="outline" className={`mb-3 px-3 py-1 text-sm ${isSuccess ? 'bg-green-100 text-green-800 border-green-200' : 'bg-red-100 text-red-800 border-red-200'}`}>
                  {isSuccess ? 'Module validé' : 'Non validé'}
                </Badge>
                <h2 className="text-4xl font-extrabold tracking-tight mb-2 text-foreground">
                  {Math.round((attempt.scorePercent || 0) * 100)}%
                </h2>
                <p className="text-muted-foreground font-medium flex items-center justify-center sm:justify-start gap-1.5">
                  <Target size={16} /> Score final : {attempt.score} / {attempt.maxScore} points
                </p>
              </div>
              <div className={`p-6 rounded-full ${isSuccess ? 'bg-green-100 text-green-600' : 'bg-red-100 text-red-600'}`}>
                {isSuccess ? <Trophy size={48} /> : <XCircle size={48} />}
              </div>
            </CardContent>
          </Card>
          
          <Card className="flex flex-col justify-center">
            <CardContent className="p-6 space-y-4">
              <Button asChild className="w-full" variant="outline">
                <Link href="/etudiant/historique">
                  Voir l'historique
                </Link>
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

        <div className="space-y-6">
          <h3 className="text-xl font-bold border-b pb-2">Correction détaillée</h3>
          
          <div className="space-y-6">
            {questions.map((q, i) => {
              const studentAnswer = answers[q.id];
              const isQcm = q.type === 'QCM';
              // For QCM, we can do exact match. For Open questions, the backend scored it via NLP.
              // Here we just display the student answer and the model answer.
              const isCorrectMatch = isQcm && studentAnswer === q.correctAnswer;
              
              return (
                <Card key={q.id} className="overflow-hidden">
                  <div className="bg-slate-50 border-b px-4 py-3 flex items-center justify-between">
                    <span className="font-semibold text-sm">Question {i + 1}</span>
                    <Badge variant="secondary" className="text-xs">{q.type}</Badge>
                  </div>
                  <CardContent className="p-4 sm:p-6 space-y-4">
                    <p className="text-lg font-medium text-foreground">{q.content}</p>
                    
                    <div className="grid sm:grid-cols-2 gap-4 pt-2">
                      <div className="space-y-2">
                        <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">Votre réponse</p>
                        <div className={`p-4 rounded-md border ${
                          isQcm 
                            ? (isCorrectMatch ? 'bg-green-50 border-green-200 text-green-900' : 'bg-red-50 border-red-200 text-red-900')
                            : 'bg-slate-50 border-slate-200 text-slate-900'
                        }`}>
                          <div className="flex items-start gap-2">
                            {isQcm && (isCorrectMatch ? <CheckCircle2 size={18} className="text-green-600 mt-0.5 shrink-0" /> : <XCircle size={18} className="text-red-600 mt-0.5 shrink-0" />)}
                            <span className={studentAnswer ? 'font-medium' : 'italic opacity-60'}>
                              {studentAnswer || 'Aucune réponse fournie'}
                            </span>
                          </div>
                        </div>
                      </div>
                      
                      <div className="space-y-2">
                        <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">Réponse attendue</p>
                        <div className="p-4 rounded-md border bg-green-50 border-green-200 text-green-900">
                          <div className="flex items-start gap-2">
                            <CheckCircle2 size={18} className="text-green-600 mt-0.5 shrink-0" />
                            <span className="font-medium">{q.correctAnswer}</span>
                          </div>
                        </div>
                      </div>
                    </div>
                    
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
