import React from 'react';
import { Link, useLocation } from 'wouter';
import { 
  BookOpen, 
  Files, 
  LayoutDashboard, 
  LogOut, 
  PlayCircle,
  Menu
} from 'lucide-react';
import { 
  Sidebar, 
  SidebarContent, 
  SidebarFooter, 
  SidebarHeader, 
  SidebarProvider, 
  SidebarMenu,
  SidebarMenuItem,
  SidebarMenuButton,
  SidebarTrigger,
  SidebarInset
} from '@/components/ui/sidebar';
import { useAuth } from '@/components/auth-provider';

export function EnseignantLayout({ children }: { children: React.ReactNode }) {
  const [location] = useLocation();
  const { user, logout } = useAuth();

  return (
    <SidebarProvider>
      <div className="flex h-screen w-full bg-background overflow-hidden">
        <Sidebar className="border-r border-sidebar-border">
          <SidebarHeader className="p-4 flex items-center justify-between">
            <div className="flex items-center gap-2 font-semibold text-sidebar-primary-foreground">
              <div className="bg-sidebar-primary rounded-md p-1">
                <BookOpen size={20} className="text-white" />
              </div>
              <span className="text-lg tracking-tight">QuizGen</span>
            </div>
          </SidebarHeader>
          <SidebarContent className="px-3 py-2">
            <SidebarMenu>
              <SidebarMenuItem>
                <SidebarMenuButton 
                  asChild 
                  isActive={location === '/enseignant'}
                  tooltip="Tableau de bord"
                >
                  <Link href="/enseignant" className="flex items-center gap-3">
                    <LayoutDashboard size={18} />
                    <span>Tableau de bord</span>
                  </Link>
                </SidebarMenuButton>
              </SidebarMenuItem>
              <SidebarMenuItem>
                <SidebarMenuButton 
                  asChild 
                  isActive={location.startsWith('/enseignant/documents')}
                  tooltip="Documents"
                >
                  <Link href="/enseignant/documents" className="flex items-center gap-3">
                    <Files size={18} />
                    <span>Documents</span>
                  </Link>
                </SidebarMenuButton>
              </SidebarMenuItem>
              <SidebarMenuItem>
                <SidebarMenuButton 
                  asChild 
                  isActive={location.startsWith('/enseignant/sessions')}
                  tooltip="Sessions"
                >
                  <Link href="/enseignant/sessions" className="flex items-center gap-3">
                    <PlayCircle size={18} />
                    <span>Sessions</span>
                  </Link>
                </SidebarMenuButton>
              </SidebarMenuItem>
            </SidebarMenu>
          </SidebarContent>
          <SidebarFooter className="p-4 border-t border-sidebar-border">
            <div className="flex items-center justify-between">
              <div className="flex flex-col">
                <span className="text-sm font-medium text-sidebar-foreground truncate w-32">
                  {user?.firstName} {user?.lastName}
                </span>
                <span className="text-xs text-sidebar-foreground/60 truncate w-32">
                  {user?.email}
                </span>
              </div>
              <button 
                onClick={logout}
                className="p-2 text-sidebar-foreground/60 hover:text-sidebar-foreground hover:bg-sidebar-accent rounded-md transition-colors"
                title="Se déconnecter"
              >
                <LogOut size={18} />
              </button>
            </div>
          </SidebarFooter>
        </Sidebar>

        <SidebarInset className="flex flex-col flex-1 overflow-hidden min-w-0">
          {/* Mobile Header */}
          <header className="flex md:hidden h-14 border-b items-center px-4 justify-between bg-card shrink-0">
            <div className="flex items-center gap-2 font-semibold">
              <div className="bg-primary rounded-md p-1">
                <BookOpen size={16} className="text-primary-foreground" />
              </div>
              <span className="text-base tracking-tight">QuizGen</span>
            </div>
            <SidebarTrigger>
              <Menu size={20} />
            </SidebarTrigger>
          </header>
          
          {/* Main content scrollable area */}
          <main className="flex-1 overflow-y-auto bg-slate-50/50 p-4 md:p-6 lg:p-8">
            <div className="mx-auto max-w-5xl">
              {children}
            </div>
          </main>
        </SidebarInset>
      </div>
    </SidebarProvider>
  );
}
