import { AdminLayout } from '@/components/layout/admin-layout';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { QuizList } from '@/components/quiz/quiz-list';

export default function AdminQuizzes() {
  return (
    <AdminLayout>
      <div className="space-y-6">
        <div>
          <h1 className="text-3xl font-bold tracking-tight text-foreground">Quiz</h1>
          <p className="mt-1 text-muted-foreground">Tous les quiz de la plateforme, tous enseignants confondus.</p>
        </div>
        <Card>
          <CardHeader className="pb-0">
            <CardTitle className="text-xl">Catalogue des quiz</CardTitle>
            <CardDescription>Consultation en lecture seule ; l'édition reste réservée à l'enseignant auteur.</CardDescription>
          </CardHeader>
          <CardContent className="p-0">
            <QuizList adminView />
          </CardContent>
        </Card>
      </div>
    </AdminLayout>
  );
}
