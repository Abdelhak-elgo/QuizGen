import { useState, useEffect, useRef } from 'react';
import { useRoute, useLocation } from 'wouter';
import { 
  useGetAttempt,
  useGetSession,
  useGetQuiz,
  useSubmitAttempt,
  getGetAttemptQueryKey,
  getGetSessionQueryKey,
  getGetQuizQueryKey
} from '@workspace/api-client-react';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { AlertTriangle, CheckCircle2, ChevronLeft, ChevronRight, Clock, Send } from 'lucide-react';
import { toast } from 'sonner';

export default function ExamMode() {
  const [match, params] = useRoute('/etudiant/examens/:attemptId');
  const attemptId = params?.attemptId || '';
  const [, setLocation] = useLocation();

  const { data: attempt, isLoading: isLoadingAttempt } = useGetAttempt(attemptId, {
    query: { enabled: !!attemptId, queryKey: getGetAttemptQueryKey(attemptId) }
  });

  const sessionId = attempt?.sessionId || '';
  const { data: session } = useGetSession(sessionId, {
    query: { enabled: !!sessionId, queryKey: getGetSessionQueryKey(sessionId) }
  });

  const quizId = session?.quizId || '';
  const { data: quiz } = useGetQuiz(quizId, {
    query: { enabled: !!quizId, queryKey: getGetQuizQueryKey(quizId) }
  });

  const submitAttempt = useSubmitAttempt();

  const [answers, setAnswers] = useState<Record<string, string>>({});
  const [currentIdx, setCurrentIdx] = useState(0);
  const [timeLeft, setTimeLeft] = useState<number | null>(null);
  
  // Initialize answers from attempt.answers if any
  useEffect(() => {
    if (attempt?.answers && Object.keys(answers).length === 0) {
      setAnswers(attempt.answers as Record<string, string>);
    }
  }, [attempt, answers]);

  // Timer logic
  useEffect(() => {
    if (session?.endTime) {
      const end = new Date(session.endTime).getTime();
      const interval = setInterval(() => {
        const now = new Date().getTime();
        const diff = Math.max(0, Math.floor((end - now) / 1000));
        setTimeLeft(diff);
        
        if (diff === 0) {
          clearInterval(interval);
          handleAutoSubmit();
        }
      }, 1000);
      return () => clearInterval(interval);
    }
    return undefined;
  }, [session?.endTime]);

  const mutateFnRef = useRef(submitAttempt.mutateAsync);
  mutateFnRef.current = submitAttempt.mutateAsync;
  
  const currentAnswersRef = useRef(answers);
  currentAnswersRef.current = answers;

  const handleAutoSubmit = async () => {
    toast.error("Le temps imparti est écoulé. Soumission automatique.");
    try {
      await mutateFnRef.current({
        id: attemptId,
        data: { answers: currentAnswersRef.current }
      });
      setLocation(`/etudiant/resultats/${attemptId}`);
    } catch (e) {
      console.error(e);
    }
  };

  const handleSubmit = async () => {
    if (!confirm("Êtes-vous sûr de vouloir soumettre vos réponses ? Cette action est irréversible.")) return;
    
    try {
      await submitAttempt.mutateAsync({
        id: attemptId,
        data: { answers }
      });
      toast.success("Quiz soumis avec succès !");
      setLocation(`/etudiant/resultats/${attemptId}`);
    } catch (e) {
      toast.error("Erreur lors de la soumission");
    }
  };

  const handleAnswer = (qId: string, val: string) => {
    setAnswers(prev => ({ ...prev, [qId]: val }));
  };

  if (!match) return null;

  if (isLoadingAttempt || !quiz || !session) {
    return <div className="flex h-screen items-center justify-center bg-slate-50">Chargement de l'examen...</div>;
  }

  if (attempt?.attemptStatus === 'SUBMITTED') {
    setLocation(`/etudiant/resultats/${attemptId}`);
    return null;
  }

  const questions = quiz.questions || [];
  const currentQ = questions[currentIdx];
  const isLast = currentIdx === questions.length - 1;
  const isFirst = currentIdx === 0;

  const formatTime = (seconds: number) => {
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    const s = seconds % 60;
    if (h > 0) return `${h}:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
    return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
  };

  const isUrgent = timeLeft !== null && timeLeft < 300; // Less than 5 mins

  return (
    <div className="min-h-[100dvh] bg-slate-50 flex flex-col">
      {/* Top Header */}
      <header className="bg-card border-b h-16 flex items-center justify-between px-4 sm:px-6 sticky top-0 z-20 shrink-0">
        <div className="font-semibold text-lg hidden sm:block truncate pr-4">
          {quiz.title}
        </div>
        
        {timeLeft !== null && (
          <div className={`flex items-center gap-2 px-4 py-1.5 rounded-full border font-mono text-lg font-bold tracking-wider ${isUrgent ? 'bg-red-50 text-red-600 border-red-200 animate-pulse' : 'bg-primary/10 text-primary border-primary/20'}`}>
            <Clock size={20} className={isUrgent ? 'text-red-500' : 'text-primary'} />
            {formatTime(timeLeft)}
          </div>
        )}

        <Button 
          onClick={handleSubmit} 
          disabled={submitAttempt.isPending}
          variant="default"
          className={submitAttempt.isPending ? 'opacity-70' : ''}
        >
          {submitAttempt.isPending ? 'Envoi...' : 'Soumettre'}
          {!submitAttempt.isPending && <Send size={16} className="ml-2" />}
        </Button>
      </header>

      <main className="flex-1 flex flex-col md:flex-row max-w-7xl mx-auto w-full p-4 gap-6">
        
        {/* Navigation Sidebar */}
        <div className="w-full md:w-64 shrink-0 space-y-4 order-2 md:order-1">
          <Card>
            <CardContent className="p-4">
              <h3 className="font-medium text-sm mb-4 text-muted-foreground uppercase tracking-wider">Navigation</h3>
              <div className="grid grid-cols-5 md:grid-cols-4 gap-2">
                {questions.map((q, i) => {
                  const isAnswered = !!answers[q.id];
                  const isActive = currentIdx === i;
                  return (
                    <button
                      key={q.id}
                      onClick={() => setCurrentIdx(i)}
                      className={`
                        h-10 w-full rounded-md font-medium text-sm flex items-center justify-center transition-all
                        ${isActive ? 'ring-2 ring-primary ring-offset-1 bg-primary text-primary-foreground' : ''}
                        ${!isActive && isAnswered ? 'bg-primary/10 text-primary border border-primary/20' : ''}
                        ${!isActive && !isAnswered ? 'bg-card border text-muted-foreground hover:bg-slate-100' : ''}
                      `}
                    >
                      {i + 1}
                    </button>
                  );
                })}
              </div>
              <div className="mt-6 space-y-2 text-xs text-muted-foreground">
                <div className="flex items-center gap-2">
                  <div className="w-3 h-3 rounded bg-primary"></div>
                  <span>Question actuelle</span>
                </div>
                <div className="flex items-center gap-2">
                  <div className="w-3 h-3 rounded bg-primary/20 border border-primary/30"></div>
                  <span>Répondu</span>
                </div>
                <div className="flex items-center gap-2">
                  <div className="w-3 h-3 rounded bg-card border"></div>
                  <span>Non répondu</span>
                </div>
              </div>
            </CardContent>
          </Card>
        </div>

        {/* Question Area */}
        <div className="flex-1 order-1 md:order-2 flex flex-col min-h-0">
          <Card className="flex-1 border-primary/10 shadow-md flex flex-col">
            <CardContent className="p-6 md:p-8 flex-1 overflow-y-auto">
              {currentQ && (
                <div className="space-y-8 animate-in fade-in slide-in-from-bottom-2 duration-300">
                  <div className="flex items-center justify-between">
                    <Badge variant="outline" className="bg-primary/5 text-primary text-sm px-3 py-1">
                      Question {currentIdx + 1} sur {questions.length}
                    </Badge>
                    <Badge variant="secondary" className="uppercase text-xs">{currentQ.type}</Badge>
                  </div>
                  
                  <div className="text-xl md:text-2xl font-medium leading-relaxed text-foreground">
                    {currentQ.content}
                  </div>

                  <div className="pt-6">
                    {currentQ.type === 'QCM' && currentQ.options ? (
                      <RadioGroup 
                        value={answers[currentQ.id] || ''} 
                        onValueChange={(val) => handleAnswer(currentQ.id, val)}
                        className="space-y-3"
                      >
                        {currentQ.options.map((opt, idx) => (
                          <div 
                            key={idx} 
                            className={`flex items-start space-x-3 space-y-0 p-4 rounded-lg border-2 transition-all cursor-pointer ${
                              answers[currentQ.id] === opt 
                                ? 'border-primary bg-primary/5' 
                                : 'border-border bg-card hover:border-primary/30 hover:bg-slate-50'
                            }`}
                            onClick={() => handleAnswer(currentQ.id, opt)}
                          >
                            <RadioGroupItem value={opt} id={`q-${currentQ.id}-opt-${idx}`} className="mt-0.5" />
                            <Label htmlFor={`q-${currentQ.id}-opt-${idx}`} className="font-normal text-base leading-snug cursor-pointer flex-1">
                              {opt}
                            </Label>
                          </div>
                        ))}
                      </RadioGroup>
                    ) : (
                      <div className="space-y-3">
                        <Label className="text-muted-foreground">Votre réponse :</Label>
                        <Textarea 
                          placeholder="Saisissez votre réponse ici..."
                          className="min-h-[200px] text-base p-4 resize-none leading-relaxed"
                          value={answers[currentQ.id] || ''}
                          onChange={(e) => handleAnswer(currentQ.id, e.target.value)}
                        />
                      </div>
                    )}
                  </div>
                </div>
              )}
            </CardContent>
            
            {/* Bottom Actions */}
            <div className="p-4 md:p-6 bg-slate-50/50 border-t flex items-center justify-between mt-auto">
              <Button 
                variant="outline" 
                onClick={() => setCurrentIdx(prev => prev - 1)}
                disabled={isFirst}
                className="w-28"
              >
                <ChevronLeft size={16} className="mr-2" />
                Précédent
              </Button>
              
              {!isLast ? (
                <Button 
                  onClick={() => setCurrentIdx(prev => prev + 1)}
                  className="w-28"
                >
                  Suivant
                  <ChevronRight size={16} className="ml-2" />
                </Button>
              ) : (
                <Button 
                  onClick={handleSubmit}
                  variant="default"
                  className="bg-green-600 hover:bg-green-700 w-28"
                  disabled={submitAttempt.isPending}
                >
                  {submitAttempt.isPending ? '...' : 'Terminer'}
                  {!submitAttempt.isPending && <CheckCircle2 size={16} className="ml-2" />}
                </Button>
              )}
            </div>
          </Card>
        </div>
      </main>
    </div>
  );
}
