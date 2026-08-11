import { useRoute, useLocation } from 'wouter';
import { useState } from 'react';
import {
  useGetQuiz,
  useUpdateQuizQuestions,
  getGetQuizQueryKey,
  QuestionUpdateItemType,
  QuestionUpdateItemDifficulty,
} from '@workspace/api-client-react';
import { useQueryClient } from '@tanstack/react-query';
import { EnseignantLayout } from '@/components/layout/enseignant-layout';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Separator } from '@/components/ui/separator';
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from '@/components/ui/alert-dialog';
import { toast } from 'sonner';
import {
  ArrowLeft,
  Check,
  Download,
  FileCode2,
  Loader2,
  Package,
  Pencil,
  Send,
} from 'lucide-react';
import { useScormExport, useMoodleExport } from '@/hooks/use-quiz-export';

type Question = {
  id: string;
  type: string;
  content: string;
  options: string[] | null;
  correctAnswer: string;
  explanation: string | null;
  difficulty: string;
  position: number;
};

function questionTypeBadgeClass(type: string) {
  return type === 'QCM'
    ? 'bg-blue-100 text-blue-700 border-blue-200'
    : type === 'OUVERTE'
    ? 'bg-violet-100 text-violet-700 border-violet-200'
    : 'bg-amber-100 text-amber-700 border-amber-200';
}

function difficultyBadgeClass(diff: string) {
  return diff === 'FACILE'
    ? 'bg-green-100 text-green-700 border-green-200'
    : diff === 'DIFFICILE'
    ? 'bg-red-100 text-red-700 border-red-200'
    : 'bg-amber-100 text-amber-700 border-amber-200';
}

export default function QuizEditor() {
  const [match, params] = useRoute('/enseignant/quizzes/:id');
  const [, navigate]    = useLocation();
  const quizId          = params?.id || '';
  const queryClient     = useQueryClient();

  const [editingId, setEditingId] = useState<string | null>(null);
  const [editDraft, setEditDraft] = useState<Partial<Question>>({});

  const { data: quiz, isLoading } = useGetQuiz(quizId, {
    query: { enabled: !!quizId, queryKey: getGetQuizQueryKey(quizId) }
  });

  const updateMutation = useUpdateQuizQuestions({
    mutation: {
      onSuccess: () => {
        queryClient.invalidateQueries({ queryKey: getGetQuizQueryKey(quizId) });
        toast.success('Questions enregistrées avec succès');
      },
      onError: () => toast.error('Erreur lors de l\'enregistrement'),
    }
  });

  const publishMutation = useUpdateQuizQuestions({
    mutation: {
      onSuccess: () => {
        queryClient.invalidateQueries({ queryKey: getGetQuizQueryKey(quizId) });
        toast.success('Quiz publié — les étudiants peuvent maintenant le passer');
      },
      onError: () => toast.error('Erreur lors de la publication'),
    }
  });

  const { exportScorm, isPending: scormPending, error: scormError } = useScormExport();
  const { exportMoodle, isPending: moodlePending, error: moodleError } = useMoodleExport();

  if (!match) return null;

  if (isLoading) {
    return (
      <EnseignantLayout>
        <div className="flex items-center justify-center p-12 text-muted-foreground">
          <Loader2 className="animate-spin mr-2" size={18} />
          Chargement du quiz…
        </div>
      </EnseignantLayout>
    );
  }

  if (!quiz) {
    return (
      <EnseignantLayout>
        <div className="text-center p-12 text-muted-foreground">Quiz introuvable.</div>
      </EnseignantLayout>
    );
  }

  const questions: Question[] = (quiz as any).questions ?? [];
  const isPublished = (quiz as any).quizStatus === 'PUBLISHED';

  function startEdit(q: Question) {
    setEditingId(q.id);
    setEditDraft({
      content:       q.content,
      correctAnswer: q.correctAnswer,
      explanation:   q.explanation ?? '',
      options:       q.options ?? [],
      difficulty:    q.difficulty,
    });
  }

  function saveEdit(q: Question) {
    const updated: Question[] = questions.map(qq =>
      qq.id === q.id ? { ...qq, ...editDraft } : qq
    );

    const questionsPayload = updated.map(qu => ({
      id:            qu.id,
      type:          qu.type as QuestionUpdateItemType,
      content:       qu.content,
      options:       qu.options,
      correctAnswer: qu.correctAnswer,
      explanation:   qu.explanation,
      difficulty:    qu.difficulty as QuestionUpdateItemDifficulty,
      position:      qu.position,
    }));

    updateMutation.mutate({
      id: quizId,
      data: { questions: questionsPayload, publish: false }
    });
    setEditingId(null);
  }

  function handlePublish() {
    const questionsPayload = questions.map(q => ({
      id:            q.id,
      type:          q.type as QuestionUpdateItemType,
      content:       q.content,
      options:       q.options,
      correctAnswer: q.correctAnswer,
      explanation:   q.explanation,
      difficulty:    q.difficulty as QuestionUpdateItemDifficulty,
      position:      q.position,
    }));
    publishMutation.mutate({
      id: quizId,
      data: { questions: questionsPayload, publish: true }
    });
  }

  const quizTitle = quiz?.title ?? 'quiz';

  function handleScormExport() {
    exportScorm(quizId, quizTitle);
    if (scormError) toast.error(scormError);
  }

  function handleMoodleExport() {
    exportMoodle(quizId, quizTitle);
    if (moodleError) toast.error(moodleError);
  }

  return (
    <EnseignantLayout>
      <div className="max-w-4xl mx-auto space-y-6">

        {/* Header */}
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
          <div className="flex items-center gap-3">
            <Button variant="ghost" size="icon" onClick={() => navigate('/enseignant')}>
              <ArrowLeft size={20} />
            </Button>
            <div>
              <h1 className="text-2xl font-bold tracking-tight">{quiz.title}</h1>
              <p className="text-sm text-muted-foreground mt-0.5">
                {questions.length} question{questions.length !== 1 ? 's' : ''} &nbsp;·&nbsp;
                <span className={`font-medium ${isPublished ? 'text-green-600' : 'text-amber-600'}`}>
                  {isPublished ? 'Publié' : 'Brouillon'}
                </span>
              </p>
            </div>
          </div>

          {/* Actions */}
          <div className="flex flex-wrap items-center gap-2">
            {/* Export SCORM */}
            <Button
              variant="outline"
              size="sm"
              onClick={handleScormExport}
              disabled={scormPending || !isPublished}
              title={!isPublished ? 'Publiez le quiz avant d\'exporter' : 'Télécharger l\'archive SCORM 2004'}
            >
              {scormPending ? <Loader2 size={15} className="animate-spin mr-1.5" /> : <Package size={15} className="mr-1.5" />}
              SCORM 2004
            </Button>

            {/* Export Moodle */}
            <Button
              variant="outline"
              size="sm"
              onClick={handleMoodleExport}
              disabled={moodlePending || !isPublished}
              title={!isPublished ? 'Publiez le quiz avant d\'exporter' : 'Télécharger le fichier Moodle XML'}
            >
              {moodlePending ? <Loader2 size={15} className="animate-spin mr-1.5" /> : <FileCode2 size={15} className="mr-1.5" />}
              Moodle XML
            </Button>

            {/* Publier */}
            {!isPublished && (
              <AlertDialog>
                <AlertDialogTrigger asChild>
                  <Button size="sm" disabled={publishMutation.isPending}>
                    {publishMutation.isPending
                      ? <Loader2 size={15} className="animate-spin mr-1.5" />
                      : <Send size={15} className="mr-1.5" />}
                    Publier le quiz
                  </Button>
                </AlertDialogTrigger>
                <AlertDialogContent>
                  <AlertDialogHeader>
                    <AlertDialogTitle>Publier ce quiz ?</AlertDialogTitle>
                    <AlertDialogDescription>
                      Une fois publié, le quiz sera visible par les étudiants lors des sessions.
                      Vous pourrez toujours modifier les questions après publication.
                    </AlertDialogDescription>
                  </AlertDialogHeader>
                  <AlertDialogFooter>
                    <AlertDialogCancel>Annuler</AlertDialogCancel>
                    <AlertDialogAction onClick={handlePublish}>Publier</AlertDialogAction>
                  </AlertDialogFooter>
                </AlertDialogContent>
              </AlertDialog>
            )}
          </div>
        </div>

        {/* Export hint — si non publié */}
        {!isPublished && (
          <div className="flex items-start gap-2 p-3 bg-amber-50 border border-amber-100 rounded-lg text-sm text-amber-800">
            <Download size={15} className="mt-0.5 shrink-0" />
            <span>Publiez ce quiz pour activer les exports SCORM et Moodle XML.</span>
          </div>
        )}

        {/* Liste des questions */}
        <div className="space-y-4">
          {questions.map((q, i) => {
            const isEditing = editingId === q.id;

            return (
              <Card key={q.id} className={isEditing ? 'ring-2 ring-primary/30' : ''}>
                <CardHeader className="pb-2">
                  <div className="flex items-start justify-between gap-3">
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className="text-sm font-semibold text-muted-foreground">Q{i + 1}</span>
                      <Badge variant="outline" className={`text-xs ${questionTypeBadgeClass(q.type)}`}>
                        {q.type}
                      </Badge>
                      <Badge variant="outline" className={`text-xs ${difficultyBadgeClass(q.difficulty)}`}>
                        {q.difficulty}
                      </Badge>
                    </div>
                    {!isEditing && (
                      <Button variant="ghost" size="icon" className="h-7 w-7" onClick={() => startEdit(q)}>
                        <Pencil size={14} />
                      </Button>
                    )}
                  </div>
                </CardHeader>

                <CardContent className="space-y-4">
                  {isEditing ? (
                    /* ── Mode édition ── */
                    <div className="space-y-4">
                      <div className="space-y-1.5">
                        <Label>Énoncé</Label>
                        <Textarea
                          value={editDraft.content ?? ''}
                          onChange={e => setEditDraft(d => ({ ...d, content: e.target.value }))}
                          rows={3}
                        />
                      </div>

                      {q.type === 'QCM' && (
                        <div className="space-y-1.5">
                          <Label>Options (une par ligne)</Label>
                          <Textarea
                            value={(editDraft.options ?? []).join('\n')}
                            onChange={e =>
                              setEditDraft(d => ({
                                ...d,
                                options: e.target.value.split('\n').filter(Boolean),
                              }))
                            }
                            rows={4}
                            placeholder="Option 1&#10;Option 2&#10;Option 3&#10;Option 4"
                          />
                        </div>
                      )}

                      <div className="space-y-1.5">
                        <Label>Réponse correcte</Label>
                        <Input
                          value={editDraft.correctAnswer ?? ''}
                          onChange={e => setEditDraft(d => ({ ...d, correctAnswer: e.target.value }))}
                        />
                      </div>

                      <div className="space-y-1.5">
                        <Label>Explication (optionnel)</Label>
                        <Textarea
                          value={editDraft.explanation ?? ''}
                          onChange={e => setEditDraft(d => ({ ...d, explanation: e.target.value }))}
                          rows={2}
                        />
                      </div>

                      <div className="flex gap-2 pt-2">
                        <Button size="sm" onClick={() => saveEdit(q)} disabled={updateMutation.isPending}>
                          {updateMutation.isPending
                            ? <Loader2 size={14} className="animate-spin mr-1.5" />
                            : <Check size={14} className="mr-1.5" />}
                          Enregistrer
                        </Button>
                        <Button size="sm" variant="ghost" onClick={() => setEditingId(null)}>
                          Annuler
                        </Button>
                      </div>
                    </div>
                  ) : (
                    /* ── Mode lecture ── */
                    <div className="space-y-3">
                      <p className="text-base font-medium leading-relaxed">{q.content}</p>

                      {q.type === 'QCM' && q.options && (
                        <div className="grid gap-1.5">
                          {q.options.map((opt, oi) => {
                            const isCorrect = opt.trim().toLowerCase() === q.correctAnswer.trim().toLowerCase();
                            return (
                              <div
                                key={oi}
                                className={`px-3 py-2 rounded-md border text-sm flex items-center gap-2 ${
                                  isCorrect
                                    ? 'bg-green-50 border-green-200 text-green-900 font-medium'
                                    : 'bg-slate-50 border-slate-200 text-slate-700'
                                }`}
                              >
                                {isCorrect && <Check size={13} className="text-green-600 shrink-0" />}
                                {opt}
                              </div>
                            );
                          })}
                        </div>
                      )}

                      {q.type !== 'QCM' && (
                        <div className="p-3 bg-green-50 border border-green-200 rounded-md text-sm">
                          <span className="font-semibold text-green-800">Réponse attendue : </span>
                          <span className="text-green-900">{q.correctAnswer}</span>
                          {(q.type === 'OUVERTE') && (
                            <span className="ml-2 text-xs text-violet-600 font-medium">(BERTScore)</span>
                          )}
                        </div>
                      )}

                      {q.explanation && (
                        <>
                          <Separator />
                          <p className="text-sm text-muted-foreground italic">{q.explanation}</p>
                        </>
                      )}
                    </div>
                  )}
                </CardContent>
              </Card>
            );
          })}
        </div>
      </div>
    </EnseignantLayout>
  );
}
