import { Link, useLocation } from 'wouter';
import { useListAssignedSessions, useJoinSession, AssignedSession } from '@workspace/api-client-react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { CalendarClock, Loader2, PlayCircle } from 'lucide-react';
import { format } from 'date-fns';
import { fr } from 'date-fns/locale';
import { toast } from 'sonner';
import { apiErrorMessage } from '@/lib/api-error';

type SessionAction =
  | { kind: 'results'; attemptId: string }
  | { kind: 'resume'; attemptId: string }
  | { kind: 'start' }
  | { kind: 'upcoming' }
  | { kind: 'ended' };

/** Ce que l'étudiant peut faire sur la session maintenant. */
function actionFor(session: AssignedSession, now: Date): SessionAction {
  if (session.attemptId && session.attemptStatus === 'SUBMITTED') {
    return { kind: 'results', attemptId: session.attemptId };
  }
  const ended = session.sessionStatus === 'CLOSED' || now > new Date(session.endTime);
  if (ended) return { kind: 'ended' };
  if (session.attemptId) return { kind: 'resume', attemptId: session.attemptId };
  if (now < new Date(session.startTime)) return { kind: 'upcoming' };
  return { kind: 'start' };
}

function formatDate(iso: string): string {
  return format(new Date(iso), "EEEE d MMMM 'à' HH:mm", { locale: fr });
}

/** Sessions auxquelles l'enseignant a assigné l'étudiant, avec accès direct. */
export function AssignedSessionsCard() {
  const [, navigate] = useLocation();
  const { data: sessions, isLoading } = useListAssignedSessions();
  const joinSession = useJoinSession();

  const start = async (sessionId: string) => {
    try {
      const attempt = await joinSession.mutateAsync({ id: sessionId, data: {} });
      navigate(`/etudiant/examens/${attempt.id}`);
    } catch (error) {
      toast.error(apiErrorMessage(error, 'Impossible de démarrer la session'));
    }
  };

  const now = new Date();

  return (
    <Card className="border-primary/20">
      <CardHeader className="pb-2">
        <CardTitle className="text-xl flex items-center gap-2">
          <CalendarClock size={20} className="text-primary" />
          Mes sessions
        </CardTitle>
        <CardDescription>Les évaluations auxquelles vos enseignants vous ont inscrit</CardDescription>
      </CardHeader>
      <CardContent>
        {isLoading ? (
          <div className="py-6 text-center text-muted-foreground">Chargement…</div>
        ) : !sessions || sessions.length === 0 ? (
          <div className="text-center py-8 text-muted-foreground bg-slate-50 rounded-lg border border-dashed">
            <p>Aucune session ne vous est assignée pour le moment.</p>
            <p className="text-sm mt-1">Si votre enseignant vous a donné un identifiant, utilisez « Rejoindre une session ».</p>
          </div>
        ) : (
          <div className="divide-y">
            {sessions.map(session => {
              const action = actionFor(session, now);
              return (
                <div key={session.id} className="py-4 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                  <div className="space-y-1 min-w-0">
                    <p className="font-semibold text-lg truncate">{session.quizTitle}</p>
                    <p className="text-sm text-muted-foreground">
                      {session.teacherName && <>{session.teacherName} · </>}
                      {session.nbQuestions ? `${session.nbQuestions} questions · ` : ''}
                      du {formatDate(session.startTime)} au {formatDate(session.endTime)}
                    </p>
                  </div>

                  <div className="shrink-0">
                    {action.kind === 'start' && (
                      <Button onClick={() => start(session.id)} disabled={joinSession.isPending}>
                        {joinSession.isPending
                          ? <Loader2 size={16} className="animate-spin mr-2" />
                          : <PlayCircle size={16} className="mr-2" />}
                        Commencer
                      </Button>
                    )}
                    {action.kind === 'resume' && (
                      <Button asChild variant="outline">
                        <Link href={`/etudiant/examens/${action.attemptId}`}>Reprendre</Link>
                      </Button>
                    )}
                    {action.kind === 'results' && (
                      <Button asChild variant="outline">
                        <Link href={`/etudiant/resultats/${action.attemptId}`}>Voir la correction</Link>
                      </Button>
                    )}
                    {action.kind === 'upcoming' && (
                      <Badge variant="outline" className="bg-blue-50 text-blue-700 border-blue-200">
                        Ouvre le {format(new Date(session.startTime), 'd MMM à HH:mm', { locale: fr })}
                      </Badge>
                    )}
                    {action.kind === 'ended' && <Badge variant="secondary">Terminée</Badge>}
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </CardContent>
    </Card>
  );
}
