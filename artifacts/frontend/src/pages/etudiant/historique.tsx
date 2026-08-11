import { Link } from 'wouter';
import { useListMyAttempts } from '@workspace/api-client-react';
import { EtudiantLayout } from '@/components/layout/etudiant-layout';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { History, Search, ArrowRight, CheckCircle2, XCircle } from 'lucide-react';
import { format } from 'date-fns';
import { fr } from 'date-fns/locale';

export default function Historique() {
  const { data: attemptsData, isLoading } = useListMyAttempts({ size: 100 });
  const attempts = attemptsData?.content || [];

  return (
    <EtudiantLayout>
      <div className="space-y-6">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">Historique des évaluations</h1>
          <p className="text-muted-foreground mt-1">Consultez vos résultats passés et vos corrections.</p>
        </div>

        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <History size={20} className="text-primary" />
              Toutes vos tentatives
            </CardTitle>
            <CardDescription>
              Historique complet de vos passages de quiz
            </CardDescription>
          </CardHeader>
          <CardContent className="p-0">
            {isLoading ? (
              <div className="p-8 text-center text-muted-foreground">Chargement...</div>
            ) : attempts.length === 0 ? (
              <div className="p-12 text-center text-muted-foreground flex flex-col items-center">
                <Search size={48} className="text-muted/30 mb-4" />
                <p>Aucun historique disponible.</p>
                <Button variant="link" asChild className="mt-2">
                  <Link href="/etudiant/rejoindre">Rejoindre une session</Link>
                </Button>
              </div>
            ) : (
              <div className="divide-y">
                {attempts.map(attempt => {
                  const isSuccess = (attempt.scorePercent || 0) >= 0.6;
                  const isSubmitted = attempt.attemptStatus === 'SUBMITTED';
                  
                  return (
                    <div key={attempt.id} className="p-4 sm:p-6 hover:bg-slate-50 transition-colors flex flex-col sm:flex-row gap-4 justify-between sm:items-center">
                      <div className="space-y-1.5">
                        <Link href={isSubmitted ? `/etudiant/resultats/${attempt.id}` : `/etudiant/examens/${attempt.id}`} className="font-semibold text-lg text-foreground hover:underline">
                          Session de quiz
                        </Link>
                        <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm text-muted-foreground">
                          <span>{attempt.startedAt ? format(new Date(attempt.startedAt), "d MMMM yyyy 'à' HH:mm", { locale: fr }) : ''}</span>
                        </div>
                      </div>

                      <div className="flex items-center justify-between sm:justify-end gap-6 w-full sm:w-auto">
                        {isSubmitted ? (
                          <div className={`flex flex-col items-end`}>
                            <span className={`text-xl font-bold tracking-tighter ${isSuccess ? 'text-green-600' : 'text-red-600'}`}>
                              {Math.round((attempt.scorePercent || 0) * 100)}%
                            </span>
                            <span className="text-xs font-medium text-muted-foreground">
                              {attempt.score} / {attempt.maxScore} pts
                            </span>
                          </div>
                        ) : (
                          <Badge variant="secondary">En cours</Badge>
                        )}
                        
                        <Button variant={isSubmitted ? "outline" : "default"} asChild>
                          <Link href={isSubmitted ? `/etudiant/resultats/${attempt.id}` : `/etudiant/examens/${attempt.id}`}>
                            {isSubmitted ? 'Détails' : 'Continuer'}
                            {isSubmitted && <ArrowRight size={16} className="ml-2" />}
                          </Link>
                        </Button>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </EtudiantLayout>
  );
}
