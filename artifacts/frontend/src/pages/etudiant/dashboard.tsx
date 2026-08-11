import { Link } from 'wouter';
import { useGetStudentDashboard } from '@workspace/api-client-react';
import { EtudiantLayout } from '@/components/layout/etudiant-layout';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Brain, CheckCircle2, History, LogIn, Target, Trophy, XCircle } from 'lucide-react';
import { format } from 'date-fns';
import { fr } from 'date-fns/locale';

export default function EtudiantDashboard() {
  const { data: dashboard, isLoading } = useGetStudentDashboard();

  return (
    <EtudiantLayout>
      <div className="space-y-8">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div>
            <h1 className="text-3xl font-bold tracking-tight">Bonjour, {dashboard?.studentName || ''}</h1>
            <p className="text-muted-foreground mt-1">Prêt pour votre prochaine évaluation ?</p>
          </div>
          <Button size="lg" asChild className="text-base h-12 px-8 shadow-md">
            <Link href="/etudiant/rejoindre">
              <LogIn size={20} className="mr-2" />
              Rejoindre une session
            </Link>
          </Button>
        </div>

        <div className="grid gap-6 md:grid-cols-3">
          <Card className="bg-gradient-to-br from-primary/10 to-transparent border-primary/20">
            <CardContent className="pt-6">
              <div className="flex items-center gap-4">
                <div className="p-3 bg-primary/20 rounded-xl text-primary">
                  <Target size={28} />
                </div>
                <div>
                  <p className="text-sm font-medium text-muted-foreground">Moyenne Globale</p>
                  <div className="flex items-baseline gap-1">
                    <h3 className="text-4xl font-bold tracking-tighter text-foreground">
                      {dashboard?.averageScore !== undefined ? Math.round(dashboard.averageScore * 100) : '-'}
                    </h3>
                    <span className="text-lg font-medium text-muted-foreground">%</span>
                  </div>
                </div>
              </div>
            </CardContent>
          </Card>
          
          <Card>
            <CardContent className="pt-6">
              <div className="flex items-center gap-4">
                <div className="p-3 bg-slate-100 rounded-xl text-slate-700">
                  <Brain size={28} />
                </div>
                <div>
                  <p className="text-sm font-medium text-muted-foreground">Tentatives totales</p>
                  <h3 className="text-4xl font-bold tracking-tighter text-foreground">
                    {dashboard?.totalAttempts || 0}
                  </h3>
                </div>
              </div>
            </CardContent>
          </Card>

          <Card className="md:col-span-3 lg:col-span-1">
            <CardContent className="pt-6 flex flex-col justify-center h-full">
              <div className="text-center space-y-2">
                <Trophy size={40} className="mx-auto text-amber-500 mb-2 opacity-80" />
                <h3 className="font-semibold text-lg">Continuez ainsi !</h3>
                <p className="text-sm text-muted-foreground">La pratique régulière est la clé de la réussite.</p>
              </div>
            </CardContent>
          </Card>
        </div>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <div>
              <CardTitle className="text-xl">Tentatives récentes</CardTitle>
              <CardDescription>Vos dernières évaluations passées</CardDescription>
            </div>
            <Button variant="ghost" size="sm" asChild className="hidden sm:flex">
              <Link href="/etudiant/historique">
                Tout voir <History size={16} className="ml-2" />
              </Link>
            </Button>
          </CardHeader>
          <CardContent>
            {isLoading ? (
              <div className="py-8 text-center text-muted-foreground">Chargement...</div>
            ) : !dashboard?.recentAttempts || dashboard.recentAttempts.length === 0 ? (
              <div className="text-center py-12 text-muted-foreground bg-slate-50 rounded-lg border border-dashed">
                <p>Aucune tentative pour le moment.</p>
                <p className="text-sm mt-1">Rejoignez une session pour passer votre premier quiz.</p>
              </div>
            ) : (
              <div className="divide-y">
                {dashboard.recentAttempts.map((attempt) => {
                  const isSuccess = (attempt.scorePercent || 0) >= 0.6;
                  return (
                    <div key={attempt.attemptId} className="py-4 flex flex-col sm:flex-row sm:items-center justify-between gap-4 hover:bg-slate-50/50 transition-colors px-2 -mx-2 rounded-md">
                      <div className="space-y-1">
                        <Link href={`/etudiant/resultats/${attempt.attemptId}`} className="font-semibold text-lg hover:underline text-foreground">
                          {attempt.quizTitle}
                        </Link>
                        <div className="text-sm text-muted-foreground">
                          {attempt.completedAt ? format(new Date(attempt.completedAt), "d MMMM yyyy 'à' HH:mm", { locale: fr }) : 'En cours'}
                        </div>
                      </div>
                      
                      <div className="flex items-center gap-4">
                        {attempt.status === 'SUBMITTED' ? (
                          <div className={`flex items-center gap-2 px-3 py-1.5 rounded-full border ${isSuccess ? 'bg-green-50 border-green-200' : 'bg-red-50 border-red-200'}`}>
                            {isSuccess ? <CheckCircle2 size={18} className="text-green-600" /> : <XCircle size={18} className="text-red-600" />}
                            <span className={`font-bold ${isSuccess ? 'text-green-700' : 'text-red-700'}`}>
                              {Math.round((attempt.scorePercent || 0) * 100)}%
                            </span>
                          </div>
                        ) : (
                          <Badge variant="secondary">En cours</Badge>
                        )}
                        <Button variant="outline" size="sm" asChild>
                          <Link href={attempt.status === 'SUBMITTED' ? `/etudiant/resultats/${attempt.attemptId}` : `/etudiant/examens/${attempt.attemptId}`}>
                            {attempt.status === 'SUBMITTED' ? 'Correction' : 'Reprendre'}
                          </Link>
                        </Button>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
            
            <div className="mt-4 sm:hidden">
              <Button variant="outline" className="w-full" asChild>
                <Link href="/etudiant/historique">Voir tout l'historique</Link>
              </Button>
            </div>
          </CardContent>
        </Card>
      </div>
    </EtudiantLayout>
  );
}
