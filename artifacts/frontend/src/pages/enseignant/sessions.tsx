import { Link } from 'wouter';
import { useListSessions } from '@workspace/api-client-react';
import { EnseignantLayout } from '@/components/layout/enseignant-layout';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { format } from 'date-fns';
import { fr } from 'date-fns/locale';
import { Calendar, Key, PlayCircle, Plus, Users } from 'lucide-react';

export default function SessionsList() {
  const { data: sessionsData, isLoading } = useListSessions({ size: 100 });
  const sessions = sessionsData?.content || [];

  const getSessionBadge = (status: string) => {
    switch (status) {
      case 'SCHEDULED': return <Badge variant="outline" className="bg-blue-50 text-blue-700 border-blue-200">Planifié</Badge>;
      case 'OPEN': return <Badge variant="default" className="bg-green-600 hover:bg-green-700">Ouvert</Badge>;
      case 'CLOSED': return <Badge variant="secondary">Fermé</Badge>;
      case 'CANCELLED': return <Badge variant="destructive">Annulé</Badge>;
      default: return <Badge>{status}</Badge>;
    }
  };

  return (
    <EnseignantLayout>
      <div className="space-y-6">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div>
            <h1 className="text-3xl font-bold tracking-tight">Sessions d'évaluation</h1>
            <p className="text-muted-foreground mt-1">Organisez et suivez les passages de vos quiz.</p>
          </div>
          <Button asChild>
            <Link href="/enseignant/sessions/new">
              <Plus size={16} className="mr-2" />
              Nouvelle Session
            </Link>
          </Button>
        </div>

        <Card>
          <CardHeader>
            <CardTitle>Toutes vos sessions</CardTitle>
            <CardDescription>Liste de toutes les sessions passées, en cours et futures.</CardDescription>
          </CardHeader>
          <CardContent className="p-0">
            {isLoading ? (
              <div className="p-8 text-center text-muted-foreground">Chargement...</div>
            ) : sessions.length === 0 ? (
              <div className="p-12 text-center text-muted-foreground flex flex-col items-center">
                <PlayCircle size={48} className="text-muted/30 mb-4" />
                <p>Aucune session n'a été créée.</p>
                <Button variant="link" asChild className="mt-2">
                  <Link href="/enseignant/sessions/new">Planifier votre première session</Link>
                </Button>
              </div>
            ) : (
              <div className="divide-y">
                {sessions.map(session => (
                  <div key={session.id} className="flex flex-col sm:flex-row sm:items-center justify-between p-4 hover:bg-slate-50 transition-colors gap-4">
                    <div className="flex items-start gap-4">
                      <div className={`mt-1 p-2 rounded-md ${session.sessionStatus === 'OPEN' ? 'bg-green-100 text-green-600' : 'bg-primary/10 text-primary'}`}>
                        <PlayCircle size={20} />
                      </div>
                      <div className="space-y-1">
                        <Link href={`/enseignant/sessions/${session.id}`} className="font-medium text-base text-foreground hover:underline">
                          {session.quizTitle}
                        </Link>
                        <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-muted-foreground">
                          <span className="flex items-center gap-1">
                            <Calendar size={12} />
                            {format(new Date(session.startTime), "d MMM yyyy 'à' HH:mm", { locale: fr })}
                          </span>
                          <span className="flex items-center gap-1">
                            <Users size={12} />
                            {session.attemptCount || 0} participants
                          </span>
                          {session.accessCode && (
                            <span className="flex items-center gap-1 text-slate-500 font-mono">
                              <Key size={12} />
                              {session.accessCode}
                            </span>
                          )}
                        </div>
                      </div>
                    </div>
                    <div className="flex flex-col sm:items-end gap-2 shrink-0">
                      {getSessionBadge(session.sessionStatus)}
                      <Button variant="ghost" size="sm" asChild className="h-8">
                        <Link href={`/enseignant/sessions/${session.id}`}>Détails</Link>
                      </Button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </EnseignantLayout>
  );
}
