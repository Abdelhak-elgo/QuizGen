import { useListUsers } from '@workspace/api-client-react';
import { AdminLayout } from '@/components/layout/admin-layout';
import { 
  Table, 
  TableBody, 
  TableCell, 
  TableHead, 
  TableHeader, 
  TableRow 
} from '@/components/ui/table';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { Pagination, PaginationContent, PaginationItem, PaginationLink, PaginationNext, PaginationPrevious } from '@/components/ui/pagination';
import { Shield, GraduationCap, UserIcon, Activity, CalendarDays, Search, XCircle } from 'lucide-react';
import { useState } from 'react';
import { Input } from '@/components/ui/input';
import { format } from 'date-fns';
import { fr } from 'date-fns/locale';

export default function AdminDashboard() {
  const [page, setPage] = useState(0);
  const [searchTerm, setSearchTerm] = useState('');
  
  const { data: userPage, isLoading, isError } = useListUsers(
    { page, size: 10 }
  );

  const getRoleIcon = (role: string) => {
    switch (role) {
      case 'ADMIN':
        return <Shield className="w-4 h-4 mr-1.5 text-rose-500" />;
      case 'ENSEIGNANT':
        return <UserIcon className="w-4 h-4 mr-1.5 text-indigo-500" />;
      case 'ETUDIANT':
        return <GraduationCap className="w-4 h-4 mr-1.5 text-emerald-500" />;
      default:
        return <UserIcon className="w-4 h-4 mr-1.5" />;
    }
  };

  const getRoleBadgeVariant = (role: string) => {
    switch (role) {
      case 'ADMIN':
        return 'destructive';
      case 'ENSEIGNANT':
        return 'default';
      case 'ETUDIANT':
        return 'secondary';
      default:
        return 'outline';
    }
  };

  const filteredUsers = userPage?.content.filter(user => {
    if (!searchTerm) return true;
    const search = searchTerm.toLowerCase();
    const fullName = `${user.firstName || ''} ${user.lastName || ''}`.toLowerCase();
    return user.email.toLowerCase().includes(search) || fullName.includes(search);
  });

  return (
    <AdminLayout>
      <div className="space-y-6 animate-in fade-in slide-in-from-bottom-4 duration-500">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div>
            <h1 className="text-3xl font-bold tracking-tight text-foreground">Utilisateurs</h1>
            <p className="text-muted-foreground mt-1">Consultez les accès et les rôles de la plateforme QuizGen.</p>
          </div>
        </div>

        <Card className="border-slate-200 shadow-sm">
          <CardHeader className="pb-4">
            <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
              <div>
                <CardTitle className="text-xl">Annuaire</CardTitle>
                <CardDescription>
                  {isLoading ? (
                    <Skeleton className="h-4 w-32 mt-1" />
                  ) : (
                    <span>{userPage?.totalElements || 0} utilisateur(s) enregistré(s)</span>
                  )}
                </CardDescription>
              </div>
              
              <div className="relative max-w-sm w-full">
                <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
                <Input
                  type="text"
                  placeholder="Filtrer la page (Nom, Email)..."
                  aria-label="Filtrer les utilisateurs de la page par nom ou e-mail"
                  className="pl-9 bg-slate-50/50 border-slate-200 focus-visible:ring-primary/20"
                  value={searchTerm}
                  onChange={(e) => setSearchTerm(e.target.value)}
                />
                {searchTerm && (
                  <button 
                    type="button"
                    onClick={() => setSearchTerm('')}
                    className="absolute right-2.5 top-2.5 text-muted-foreground hover:text-foreground transition-colors"
                    aria-label="Effacer le filtre"
                  >
                    <XCircle className="h-4 w-4" />
                  </button>
                )}
              </div>
            </div>
          </CardHeader>
          <CardContent>
            {isError ? (
              <div className="rounded-md border border-destructive/20 bg-destructive/10 p-4 text-center text-sm text-destructive my-4">
                Une erreur est survenue lors du chargement des utilisateurs.
                <div className="mt-1 text-xs opacity-80">Réessayez dans quelques instants.</div>
              </div>
            ) : (
              <div className="rounded-md border border-slate-200 overflow-x-auto">
                <Table>
                  <TableHeader className="bg-slate-50/80">
                    <TableRow className="hover:bg-transparent">
                      <TableHead className="w-[300px]">Utilisateur</TableHead>
                      <TableHead>Rôle</TableHead>
                      <TableHead className="hidden sm:table-cell">Statut</TableHead>
                      <TableHead className="hidden md:table-cell text-right">Inscription</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {isLoading ? (
                      Array.from({ length: 5 }).map((_, i) => (
                        <TableRow key={i}>
                          <TableCell>
                            <div className="flex flex-col space-y-2">
                              <Skeleton className="h-4 w-40" />
                              <Skeleton className="h-3 w-32" />
                            </div>
                          </TableCell>
                          <TableCell><Skeleton className="h-6 w-24 rounded-full" /></TableCell>
                          <TableCell className="hidden sm:table-cell"><Skeleton className="h-6 w-16 rounded-full" /></TableCell>
                          <TableCell className="hidden md:table-cell text-right"><Skeleton className="h-4 w-24 ml-auto" /></TableCell>
                        </TableRow>
                      ))
                    ) : filteredUsers?.length === 0 ? (
                      <TableRow>
                            <TableCell colSpan={4} className="h-24 text-center">
                          <div className="flex flex-col items-center justify-center text-muted-foreground">
                            <Activity className="h-8 w-8 mb-2 opacity-20" />
                            <p>Aucun utilisateur trouvé.</p>
                          </div>
                        </TableCell>
                      </TableRow>
                    ) : (
                      filteredUsers?.map((user) => (
                        <TableRow key={user.id} className="group">
                          <TableCell className="font-medium">
                            <div className="flex flex-col">
                              <span className="text-foreground">
                                {user.firstName || user.lastName 
                                  ? `${user.firstName || ''} ${user.lastName || ''}`.trim() 
                                  : <span className="text-muted-foreground italic">Sans nom</span>}
                              </span>
                              <span className="text-xs text-muted-foreground mt-0.5">{user.email}</span>
                            </div>
                          </TableCell>
                          <TableCell className="hidden sm:table-cell">
                            <Badge variant={getRoleBadgeVariant(user.role) as any} className="font-medium">
                              {getRoleIcon(user.role)}
                              {user.role}
                            </Badge>
                          </TableCell>
                          <TableCell>
                            <Badge variant="outline" className={
                              user.isActive 
                                ? "bg-emerald-50 text-emerald-700 border-emerald-200" 
                                : "bg-slate-100 text-slate-500 border-slate-200"
                            }>
                              {user.isActive ? 'Actif' : 'Inactif'}
                            </Badge>
                          </TableCell>
                          <TableCell className="hidden md:table-cell text-right text-muted-foreground text-sm">
                            <div className="flex items-center justify-end">
                              <CalendarDays className="mr-1.5 h-3.5 w-3.5 opacity-70" />
                              {user.createdAt 
                                ? format(new Date(user.createdAt), "dd MMM yyyy", { locale: fr })
                                : 'Inconnue'}
                            </div>
                          </TableCell>
                        </TableRow>
                      ))
                    )}
                  </TableBody>
                </Table>
              </div>
            )}

            {/* Pagination */}
            {!isLoading && userPage && userPage.totalPages > 1 && (
              <div className="mt-6 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                <div className="text-sm text-muted-foreground">
                  Page <span className="font-medium text-foreground">{page + 1}</span> sur <span className="font-medium text-foreground">{userPage.totalPages}</span>
                </div>
                <Pagination>
                  <PaginationContent>
                    <PaginationItem>
                      <PaginationPrevious 
                        onClick={(e) => {
                          e.preventDefault();
                          if (page > 0) setPage(p => p - 1);
                        }}
                        className={page === 0 ? "pointer-events-none opacity-50" : "cursor-pointer"}
                        href="#"
                      />
                    </PaginationItem>
                    
                    {/* Render page numbers */}
                    {Array.from({ length: userPage.totalPages }).map((_, i) => {
                      // Simple logic to show current, previous, next, first, last
                      if (
                        i === 0 || 
                        i === userPage.totalPages - 1 || 
                        Math.abs(page - i) <= 1
                      ) {
                        return (
                          <PaginationItem key={i}>
                            <PaginationLink 
                              href="#"
                              isActive={page === i}
                              onClick={(e) => {
                                e.preventDefault();
                                setPage(i);
                              }}
                            >
                              {i + 1}
                            </PaginationLink>
                          </PaginationItem>
                        );
                      }
                      
                      // Show ellipsis
                      if (
                        (i === 1 && page > 2) || 
                        (i === userPage.totalPages - 2 && page < userPage.totalPages - 3)
                      ) {
                        return (
                          <PaginationItem key={`ellipsis-${i}`}>
                            <span className="flex h-9 w-9 items-center justify-center">...</span>
                          </PaginationItem>
                        );
                      }
                      
                      return null;
                    })}
                    
                    <PaginationItem>
                      <PaginationNext 
                        onClick={(e) => {
                          e.preventDefault();
                          if (!userPage.last) setPage(p => p + 1);
                        }}
                        className={userPage.last ? "pointer-events-none opacity-50" : "cursor-pointer"}
                        href="#"
                      />
                    </PaginationItem>
                  </PaginationContent>
                </Pagination>
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </AdminLayout>
  );
}
