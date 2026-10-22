import { useState } from 'react';
import { Link } from 'wouter';
import {
  getListQuizzesQueryKey,
  useListQuizzes,
  type ListQuizzesParams,
  type ListQuizzesStatus,
} from '@workspace/api-client-react';
import { format } from 'date-fns';
import { fr } from 'date-fns/locale';
import { BarChart3, FileText } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { ListPagination } from '@/components/list-pagination';
import { QUIZ_STATUS_LABELS, QuizStatusBadge } from '@/components/quiz/quiz-status-badge';

const PAGE_SIZE = 15;
// Quiz, [Enseignant | Actions], Questions, Statut, Créé le
const COLUMN_COUNT = 5;
const GENERATION_POLL_MS = 5000;
const STATUS_FILTERS: (ListQuizzesStatus | 'ALL')[] = ['ALL', 'PUBLISHED', 'REVIEWING', 'DRAFT', 'FAILED', 'ARCHIVED'];
const DIFFICULTY_LABELS: Record<string, string> = { FACILE: 'Facile', MOYEN: 'Moyen', DIFFICILE: 'Difficile' };

interface QuizListProps {
  /** Vue administrateur : colonne « Enseignant », sans lien vers l'éditeur. */
  adminView?: boolean;
}

/** Liste complète et paginée des quiz, filtrable par statut. */
export function QuizList({ adminView = false }: QuizListProps) {
  const [page, setPage] = useState(0);
  const [status, setStatus] = useState<ListQuizzesStatus | 'ALL'>('ALL');

  const params: ListQuizzesParams = { page, size: PAGE_SIZE, ...(status === 'ALL' ? {} : { status }) };
  const { data, isLoading, isError } = useListQuizzes(params, {
    query: {
      queryKey: getListQuizzesQueryKey(params),
      // Le backend fait avancer les générations en cours à chaque liste
      refetchInterval: (query) =>
        query.state.data?.content?.some(q => q.quizStatus === 'DRAFT' && q.nlpTaskId) ? GENERATION_POLL_MS : false,
    },
  });
  const quizzes = data?.content ?? [];

  const changeStatus = (value: string) => {
    setStatus(value as ListQuizzesStatus | 'ALL');
    setPage(0);
  };

  return (
    <div>
      <div className="overflow-x-auto px-4 pt-4">
        <Tabs value={status} onValueChange={changeStatus}>
          <TabsList aria-label="Filtrer par statut">
            {STATUS_FILTERS.map(s => (
              <TabsTrigger key={s} value={s}>{s === 'ALL' ? 'Tous' : QUIZ_STATUS_LABELS[s]}</TabsTrigger>
            ))}
          </TabsList>
        </Tabs>
      </div>

      {isError ? (
        <div className="m-4 rounded-md border border-destructive/20 bg-destructive/10 p-4 text-center text-sm text-destructive">
          Impossible de charger les quiz. Réessayez dans quelques instants.
        </div>
      ) : (
        <div className="mt-4 overflow-x-auto border-t">
          <Table>
            <TableHeader className="bg-slate-50/80">
              <TableRow className="hover:bg-transparent">
                <TableHead>Quiz</TableHead>
                {adminView && <TableHead>Enseignant</TableHead>}
                <TableHead className="hidden md:table-cell">Questions</TableHead>
                <TableHead>Statut</TableHead>
                <TableHead className="hidden sm:table-cell">Créé le</TableHead>
                {!adminView && <TableHead className="text-right">Actions</TableHead>}
              </TableRow>
            </TableHeader>
            <TableBody>
              {isLoading ? (
                Array.from({ length: 5 }).map((_, i) => (
                  <TableRow key={i}>
                    <TableCell colSpan={COLUMN_COUNT}><Skeleton className="h-5 w-full" /></TableCell>
                  </TableRow>
                ))
              ) : quizzes.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={COLUMN_COUNT} className="h-28 text-center text-muted-foreground">
                    <FileText className="mx-auto mb-2 h-8 w-8 opacity-20" />
                    {status === 'ALL' ? 'Aucun quiz pour le moment.' : 'Aucun quiz avec ce statut.'}
                  </TableCell>
                </TableRow>
              ) : (
                quizzes.map(quiz => {
                  const isGenerating = quiz.quizStatus === 'DRAFT' && !!quiz.nlpTaskId;
                  return (
                    <TableRow key={quiz.id}>
                      <TableCell className="max-w-[280px]">
                        {adminView ? (
                          <span className="font-medium">{quiz.title}</span>
                        ) : (
                          <Link href={`/enseignant/quizzes/${quiz.id}`} className="font-medium hover:underline">
                            {quiz.title}
                          </Link>
                        )}
                        <div className="truncate text-xs text-muted-foreground">
                          {quiz.documentName ?? 'Document inconnu'} · {DIFFICULTY_LABELS[quiz.difficulty] ?? quiz.difficulty}
                        </div>
                      </TableCell>
                      {adminView && <TableCell className="text-sm">{quiz.teacherName ?? '—'}</TableCell>}
                      <TableCell className="hidden md:table-cell text-sm">
                        {quiz.questions?.length ?? 0} / {quiz.nbQuestions}
                      </TableCell>
                      <TableCell><QuizStatusBadge status={quiz.quizStatus} isGenerating={isGenerating} /></TableCell>
                      <TableCell className="hidden sm:table-cell text-sm text-muted-foreground">
                        {quiz.createdAt ? format(new Date(quiz.createdAt), 'dd MMM yyyy', { locale: fr }) : '—'}
                      </TableCell>
                      {!adminView && (
                        <TableCell className="text-right">
                          <div className="flex justify-end gap-1">
                            {quiz.quizStatus === 'PUBLISHED' && (
                              <Button variant="ghost" size="sm" asChild className="h-8" title="Analytique">
                                <Link href={`/enseignant/analytics/${quiz.id}`} aria-label={`Analytique de ${quiz.title}`}>
                                  <BarChart3 size={14} />
                                </Link>
                              </Button>
                            )}
                            <Button variant="ghost" size="sm" asChild className="h-8">
                              <Link href={`/enseignant/quizzes/${quiz.id}`}>Ouvrir</Link>
                            </Button>
                          </div>
                        </TableCell>
                      )}
                    </TableRow>
                  );
                })
              )}
            </TableBody>
          </Table>
        </div>
      )}

      {data && (
        <ListPagination
          page={page}
          totalPages={data.totalPages}
          totalElements={data.totalElements}
          onPageChange={setPage}
        />
      )}
    </div>
  );
}
