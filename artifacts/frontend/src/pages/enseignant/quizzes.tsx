import { Link } from 'wouter';
import { Plus } from 'lucide-react';
import { EnseignantLayout } from '@/components/layout/enseignant-layout';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { QuizList } from '@/components/quiz/quiz-list';

export default function QuizzesList() {
  return (
    <EnseignantLayout>
      <div className="space-y-6">
        <div className="flex flex-col justify-between gap-4 sm:flex-row sm:items-center">
          <div>
            <h1 className="text-3xl font-bold tracking-tight">Mes quiz</h1>
            <p className="mt-1 text-muted-foreground">Tous les quiz générés, à relire, publiés ou en échec.</p>
          </div>
          <Button asChild>
            <Link href="/enseignant/quizzes/new">
              <Plus size={16} className="mr-2" />
              Nouveau Quiz
            </Link>
          </Button>
        </div>

        <Card>
          <CardHeader className="pb-0">
            <CardTitle>Bibliothèque de quiz</CardTitle>
            <CardDescription>Filtrez par statut ; les générations en cours se mettent à jour automatiquement.</CardDescription>
          </CardHeader>
          <CardContent className="p-0">
            <QuizList />
          </CardContent>
        </Card>
      </div>
    </EnseignantLayout>
  );
}
