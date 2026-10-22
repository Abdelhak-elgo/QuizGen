import { Link } from 'wouter';
import { EnseignantLayout } from '@/components/layout/enseignant-layout';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Plus } from 'lucide-react';
import { SessionList } from '@/components/session/session-list';

export default function SessionsList() {
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
            <SessionList
              emptyAction={
                <Button variant="link" asChild className="mt-2">
                  <Link href="/enseignant/sessions/new">Planifier votre première session</Link>
                </Button>
              }
            />
          </CardContent>
        </Card>
      </div>
    </EnseignantLayout>
  );
}
