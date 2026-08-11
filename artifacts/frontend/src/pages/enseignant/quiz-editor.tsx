import { useState } from 'react';
import { useRoute, useLocation } from 'wouter';
import { 
  useGetQuiz, 
  useUpdateQuizQuestions, 
  getGetQuizQueryKey,
  Question,
  QuestionUpdateItem
} from '@workspace/api-client-react';
import { useQueryClient } from '@tanstack/react-query';
import { EnseignantLayout } from '@/components/layout/enseignant-layout';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { Badge } from '@/components/ui/badge';
import { ArrowLeft, Save, Send, CheckCircle2, ChevronRight, ChevronDown } from 'lucide-react';
import { toast } from 'sonner';

export default function QuizEditor() {
  const [match, params] = useRoute('/enseignant/quizzes/:id');
  const quizId = params?.id || '';
  const [, setLocation] = useLocation();
  const queryClient = useQueryClient();

  const { data: quiz, isLoading } = useGetQuiz(quizId, {
    query: {
      enabled: !!quizId,
      queryKey: getGetQuizQueryKey(quizId)
    }
  });

  const updateQuiz = useUpdateQuizQuestions();
  
  // Local state for editing questions
  const [editedQuestions, setEditedQuestions] = useState<Record<string, QuestionUpdateItem>>({});
  const [expandedQs, setExpandedQs] = useState<Record<string, boolean>>({});

  const toggleQ = (qId: string) => {
    setExpandedQs(prev => ({ ...prev, [qId]: !prev[qId] }));
  };

  const initEdit = (q: Question) => {
    if (!editedQuestions[q.id]) {
      setEditedQuestions(prev => ({
        ...prev,
        [q.id]: {
          id: q.id,
          type: q.type,
          content: q.content,
          options: q.options || [],
          correctAnswer: q.correctAnswer,
          explanation: q.explanation || '',
          difficulty: q.difficulty,
          position: q.position,
          keywords: q.keywords || []
        }
      }));
    }
  };

  const getQ = (q: Question) => editedQuestions[q.id] || q;

  const handleUpdateContent = (qId: string, content: string) => {
    setEditedQuestions(prev => ({
      ...prev,
      [qId]: { ...prev[qId], content }
    }));
  };

  const handleUpdateOption = (qId: string, idx: number, value: string) => {
    setEditedQuestions(prev => {
      const q = prev[qId];
      if (!q || !q.options) return prev;
      const newOptions = [...q.options];
      newOptions[idx] = value;
      return { ...prev, [qId]: { ...q, options: newOptions } };
    });
  };

  const handleSetCorrect = (qId: string, value: string) => {
    setEditedQuestions(prev => ({
      ...prev,
      [qId]: { ...prev[qId], correctAnswer: value }
    }));
  };

  const handleSaveAll = async (publish = false) => {
    if (!quiz || !quiz.questions) return;
    
    const finalQuestions: QuestionUpdateItem[] = quiz.questions.map(q => {
      const edited = editedQuestions[q.id];
      if (edited) return edited;
      return {
        id: q.id,
        type: q.type,
        content: q.content,
        options: q.options,
        correctAnswer: q.correctAnswer,
        explanation: q.explanation,
        difficulty: q.difficulty,
        position: q.position,
        keywords: q.keywords
      };
    });

    try {
      await updateQuiz.mutateAsync({
        id: quizId,
        data: {
          questions: finalQuestions,
          publish
        }
      });
      
      queryClient.invalidateQueries({ queryKey: getGetQuizQueryKey(quizId) });
      toast.success(publish ? 'Quiz publié avec succès !' : 'Modifications sauvegardées.');
      
      if (publish) {
        setLocation('/enseignant');
      }
    } catch (e) {
      toast.error('Erreur lors de la sauvegarde');
    }
  };

  if (!match) return null;

  if (isLoading) {
    return (
      <EnseignantLayout>
        <div className="flex items-center justify-center h-64">Chargement du quiz...</div>
      </EnseignantLayout>
    );
  }

  if (!quiz) {
    return (
      <EnseignantLayout>
        <div className="text-center py-12">Quiz introuvable.</div>
      </EnseignantLayout>
    );
  }

  const isPublished = quiz.quizStatus === 'PUBLISHED';

  return (
    <EnseignantLayout>
      <div className="max-w-4xl mx-auto space-y-6">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <Button variant="ghost" size="icon" onClick={() => window.history.back()}>
              <ArrowLeft size={20} />
            </Button>
            <div>
              <div className="flex items-center gap-2">
                <h1 className="text-2xl font-bold tracking-tight">{quiz.title}</h1>
                <Badge variant={isPublished ? 'default' : 'secondary'} className={isPublished ? 'bg-green-600' : ''}>
                  {isPublished ? 'Publié' : 'Brouillon'}
                </Badge>
              </div>
              <p className="text-sm text-muted-foreground mt-1">Généré à partir de: {quiz.documentName}</p>
            </div>
          </div>
          
          <div className="flex gap-2">
            {!isPublished && (
              <>
                <Button variant="outline" onClick={() => handleSaveAll(false)} disabled={updateQuiz.isPending}>
                  <Save size={16} className="mr-2" />
                  Sauvegarder
                </Button>
                <Button onClick={() => handleSaveAll(true)} disabled={updateQuiz.isPending}>
                  <Send size={16} className="mr-2" />
                  Publier
                </Button>
              </>
            )}
          </div>
        </div>

        <div className="space-y-4">
          {(quiz.questions || []).map((q, i) => {
            const isExpanded = expandedQs[q.id];
            const currentQ = getQ(q) as any;
            
            return (
              <Card key={q.id} className={editedQuestions[q.id] ? 'border-primary/50' : ''}>
                <div 
                  className="flex items-center justify-between p-4 cursor-pointer hover:bg-slate-50 transition-colors"
                  onClick={() => {
                    toggleQ(q.id);
                    if (!isExpanded && !isPublished) initEdit(q);
                  }}
                >
                  <div className="flex items-center gap-3 font-medium">
                    <span className="w-6 text-muted-foreground">{i + 1}.</span>
                    <Badge variant="outline" className="text-xs bg-card">
                      {currentQ.type}
                    </Badge>
                    <span className="truncate max-w-md md:max-w-xl">{currentQ.content}</span>
                  </div>
                  {isExpanded ? <ChevronDown size={20} className="text-muted-foreground" /> : <ChevronRight size={20} className="text-muted-foreground" />}
                </div>

                {isExpanded && (
                  <CardContent className="pt-0 pb-6 px-4 sm:px-12 border-t mt-2">
                    <div className="mt-4 space-y-4">
                      {isPublished ? (
                        <>
                          <div className="p-3 bg-slate-50 rounded-md border text-sm">{currentQ.content}</div>
                          {currentQ.type === 'QCM' && currentQ.options && (
                            <div className="space-y-2 mt-4">
                              <p className="text-xs font-semibold text-muted-foreground uppercase">Options</p>
                              {currentQ.options.map((opt: string, idx: number) => (
                                <div key={idx} className={`p-2 border rounded-md text-sm flex justify-between ${opt === currentQ.correctAnswer ? 'bg-green-50 border-green-200 text-green-800 font-medium' : 'bg-card'}`}>
                                  <span>{opt}</span>
                                  {opt === currentQ.correctAnswer && <CheckCircle2 size={16} className="text-green-600" />}
                                </div>
                              ))}
                            </div>
                          )}
                          {currentQ.type !== 'QCM' && (
                            <div className="space-y-2 mt-4">
                              <p className="text-xs font-semibold text-muted-foreground uppercase">Réponse attendue</p>
                              <div className="p-3 bg-green-50 border border-green-200 rounded-md text-sm text-green-800">
                                {currentQ.correctAnswer}
                              </div>
                            </div>
                          )}
                        </>
                      ) : (
                        <>
                          <div className="space-y-1.5">
                            <label className="text-xs font-medium text-foreground">Énoncé de la question</label>
                            <Textarea 
                              value={currentQ.content} 
                              onChange={(e) => handleUpdateContent(q.id, e.target.value)}
                              className="min-h-[80px]"
                            />
                          </div>

                          {currentQ.type === 'QCM' && currentQ.options && (
                            <div className="space-y-3 pt-2">
                              <label className="text-xs font-medium text-foreground">Options (cochez la bonne réponse)</label>
                              <div className="space-y-2">
                                {currentQ.options.map((opt: string, idx: number) => (
                                  <div key={idx} className="flex items-center gap-3">
                                    <div 
                                      className="flex-shrink-0 cursor-pointer"
                                      onClick={() => handleSetCorrect(q.id, opt)}
                                    >
                                      <div className={`w-5 h-5 rounded-full border flex items-center justify-center ${opt === currentQ.correctAnswer ? 'bg-green-500 border-green-500' : 'border-slate-300'}`}>
                                        {opt === currentQ.correctAnswer && <div className="w-2 h-2 bg-white rounded-full" />}
                                      </div>
                                    </div>
                                    <Input 
                                      value={opt} 
                                      onChange={(e) => handleUpdateOption(q.id, idx, e.target.value)} 
                                      className={opt === currentQ.correctAnswer ? 'border-green-200 bg-green-50/50' : ''}
                                    />
                                  </div>
                                ))}
                              </div>
                            </div>
                          )}

                          {currentQ.type !== 'QCM' && (
                            <div className="space-y-1.5 pt-2">
                              <label className="text-xs font-medium text-foreground">Réponse modèle / attendue</label>
                              <Textarea 
                                value={currentQ.correctAnswer} 
                                onChange={(e) => handleSetCorrect(q.id, e.target.value)}
                                className="min-h-[80px]"
                              />
                            </div>
                          )}
                        </>
                      )}
                    </div>
                  </CardContent>
                )}
              </Card>
            );
          })}
        </div>
      </div>
    </EnseignantLayout>
  );
}
