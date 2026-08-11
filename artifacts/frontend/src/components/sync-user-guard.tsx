import React, { useEffect, useRef } from 'react';
import { useAuth } from './auth-provider';
import { useSyncUser } from '@workspace/api-client-react';

export function SyncUserGuard({ children }: { children: React.ReactNode }) {
  const { isLoading, user, role } = useAuth();
  const syncUserMutation = useSyncUser();
  const syncAttempted = useRef(false);

  useEffect(() => {
    if (!isLoading && user && !syncAttempted.current) {
      syncAttempted.current = true;
      syncUserMutation.mutate();
    }
  }, [isLoading, user, syncUserMutation]);

  if (isLoading || (user && !syncAttempted.current) || syncUserMutation.isPending) {
    return (
      <div className="flex h-screen items-center justify-center bg-background">
        <div className="flex flex-col items-center gap-4">
          <div className="h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent" />
          <p className="text-sm text-muted-foreground">Chargement de votre profil...</p>
        </div>
      </div>
    );
  }

  return <>{children}</>;
}
