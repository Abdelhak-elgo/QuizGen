import { useRoute, Link } from 'wouter';
import { 
  useGetSession, 
  useCancelSession,
  getGetSessionQueryKey
} from '@workspace/api-client-react';
import { useQueryClient } from '@tanstack/react-query';
import { EnseignantLayout } from '@/components/layout/enseignant-layout';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { ArrowLeft, Ban, Calendar, Key, PlayCircle, Users, BarChart3 } from 'lucide-react';
import { format, isPast, isFuture } from 'date-fns';
import { fr } from 'date-fns/locale';
import { toast } from 'sonner';

export default function SessionDetail() {
  const [match, params] = useRoute('/enseignant/sessions/:id');
  const sessionId = params?.id || '';
  const queryClient = useQueryClient();

  const { data: session, isLoading } = useGetSession(sessionId, {
    query: {
      enabled: !!sessionId,
      queryKey: getGetSessionQueryKey(sessionId)
    }
  });

  const cancelSession = useCancelSession();

  const handleCancel = async () => {
    if (!confirm('Êtes-vous sûr de vouloir annuler cette session ? Les étudiants ne pourront plus y participer.')) return;
    
    try {
      await cancelSession.mutateAsync({ id: sessionId });
      queryClient.invalidateQueries({ queryKey: getGetSessionQueryKey(sessionId) });
      toast.success('Session annulée.');
    } catch (e) {
      toast.error('Erreur lors de l\'annulation');
    }
  };

  if (!match) return null;

  if (isLoading) {
    return <EnseignantLayout><div className="flex justify-center p-12">Chargement...</div></EnseignantLayout>;
  }

  if (!session) {
    return <EnseignantLayout><div className="text-center p-12 text-muted-foreground">Session introuvable</div></EnseignantLayout>;
  }

  const getStatusDisplay = () => {
    switch (session.sessionStatus) {
      case 'SCHEDULED': return { label: 'Planifiée', color: 'bg-blue-100 text-blue-800 border-blue-200' };
      case 'OPEN': return { label: 'Ouverte', color: 'bg-green-100 text-green-800 border-green-200' };
      case 'CLOSED': return { label: 'Fermée', color: 'bg-slate-100 text-slate-800 border-slate-200' };
      case 'CANCELLED': return { label: 'Annulée', color: 'bg-red-100 text-red-800 border-red-200' };
      default: return { label: session.sessionStatus, color: '' };
    }
  };

  const status = getStatusDisplay();
  const canCancel = session.sessionStatus === 'SCHEDULED' || session.sessionStatus === 'OPEN';

  return (
    <EnseignantLayout>
      <div className="max-w-4xl mx-auto space-y-6">
        <div className="flex items-center gap-3">
          <Button variant="ghost" size="icon" onClick={() => window.history.back()}>
            <ArrowLeft size={20} />
          </Button>
          <div>
            <div className="flex items-center gap-3">
              <h1 className="text-2xl font-bold tracking-tight">Détails de la session</h1>
              <Badge variant="outline" className={status.color}>{status.label}</Badge>
            </div>
          </div>
        </div>

        <div className="grid md:grid-cols-3 gap-6">
          <Card className="md:col-span-2">
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <PlayCircle size={20} className="text-primary" />
                Informations générales
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-6">
              <div>
                <p className="text-sm font-medium text-muted-foreground mb-1">Quiz évalué</p>
                <Link href={`/enseignant/quizzes/${session.quizId}`} className="text-lg font-semibold text-primary hover:underline flex items-center">
                  {session.quizTitle}
                </Link>
              </div>

              <div className="grid sm:grid-cols-2 gap-4 bg-slate-50 p-4 rounded-lg border">
                <div>
                  <p className="text-xs font-medium text-muted-foreground mb-1 flex items-center gap-1">
                    <Calendar size={14} /> Début
                  </p>
                  <p className="text-sm font-medium">
                    {format(new Date(session.startTime), "EEEE d MMMM yyyy", { locale: fr })}<br/>
                    {format(new Date(session.startTime), "HH:mm", { locale: fr })}
                  </p>
                </div>
                <div>
                  <p className="text-xs font-medium text-muted-foreground mb-1 flex items-center gap-1">
                    <Calendar size={14} /> Fin
                  </p>
                  <p className="text-sm font-medium">
                    {format(new Date(session.endTime), "EEEE d MMMM yyyy", { locale: fr })}<br/>
                    {format(new Date(session.endTime), "HH:mm", { locale: fr })}
                  </p>
                </div>
              </div>

              {session.accessCode && (
                <div className="flex items-center gap-3 p-3 bg-amber-50 border border-amber-200 rounded-md text-amber-800">
                  <Key size={18} />
                  <div>
                    <p className="text-xs font-semibold uppercase">Code d'accès requis</p>
                    <p className="font-mono font-bold tracking-widest">{session.accessCode}</p>
                  </div>
                </div>
              )}
            </CardContent>
          </Card>

          <div className="space-y-6">
            <Card>
              <CardHeader>
                <CardTitle className="text-lg flex items-center gap-2">
                  <Users size={18} className="text-primary" />
                  Participation
                </CardTitle>
              </CardHeader>
              <CardContent>
                <div className="text-center py-4">
                  <span className="text-4xl font-bold text-foreground">{session.attemptCount || 0}</span>
                  <p className="text-sm text-muted-foreground mt-1">étudiants ont participé</p>
                </div>
                
                <div className="pt-4 border-t mt-4 space-y-3">
                  <Button asChild className="w-full" variant="outline">
                    <Link href={`/enseignant/analytics/${session.quizId}`}>
                      <BarChart3 size={16} className="mr-2" />
                      Voir les statistiques du quiz
                    </Link>
                  </Button>
                  
                  {canCancel && (
                    <Button 
                      variant="destructive" 
                      className="w-full bg-red-50 text-red-600 hover:bg-red-100 hover:text-red-700 border-red-200" 
                      onClick={handleCancel}
                      disabled={cancelSession.isPending}
                    >
                      <Ban size={16} className="mr-2" />
                      {cancelSession.isPending ? 'Annulation...' : 'Annuler la session'}
                    </Button>
                  )}
                </div>
              </CardContent>
            </Card>
          </div>
        </div>
      </div>
    </EnseignantLayout>
  );
}
