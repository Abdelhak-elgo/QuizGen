import { useRoute, useLocation } from 'wouter';
import { useState } from 'react';
import {
  useGetQuiz,
  useUpdateQuizQuestions,
  useDeleteQuiz,
  useRegenerateQuiz,
  getGetQuizQueryKey,
  getListQuizzesQueryKey,
  QuestionUpdateItemType,
  QuestionUpdateItemDifficulty,
} from '@workspace/api-client-react';
import { useQueryClient } from '@tanstack/react-query';
import { EnseignantLayout } from '@/components/layout/enseignant-layout';
import { QuizStatusBanner } from '@/components/quiz/quiz-status-banner';
import { Card, CardContent, CardHeader } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Separator } from '@/components/ui/separator';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
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
  FileCode2,
  Loader2,
  Package,
  Pencil,
  Plus,
  Send,
  Trash2,
} from 'lucide-react';
import { useScormExport, useMoodleExport } from '@/hooks/use-quiz-export';
import { apiErrorMessage } from '@/lib/api-error';

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

// Question ajoutée à la main, pas encore enregistrée (le backend attend id = null)
const NEW_QUESTION_PREFIX = 'new-';
const GENERATION_POLL_MS = 3000;

const STATUS_LABELS: Record<string, { label: string; className: string }> = {
  DRAFT:     { label: 'Brouillon',   className: 'text-amber-600' },
  REVIEWING: { label: 'En révision', className: 'text-amber-600' },
  PUBLISHED: { label: 'Publié',      className: 'text-green-600' },
  FAILED:    { label: 'Échec de la génération', className: 'text-red-600' },
  ARCHIVED:  { label: 'Archivé',     className: 'text-muted-foreground' },
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

function toPayload(questions: Question[]) {
  return questions.map((q, i) => ({
    id:            q.id.startsWith(NEW_QUESTION_PREFIX) ? undefined : q.id,
    type:          q.type as QuestionUpdateItemType,
    content:       q.content,
    // Lignes vides ignorées à l'enregistrement (pas à la saisie, pour pouvoir taper Entrée)
    options:       q.type === 'QCM' ? (q.options ?? []).map(o => o.trim()).filter(Boolean) : null,
    correctAnswer: q.correctAnswer,
    explanation:   q.explanation,
    difficulty:    q.difficulty as QuestionUpdateItemDifficulty,
    position:      i,
  }));
}

export default function QuizEditor() {
  const [match, params] = useRoute('/enseignant/quizzes/:id');
  const [, navigate]    = useLocation();
  const quizId          = params?.id || '';
  const queryClient     = useQueryClient();

  const [editingId, setEditingId] = useState<string | null>(null);
  const [editDraft, setEditDraft] = useState<Partial<Question>>({});
  const [newQuestion, setNewQuestion] = useState<Question | null>(null);

  const { data: quiz, isLoading } = useGetQuiz(quizId, {
    query: {
      enabled: !!quizId,
      queryKey: getGetQuizQueryKey(quizId),
      // Tant que la génération tourne, on suit son avancement ici aussi
      refetchInterval: (query) => {
        const q = query.state.data;
        return q?.quizStatus === 'DRAFT' && q?.nlpTaskId ? GENERATION_POLL_MS : false;
      },
    },
  });

  const refreshQuiz = () => {
    queryClient.invalidateQueries({ queryKey: getGetQuizQueryKey(quizId) });
    queryClient.invalidateQueries({ queryKey: getListQuizzesQueryKey() });
  };

  const updateMutation = useUpdateQuizQuestions({
    mutation: {
      onSuccess: () => { refreshQuiz(); toast.success('Questions enregistrées avec succès'); },
      onError: (e) => toast.error(apiErrorMessage(e, 'Erreur lors de l\'enregistrement')),
    },
  });

  const publishMutation = useUpdateQuizQuestions({
    mutation: {
      onSuccess: () => { refreshQuiz(); toast.success('Quiz publié — les étudiants peuvent maintenant le passer'); },
      onError: (e) => toast.error(apiErrorMessage(e, 'Erreur lors de la publication')),
    },
  });

  const deleteMutation = useDeleteQuiz({
    mutation: {
      onSuccess: () => {
        queryClient.invalidateQueries({ queryKey: getListQuizzesQueryKey() });
        toast.success('Quiz supprimé');
        navigate('/enseignant');
      },
      onError: (e) => toast.error(apiErrorMessage(e, 'Erreur lors de la suppression')),
    },
  });

  const regenerateMutation = useRegenerateQuiz({
    mutation: {
      onSuccess: () => { refreshQuiz(); toast.success('Génération relancée'); },
      onError: (e) => toast.error(apiErrorMessage(e, 'Impossible de relancer la génération')),
    },
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

  const questions: Question[] = (quiz.questions ?? []) as Question[];
  const displayed = newQuestion ? [...questions, newQuestion] : questions;
  const status = quiz.quizStatus;
  const isPublished = status === 'PUBLISHED';
  const isGenerating = status === 'DRAFT' && !!quiz.nlpTaskId;
  const statusLabel = isGenerating
    ? { label: 'Génération en cours', className: 'text-blue-600' }
    : STATUS_LABELS[status] ?? { label: status, className: '' };

  function startEdit(q: Question) {
    setEditingId(q.id);
    setEditDraft({
      type:          q.type,
      content:       q.content,
      correctAnswer: q.correctAnswer,
      explanation:   q.explanation ?? '',
      options:       q.options ?? [],
      difficulty:    q.difficulty,
    });
  }

  function cancelEdit() {
    setEditingId(null);
    setNewQuestion(null);
  }

  function startAddQuestion() {
    const q: Question = {
      id: `${NEW_QUESTION_PREFIX}${Date.now()}`,
      type: 'QCM',
      content: '',
      options: ['', '', '', ''],
      correctAnswer: '',
      explanation: '',
      difficulty: quiz?.difficulty ?? 'MOYEN',
      position: questions.length,
    };
    setNewQuestion(q);
    startEdit(q);
  }

  function saveEdit(q: Question) {
    const updated = displayed.map(qq => (qq.id === q.id ? { ...qq, ...editDraft } as Question : qq));
    updateMutation.mutate(
      { id: quizId, data: { questions: toPayload(updated), publish: false } },
      { onSuccess: () => { setEditingId(null); setNewQuestion(null); } },
    );
  }

  function removeQuestion(q: Question) {
    const remaining = questions.filter(qq => qq.id !== q.id);
    if (remaining.length === 0) {
      toast.error('Un quiz doit garder au moins une question — supprimez plutôt le quiz.');
      return;
    }
    updateMutation.mutate({ id: quizId, data: { questions: toPayload(remaining), publish: false } });
  }

  function handlePublish() {
    publishMutation.mutate({ id: quizId, data: { questions: toPayload(questions), publish: true } });
  }

  const quizTitle = quiz.title ?? 'quiz';

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
                <span className={`font-medium ${statusLabel.className}`}>{statusLabel.label}</span>
              </p>
            </div>
          </div>

          {/* Actions */}
          <div className="flex flex-wrap items-center gap-2">
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

            {!isPublished && (
              <AlertDialog>
                <AlertDialogTrigger asChild>
                  <Button size="sm" disabled={publishMutation.isPending || isGenerating || questions.length === 0}>
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

            <AlertDialog>
              <AlertDialogTrigger asChild>
                <Button size="sm" variant="ghost" className="text-destructive hover:text-destructive hover:bg-destructive/10"
                  disabled={deleteMutation.isPending} title="Supprimer le quiz">
                  {deleteMutation.isPending ? <Loader2 size={15} className="animate-spin" /> : <Trash2 size={15} />}
                </Button>
              </AlertDialogTrigger>
              <AlertDialogContent>
                <AlertDialogHeader>
                  <AlertDialogTitle>Supprimer ce quiz ?</AlertDialogTitle>
                  <AlertDialogDescription>
                    {isGenerating
                      ? 'La génération en cours sera abandonnée. '
                      : ''}
                    Le quiz et ses questions seront définitivement supprimés. Un quiz déjà utilisé
                    par une session ne peut pas être supprimé.
                  </AlertDialogDescription>
                </AlertDialogHeader>
                <AlertDialogFooter>
                  <AlertDialogCancel>Annuler</AlertDialogCancel>
                  <AlertDialogAction
                    className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
                    onClick={() => deleteMutation.mutate({ id: quizId })}
                  >
                    Supprimer
                  </AlertDialogAction>
                </AlertDialogFooter>
              </AlertDialogContent>
            </AlertDialog>
          </div>
        </div>

        <QuizStatusBanner
          status={status}
          isGenerating={isGenerating}
          questionCount={questions.length}
          isRegenerating={regenerateMutation.isPending}
          onRegenerate={() => regenerateMutation.mutate({ id: quizId })}
        />

        {/* Liste des questions */}
        <div className="space-y-4">
          {displayed.map((q, i) => {
            const isEditing = editingId === q.id;
            const isNew = q.id.startsWith(NEW_QUESTION_PREFIX);
            const type = (isEditing ? editDraft.type : q.type) ?? q.type;

            return (
              <Card key={q.id} className={isEditing ? 'ring-2 ring-primary/30' : ''}>
                <CardHeader className="pb-2">
                  <div className="flex items-start justify-between gap-3">
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className="text-sm font-semibold text-muted-foreground">Q{i + 1}</span>
                      <Badge variant="outline" className={`text-xs ${questionTypeBadgeClass(type)}`}>
                        {type}
                      </Badge>
                      <Badge variant="outline" className={`text-xs ${difficultyBadgeClass(q.difficulty)}`}>
                        {q.difficulty}
                      </Badge>
                    </div>
                    {!isEditing && (
                      <div className="flex gap-1">
                        <Button variant="ghost" size="icon" className="h-7 w-7" onClick={() => startEdit(q)}
                          disabled={!!editingId} title="Modifier">
                          <Pencil size={14} />
                        </Button>
                        <Button variant="ghost" size="icon" className="h-7 w-7 text-destructive hover:text-destructive"
                          onClick={() => removeQuestion(q)} disabled={!!editingId || updateMutation.isPending}
                          title="Supprimer la question">
                          <Trash2 size={14} />
                        </Button>
                      </div>
                    )}
                  </div>
                </CardHeader>

                <CardContent className="space-y-4">
                  {isEditing ? (
                    /* ── Mode édition ── */
                    <div className="space-y-4">
                      {isNew && (
                        <div className="space-y-1.5">
                          <Label>Type de question</Label>
                          <Select
                            value={type}
                            onValueChange={v => setEditDraft(d => ({
                              ...d,
                              type: v,
                              options: v === 'QCM' ? (d.options?.length ? d.options : ['', '', '', '']) : [],
                            }))}
                          >
                            <SelectTrigger className="w-48"><SelectValue /></SelectTrigger>
                            <SelectContent>
                              <SelectItem value="QCM">QCM</SelectItem>
                              <SelectItem value="OUVERTE">Ouverte</SelectItem>
                              <SelectItem value="EXERCICE">Exercice</SelectItem>
                            </SelectContent>
                          </Select>
                        </div>
                      )}

                      <div className="space-y-1.5">
                        <Label>Énoncé</Label>
                        <Textarea
                          value={editDraft.content ?? ''}
                          onChange={e => setEditDraft(d => ({ ...d, content: e.target.value }))}
                          rows={3}
                        />
                      </div>

                      {type === 'QCM' && (
                        <div className="space-y-1.5">
                          <Label>Options (4, une par ligne)</Label>
                          <Textarea
                            value={(editDraft.options ?? []).join('\n')}
                            onChange={e =>
                              setEditDraft(d => ({ ...d, options: e.target.value.split('\n') }))
                            }
                            rows={4}
                            placeholder="Option 1&#10;Option 2&#10;Option 3&#10;Option 4"
                          />
                        </div>
                      )}

                      <div className="space-y-1.5">
                        <Label>{type === 'QCM' ? 'Réponse correcte (identique à une option)' : 'Réponse attendue'}</Label>
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
                        <Button size="sm" variant="ghost" onClick={cancelEdit}>
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

        {!isGenerating && !newQuestion && (
          <Button variant="outline" className="w-full border-dashed" onClick={startAddQuestion} disabled={!!editingId}>
            <Plus size={16} className="mr-2" />
            Ajouter une question
          </Button>
        )}
      </div>
    </EnseignantLayout>
  );
}
