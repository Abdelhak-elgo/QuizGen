import { useState, type ReactNode } from 'react';
import { Link } from 'wouter';
import { getListSessionsQueryKey, useListSessions, type ListSessionsParams } from '@workspace/api-client-react';
import { format } from 'date-fns';
import { fr } from 'date-fns/locale';
import { Calendar, Key, PlayCircle, UserIcon, Users } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { ListPagination } from '@/components/list-pagination';
import { SessionStatusBadge } from '@/components/session/session-status-badge';

const PAGE_SIZE = 15;

interface SessionListProps {
  /** Vue administrateur : affiche l'enseignant, sans lien vers le détail enseignant. */
  adminView?: boolean;
  /** Contenu affiché quand aucune session n'existe (ex. lien de création). */
  emptyAction?: ReactNode;
}

/** Liste complète et paginée des sessions (les plus récentes d'abord). */
export function SessionList({ adminView = false, emptyAction }: SessionListProps) {
  const [page, setPage] = useState(0);
  const params: ListSessionsParams = { page, size: PAGE_SIZE };
  const { data, isLoading, isError } = useListSessions(params, {
    query: { queryKey: getListSessionsQueryKey(params) },
  });
  const sessions = data?.content ?? [];

  if (isLoading) {
    return (
      <div className="space-y-4 p-6">
        {[1, 2, 3].map(i => <Skeleton key={i} className="h-12 w-full" />)}
      </div>
    );
  }
  if (isError) {
    return (
      <div className="m-4 rounded-md border border-destructive/20 bg-destructive/10 p-4 text-center text-sm text-destructive">
        Impossible de charger les sessions. Réessayez dans quelques instants.
      </div>
    );
  }
  if (sessions.length === 0) {
    return (
      <div className="flex flex-col items-center p-12 text-center text-muted-foreground">
        <PlayCircle size={48} className="mb-4 text-muted/30" />
        <p>Aucune session n'a été créée.</p>
        {emptyAction}
      </div>
    );
  }

  return (
    <div>
      <div className="divide-y">
        {sessions.map(session => (
          <div key={session.id} className="flex flex-col justify-between gap-4 p-4 transition-colors hover:bg-slate-50 sm:flex-row sm:items-center">
            <div className="flex items-start gap-4">
              <div className={`mt-1 rounded-md p-2 ${session.sessionStatus === 'OPEN' ? 'bg-green-100 text-green-600' : 'bg-primary/10 text-primary'}`}>
                <PlayCircle size={20} />
              </div>
              <div className="space-y-1">
                {adminView ? (
                  <span className="text-base font-medium text-foreground">{session.quizTitle}</span>
                ) : (
                  <Link href={`/enseignant/sessions/${session.id}`} className="text-base font-medium text-foreground hover:underline">
                    {session.quizTitle}
                  </Link>
                )}
                <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-muted-foreground">
                  {adminView && (
                    <span className="flex items-center gap-1">
                      <UserIcon size={12} />
                      {session.teacherName ?? 'Enseignant inconnu'}
                    </span>
                  )}
                  <span className="flex items-center gap-1">
                    <Calendar size={12} />
                    {format(new Date(session.startTime), "d MMM yyyy 'à' HH:mm", { locale: fr })}
                  </span>
                  <span className="flex items-center gap-1">
                    <Users size={12} />
                    {session.attemptCount || 0} participant(s)
                  </span>
                  {session.accessCode && (
                    <span className="flex items-center gap-1 font-mono text-slate-500">
                      <Key size={12} />
                      {session.accessCode}
                    </span>
                  )}
                </div>
              </div>
            </div>
            <div className="flex shrink-0 flex-col gap-2 sm:items-end">
              <SessionStatusBadge status={session.sessionStatus} />
              {!adminView && (
                <Button variant="ghost" size="sm" asChild className="h-8">
                  <Link href={`/enseignant/sessions/${session.id}`}>Détails</Link>
                </Button>
              )}
            </div>
          </div>
        ))}
      </div>
      <ListPagination
        page={page}
        totalPages={data?.totalPages ?? 0}
        totalElements={data?.totalElements ?? 0}
        onPageChange={setPage}
      />
    </div>
  );
}
