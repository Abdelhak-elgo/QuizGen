import { AdminLayout } from '@/components/layout/admin-layout';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { SessionList } from '@/components/session/session-list';

export default function AdminSessions() {
  return (
    <AdminLayout>
      <div className="space-y-6">
        <div>
          <h1 className="text-3xl font-bold tracking-tight text-foreground">Sessions</h1>
          <p className="mt-1 text-muted-foreground">Toutes les sessions d'évaluation de la plateforme.</p>
        </div>
        <Card>
          <CardHeader>
            <CardTitle className="text-xl">Sessions d'évaluation</CardTitle>
            <CardDescription>Passées, en cours et planifiées, tous enseignants confondus.</CardDescription>
          </CardHeader>
          <CardContent className="p-0">
            <SessionList adminView />
          </CardContent>
        </Card>
      </div>
    </AdminLayout>
  );
}
