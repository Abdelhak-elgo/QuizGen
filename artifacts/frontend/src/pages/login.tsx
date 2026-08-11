import { useEffect } from 'react';
import { useAuth } from '@/components/auth-provider';
import { Redirect } from 'wouter';
import { BookOpen, GraduationCap } from 'lucide-react';
import { keycloak } from '@/lib/keycloak';

export default function Login() {
  const { user, role, isLoading } = useAuth();

  if (isLoading) {
    return (
      <div className="flex h-screen items-center justify-center bg-slate-50">
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent" />
      </div>
    );
  }

  if (user) {
    if (role === 'ENSEIGNANT' || role === 'ADMIN') {
      return <Redirect to="/enseignant" />;
    } else {
      return <Redirect to="/etudiant" />;
    }
  }

  const handleLogin = () => {
    keycloak.login();
  };

  return (
    <div className="min-h-screen bg-slate-50 flex flex-col justify-center py-12 sm:px-6 lg:px-8">
      <div className="sm:mx-auto sm:w-full sm:max-w-md">
        <div className="flex justify-center">
          <div className="bg-primary rounded-xl p-3 shadow-lg">
            <BookOpen size={48} className="text-primary-foreground" />
          </div>
        </div>
        <h2 className="mt-6 text-center text-3xl font-extrabold text-foreground tracking-tight">
          Bienvenue sur QuizGen
        </h2>
        <p className="mt-2 text-center text-sm text-muted-foreground">
          La plateforme de génération de quiz propulsée par l'IA pour l'université
        </p>
      </div>

      <div className="mt-8 sm:mx-auto sm:w-full sm:max-w-md">
        <div className="bg-card py-8 px-4 shadow-xl shadow-slate-200/50 sm:rounded-lg sm:px-10 border border-slate-100">
          <div className="space-y-6">
            <div>
              <div className="flex items-center justify-center space-x-2 text-muted-foreground mb-6">
                <GraduationCap size={20} />
                <span className="text-sm font-medium">Programme MIAGE</span>
              </div>
              <button
                onClick={handleLogin}
                className="w-full flex justify-center py-3 px-4 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-primary hover:bg-primary/90 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-primary transition-all active:scale-[0.98]"
              >
                Se connecter avec Keycloak
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
