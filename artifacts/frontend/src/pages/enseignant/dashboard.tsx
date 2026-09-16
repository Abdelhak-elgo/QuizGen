import { Link } from 'wouter';
import { useListQuizzes, useListSessions, useGetMe } from '@workspace/api-client-react';
import { EnseignantLayout } from '@/components/layout/enseignant-layout';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { FileText, PlayCircle, Plus, Users } from 'lucide-react';
import { format } from 'date-fns';
import { fr } from 'date-fns/locale';

export default function EnseignantDashboard() {
  const { data: me } = useGetMe();
  const { data: quizzesData, isLoading: isLoadingQuizzes } = useListQuizzes({ size: 5 });
  const { data: sessionsData, isLoading: isLoadingSessions } = useListSessions({ size: 5 });

  const quizzes = quizzesData?.content || [];
  const sessions = sessionsData?.content || [];

  const getStatusBadge = (status: string) => {
    switch (status) {
      case 'DRAFT': return <Badge variant="secondary">Brouillon</Badge>;
      case 'REVIEWING': return <Badge variant="outline" className="bg-yellow-50 text-yellow-700 border-yellow-200">En révision</Badge>;
      case 'PUBLISHED': return <Badge variant="default" className="bg-green-600 hover:bg-green-700">Publié</Badge>;
      case 'ARCHIVED': return <Badge variant="secondary" className="opacity-50">Archivé</Badge>;
      default: return <Badge>{status}</Badge>;
    }
  };

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
      <div className="space-y-8">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div>
            <h1 className="text-3xl font-bold tracking-tight">Bonjour, {me?.firstName}</h1>
            <p className="text-muted-foreground mt-1">Voici un aperçu de vos activités récentes.</p>
          </div>
          <div className="flex gap-2">
            <Button asChild variant="outline">
              <Link href="/enseignant/documents">Gérer les documents</Link>
            </Button>
            <Button asChild>
              <Link href="/enseignant/quizzes/new">
                <Plus size={16} className="mr-2" />
                Nouveau Quiz
              </Link>
            </Button>
          </div>
        </div>

        <div className="grid gap-6 md:grid-cols-2">
          {/* Quizzes Récents */}
          <Card>
            <CardHeader className="pb-3 border-b">
              <div className="flex items-center justify-between">
                <div>
                  <CardTitle className="text-lg">Quiz Récents</CardTitle>
                  <CardDescription>Vos dernières créations de quiz</CardDescription>
                </div>
                <div className="p-2 bg-primary/10 rounded-md text-primary">
                  <FileText size={20} />
                </div>
              </div>
            </CardHeader>
            <CardContent className="pt-4 px-0">
              {isLoadingQuizzes ? (
                <div className="px-6 py-4 space-y-4">
                  {[1, 2, 3].map(i => (
                    <div key={i} className="flex justify-between items-center animate-pulse">
                      <div className="space-y-2">
                        <div className="h-4 w-32 bg-slate-200 rounded"></div>
                        <div className="h-3 w-20 bg-slate-100 rounded"></div>
                      </div>
                      <div className="h-6 w-16 bg-slate-200 rounded-full"></div>
                    </div>
                  ))}
                </div>
              ) : quizzes.length === 0 ? (
                <div className="px-6 py-8 text-center text-muted-foreground">
                  <p>Aucun quiz n'a été créé.</p>
                  <Button variant="link" asChild className="mt-2">
                    <Link href="/enseignant/quizzes/new">Créer votre premier quiz</Link>
                  </Button>
                </div>
              ) : (
                <div className="divide-y">
                  {quizzes.map(quiz => (
                    <div key={quiz.id} className="px-6 py-3 flex items-center justify-between hover:bg-slate-50 transition-colors">
                      <div className="flex flex-col gap-1 min-w-0 pr-4">
                        <Link href={`/enseignant/quizzes/${quiz.id}`} className="font-medium truncate hover:underline">
                          {quiz.title}
                        </Link>
                        <div className="flex items-center gap-2 text-xs text-muted-foreground">
                          {quiz.difficulty === 'FACILE' && <Badge variant="outline" className="bg-green-50 text-green-700 border-green-200 text-[10px] h-5">Facile</Badge>}
                          {quiz.difficulty === 'MOYEN' && <Badge variant="outline" className="bg-yellow-50 text-yellow-700 border-yellow-200 text-[10px] h-5">Moyen</Badge>}
                          {quiz.difficulty === 'DIFFICILE' && <Badge variant="outline" className="bg-red-50 text-red-700 border-red-200 text-[10px] h-5">Difficile</Badge>}
                          <span>{quiz.nbQuestions} questions</span>
                          <span>•</span>
                          <span>{quiz.createdAt ? format(new Date(quiz.createdAt), 'dd MMM yyyy', { locale: fr }) : 'Inconnu'}</span>
                        </div>
                      </div>
                      <div className="shrink-0">
                        {getStatusBadge(quiz.quizStatus)}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>

          {/* Sessions Récentes */}
          <Card>
            <CardHeader className="pb-3 border-b">
              <div className="flex items-center justify-between">
                <div>
                  <CardTitle className="text-lg">Sessions Récentes</CardTitle>
                  <CardDescription>Vos évaluations en cours et planifiées</CardDescription>
                </div>
                <div className="p-2 bg-primary/10 rounded-md text-primary">
                  <PlayCircle size={20} />
                </div>
              </div>
            </CardHeader>
            <CardContent className="pt-4 px-0">
              {isLoadingSessions ? (
                <div className="px-6 py-4 space-y-4">
                  {[1, 2].map(i => (
                    <div key={i} className="flex justify-between items-center animate-pulse">
                      <div className="space-y-2">
                        <div className="h-4 w-40 bg-slate-200 rounded"></div>
                        <div className="h-3 w-24 bg-slate-100 rounded"></div>
                      </div>
                      <div className="h-6 w-20 bg-slate-200 rounded-full"></div>
                    </div>
                  ))}
                </div>
              ) : sessions.length === 0 ? (
                <div className="px-6 py-8 text-center text-muted-foreground">
                  <p>Aucune session organisée.</p>
                  <Button variant="link" asChild className="mt-2">
                    <Link href="/enseignant/sessions/new">Créer une session</Link>
                  </Button>
                </div>
              ) : (
                <div className="divide-y">
                  {sessions.map(session => (
                    <div key={session.id} className="px-6 py-3 flex items-center justify-between hover:bg-slate-50 transition-colors">
                      <div className="flex flex-col gap-1 min-w-0 pr-4">
                        <Link href={`/enseignant/sessions/${session.id}`} className="font-medium truncate hover:underline">
                          {session.quizTitle}
                        </Link>
                        <div className="flex items-center gap-2 text-xs text-muted-foreground">
                          <Users size={12} />
                          <span>{session.attemptCount || 0} participants</span>
                          <span>•</span>
                          <span>{format(new Date(session.startTime), 'dd/MM HH:mm')}</span>
                        </div>
                      </div>
                      <div className="shrink-0">
                        {getSessionBadge(session.sessionStatus)}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>
        </div>
      </div>
    </EnseignantLayout>
  );
}
