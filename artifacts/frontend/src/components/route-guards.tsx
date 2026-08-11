import React from 'react';
import { useAuth } from './auth-provider';
import { Redirect } from 'wouter';

export function EnseignantGuard({ children }: { children: React.ReactNode }) {
  const { role, isLoading } = useAuth();
  
  if (isLoading) return null;
  
  if (role === 'ETUDIANT') {
    return <Redirect to="/etudiant" />;
  }
  
  return <>{children}</>;
}

export function EtudiantGuard({ children }: { children: React.ReactNode }) {
  const { role, isLoading } = useAuth();
  
  if (isLoading) return null;
  
  if (role === 'ENSEIGNANT' || role === 'ADMIN') {
    return <Redirect to="/enseignant" />;
  }
  
  return <>{children}</>;
}
