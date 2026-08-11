import React from 'react';
import { Link, useLocation } from 'wouter';
import { BookOpen, History, LayoutDashboard, LogOut } from 'lucide-react';
import { useAuth } from '@/components/auth-provider';

export function EtudiantLayout({ children }: { children: React.ReactNode }) {
  const [location] = useLocation();
  const { user, logout } = useAuth();

  const navItems = [
    { href: '/etudiant', label: 'Accueil', icon: LayoutDashboard },
    { href: '/etudiant/historique', label: 'Historique', icon: History },
  ];

  return (
    <div className="min-h-[100dvh] flex flex-col bg-slate-50/50">
      {/* Top Navigation (Desktop) */}
      <header className="hidden md:flex h-16 bg-card border-b px-6 items-center justify-between sticky top-0 z-10 shrink-0 shadow-sm">
        <div className="flex items-center gap-6">
          <Link href="/etudiant" className="flex items-center gap-2 font-semibold text-primary">
            <div className="bg-primary rounded-md p-1.5">
              <BookOpen size={18} className="text-primary-foreground" />
            </div>
            <span className="text-lg tracking-tight">QuizGen</span>
          </Link>
          
          <nav className="flex items-center gap-1 ml-4">
            {navItems.map((item) => {
              const isActive = location === item.href;
              const Icon = item.icon;
              return (
                <Link
                  key={item.href}
                  href={item.href}
                  className={`flex items-center gap-2 px-3 py-2 rounded-md text-sm font-medium transition-colors ${
                    isActive 
                      ? 'bg-primary/10 text-primary' 
                      : 'text-muted-foreground hover:bg-slate-100 hover:text-foreground'
                  }`}
                >
                  <Icon size={16} />
                  {item.label}
                </Link>
              );
            })}
          </nav>
        </div>
        
        <div className="flex items-center gap-4">
          <div className="text-sm">
            <span className="font-medium text-foreground">{user?.firstName} {user?.lastName}</span>
            <span className="text-muted-foreground ml-2 hidden lg:inline">Étudiant</span>
          </div>
          <button 
            onClick={logout}
            className="p-2 text-muted-foreground hover:text-destructive hover:bg-destructive/10 rounded-md transition-colors"
            title="Se déconnecter"
          >
            <LogOut size={18} />
          </button>
        </div>
      </header>

      {/* Mobile Header */}
      <header className="flex md:hidden h-14 bg-card border-b px-4 items-center justify-between sticky top-0 z-10 shrink-0">
        <Link href="/etudiant" className="flex items-center gap-2 font-semibold text-primary">
          <div className="bg-primary rounded-md p-1">
            <BookOpen size={16} className="text-primary-foreground" />
          </div>
          <span className="text-base tracking-tight">QuizGen</span>
        </Link>
        <button onClick={logout} className="p-2 text-muted-foreground">
          <LogOut size={18} />
        </button>
      </header>

      {/* Main Content */}
      <main className="flex-1 w-full max-w-5xl mx-auto p-4 md:p-6 lg:p-8 pb-24 md:pb-8">
        {children}
      </main>

      {/* Bottom Navigation (Mobile) */}
      <nav className="md:hidden fixed bottom-0 left-0 right-0 h-16 bg-card border-t flex items-center justify-around px-2 z-10 pb-safe">
        {navItems.map((item) => {
          const isActive = location === item.href;
          const Icon = item.icon;
          return (
            <Link
              key={item.href}
              href={item.href}
              className={`flex flex-col items-center justify-center w-full h-full space-y-1 ${
                isActive ? 'text-primary' : 'text-muted-foreground'
              }`}
            >
              <Icon size={20} className={isActive ? 'fill-primary/20' : ''} />
              <span className="text-[10px] font-medium">{item.label}</span>
            </Link>
          );
        })}
      </nav>
    </div>
  );
}
