import { type ReactNode } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ErrorBoundary } from '@/components/error-boundary';
import { Toaster } from '@/components/ui/toaster';
import { TooltipProvider } from '@/components/ui/tooltip';
import { Route, Switch, useLocation, Router as WouterRouter } from 'wouter';

import { AuthProvider } from '@/components/auth-provider';
import { SyncUserGuard } from '@/components/sync-user-guard';
import { EnseignantGuard, EtudiantGuard } from '@/components/route-guards';

// Pages
import NotFound from '@/pages/not-found';
import Login from '@/pages/login';

// Enseignant Pages
import EnseignantDashboard from '@/pages/enseignant/dashboard';
import DocumentLibrary from '@/pages/enseignant/documents';
import QuizNew from '@/pages/enseignant/quiz-new';
import QuizEditor from '@/pages/enseignant/quiz-editor';
import SessionsList from '@/pages/enseignant/sessions';
import SessionNew from '@/pages/enseignant/session-new';
import SessionDetail from '@/pages/enseignant/session-detail';
import Analytics from '@/pages/enseignant/analytics';

// Etudiant Pages
import EtudiantDashboard from '@/pages/etudiant/dashboard';
import Rejoindre from '@/pages/etudiant/rejoindre';
import Historique from '@/pages/etudiant/historique';
import ExamMode from '@/pages/etudiant/exam';
import Resultats from '@/pages/etudiant/resultats';

const queryClient = new QueryClient();

function Router() {
  return (
    <RoutedErrorBoundary>
      <Switch>
        {/* Public / Auth */}
        <Route path="/" component={Login} />
        
        {/* Enseignant Routes */}
        <Route path="/enseignant">
          <EnseignantGuard><SyncUserGuard><EnseignantDashboard /></SyncUserGuard></EnseignantGuard>
        </Route>
        <Route path="/enseignant/documents">
          <EnseignantGuard><SyncUserGuard><DocumentLibrary /></SyncUserGuard></EnseignantGuard>
        </Route>
        <Route path="/enseignant/quizzes/new">
          <EnseignantGuard><SyncUserGuard><QuizNew /></SyncUserGuard></EnseignantGuard>
        </Route>
        <Route path="/enseignant/quizzes/:id">
          <EnseignantGuard><SyncUserGuard><QuizEditor /></SyncUserGuard></EnseignantGuard>
        </Route>
        <Route path="/enseignant/sessions">
          <EnseignantGuard><SyncUserGuard><SessionsList /></SyncUserGuard></EnseignantGuard>
        </Route>
        <Route path="/enseignant/sessions/new">
          <EnseignantGuard><SyncUserGuard><SessionNew /></SyncUserGuard></EnseignantGuard>
        </Route>
        <Route path="/enseignant/sessions/:id">
          <EnseignantGuard><SyncUserGuard><SessionDetail /></SyncUserGuard></EnseignantGuard>
        </Route>
        <Route path="/enseignant/analytics/:quizId">
          <EnseignantGuard><SyncUserGuard><Analytics /></SyncUserGuard></EnseignantGuard>
        </Route>

        {/* Etudiant Routes */}
        <Route path="/etudiant">
          <EtudiantGuard><SyncUserGuard><EtudiantDashboard /></SyncUserGuard></EtudiantGuard>
        </Route>
        <Route path="/etudiant/rejoindre">
          <EtudiantGuard><SyncUserGuard><Rejoindre /></SyncUserGuard></EtudiantGuard>
        </Route>
        <Route path="/etudiant/historique">
          <EtudiantGuard><SyncUserGuard><Historique /></SyncUserGuard></EtudiantGuard>
        </Route>
        <Route path="/etudiant/examens/:attemptId">
          <EtudiantGuard><SyncUserGuard><ExamMode /></SyncUserGuard></EtudiantGuard>
        </Route>
        <Route path="/etudiant/resultats/:attemptId">
          <EtudiantGuard><SyncUserGuard><Resultats /></SyncUserGuard></EtudiantGuard>
        </Route>

        <Route component={NotFound} />
      </Switch>
    </RoutedErrorBoundary>
  );
}

function RoutedErrorBoundary({ children }: { children: ReactNode }) {
  const [location] = useLocation();
  return <ErrorBoundary resetKey={location}>{children}</ErrorBoundary>;
}

function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <TooltipProvider>
        <WouterRouter base={import.meta.env.BASE_URL.replace(/\/$/, '')}>
          <AuthProvider>
            <Router />
          </AuthProvider>
        </WouterRouter>
        <Toaster />
      </TooltipProvider>
    </QueryClientProvider>
  );
}

export default App;
