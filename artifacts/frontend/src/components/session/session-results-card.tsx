import { Link } from 'wouter';
import {
  useGetSessionResults,
  getGetSessionResultsQueryKey,
  StudentResult,
} from '@workspace/api-client-react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Progress } from '@/components/ui/progress';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { ClipboardList, Sparkles } from 'lucide-react';
import { format } from 'date-fns';
import { fr } from 'date-fns/locale';

// Les copies arrivent pendant la session : rafraîchissement régulier tant qu'elle est ouverte
const LIVE_REFRESH_MS = 15000;
const PASSING_PERCENT = 60;

interface SessionResultsCardProps {
  sessionId: string;
  isLive: boolean;
}

function studentName(s: StudentResult): string {
  return [s.firstName, s.lastName].filter(Boolean).join(' ') || s.email;
}

function StatusBadge({ student, sessionEnded }: { student: StudentResult; sessionEnded: boolean }) {
  switch (student.status) {
    case 'SUBMITTED':
      return <Badge className="bg-green-600 hover:bg-green-700">Rendu</Badge>;
    case 'IN_PROGRESS':
      return <Badge variant="outline" className="bg-blue-50 text-blue-700 border-blue-200">En cours</Badge>;
    case 'EXPIRED':
      return <Badge variant="outline" className="bg-amber-50 text-amber-700 border-amber-200">Temps écoulé</Badge>;
    default:
      return sessionEnded
        ? <Badge variant="outline" className="bg-red-50 text-red-700 border-red-200">Absent</Badge>
        : <Badge variant="secondary">Pas commencé</Badge>;
  }
}

function SummaryTile({ label, value, hint }: { label: string; value: string; hint?: string }) {
  return (
    <div className="rounded-lg border bg-slate-50 p-3">
      <p className="text-xs font-medium text-muted-foreground">{label}</p>
      <p className="text-2xl font-bold tracking-tight">{value}</p>
      {hint && <p className="text-xs text-muted-foreground">{hint}</p>}
    </div>
  );
}

/** Tableau de suivi de la session : statut, score et accès à la copie de chaque étudiant. */
export function SessionResultsCard({ sessionId, isLive }: SessionResultsCardProps) {
  const { data: results, isLoading } = useGetSessionResults(sessionId, {
    query: {
      enabled: !!sessionId,
      queryKey: getGetSessionResultsQueryKey(sessionId),
      refetchInterval: isLive ? LIVE_REFRESH_MS : false,
    },
  });

  const sessionEnded = !!results && (results.sessionStatus === 'CLOSED' || new Date(results.endTime) < new Date());
  const summary = results?.summary;

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <ClipboardList size={20} className="text-primary" />
          Résultats des étudiants
        </CardTitle>
        <CardDescription>
          Statut, score et copie de chaque étudiant{isLive ? ' — mis à jour automatiquement' : ''}.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        {isLoading || !results || !summary ? (
          <p className="text-sm text-muted-foreground">Chargement des résultats…</p>
        ) : (
          <>
            <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
              <SummaryTile label="Copies rendues" value={`${summary.submitted} / ${summary.totalStudents}`}
                hint={`${summary.inProgress} en cours · ${summary.notStarted} non commencé(s)`} />
              <SummaryTile label="Moyenne"
                value={summary.averagePercent != null ? `${Math.round(summary.averagePercent)}%` : '—'} />
              <SummaryTile label={`Réussite (≥ ${PASSING_PERCENT}%)`}
                value={summary.passRate != null ? `${Math.round(summary.passRate)}%` : '—'} />
              <SummaryTile label="Questions" value={String(results.nbQuestions)} />
            </div>

            {results.students.length === 0 ? (
              <p className="text-sm text-muted-foreground bg-slate-50 border border-dashed rounded-md p-4">
                Aucun étudiant pour le moment : assignez des étudiants ou communiquez l'identifiant de la session.
              </p>
            ) : (
              <div className="overflow-x-auto">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Étudiant</TableHead>
                      <TableHead>Statut</TableHead>
                      <TableHead className="w-48">Score</TableHead>
                      <TableHead>Rendu le</TableHead>
                      <TableHead className="text-right">Copie</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {results.students.map(s => (
                      <TableRow key={s.studentId}>
                        <TableCell>
                          <p className="font-medium">{studentName(s)}</p>
                          <p className="text-xs text-muted-foreground">
                            {s.email}{!s.assigned && ' · a rejoint via l\'identifiant'}
                          </p>
                        </TableCell>
                        <TableCell><StatusBadge student={s} sessionEnded={sessionEnded} /></TableCell>
                        <TableCell>
                          {s.percent != null ? (
                            <div className="space-y-1">
                              <div className="flex justify-between text-sm">
                                <span className={`font-semibold ${s.percent >= PASSING_PERCENT ? 'text-green-700' : 'text-red-700'}`}>
                                  {Math.round(s.percent)}%
                                </span>
                                <span className="text-muted-foreground">{s.score} / {s.maxScore}</span>
                              </div>
                              <Progress value={s.percent} className="h-1.5" />
                            </div>
                          ) : <span className="text-muted-foreground">—</span>}
                        </TableCell>
                        <TableCell className="text-sm text-muted-foreground">
                          {s.completedAt ? format(new Date(s.completedAt), 'd MMM HH:mm', { locale: fr }) : '—'}
                        </TableCell>
                        <TableCell className="text-right">
                          {s.attemptId && s.status !== 'IN_PROGRESS' ? (
                            <Button asChild size="sm" variant="outline">
                              <Link href={`/enseignant/copies/${s.attemptId}`}>
                                {s.hasRecommendation && <Sparkles size={13} className="mr-1.5 text-violet-600" />}
                                Voir la copie
                              </Link>
                            </Button>
                          ) : <span className="text-xs text-muted-foreground">—</span>}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
            )}
          </>
        )}
      </CardContent>
    </Card>
  );
}
