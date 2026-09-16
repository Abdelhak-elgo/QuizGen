import React from 'react';
import { useAuth } from './auth-provider';
import { Redirect } from 'wouter';

export function AdminGuard({ children }: { children: React.ReactNode }) {
  const { role, isLoading } = useAuth();

  if (isLoading) return null;

  if (role === 'ETUDIANT') {
    return <Redirect to="/etudiant" />;
  }

  if (role === 'ENSEIGNANT') {
    return <Redirect to="/enseignant" />;
  }

  if (role !== 'ADMIN') {
    return <Redirect to="/" />;
  }

  return <>{children}</>;
}

export function EnseignantGuard({ children }: { children: React.ReactNode }) {
  const { role, isLoading } = useAuth();

  if (isLoading) return null;

  if (role === 'ETUDIANT') {
    return <Redirect to="/etudiant" />;
  }

  if (role === 'ADMIN') {
    return <Redirect to="/admin" />;
  }

  return <>{children}</>;
}

export function EtudiantGuard({ children }: { children: React.ReactNode }) {
  const { role, isLoading } = useAuth();

  if (isLoading) return null;

  if (role === 'ENSEIGNANT') {
    return <Redirect to="/enseignant" />;
  }

  if (role === 'ADMIN') {
    return <Redirect to="/admin" />;
  }

  return <>{children}</>;
}
