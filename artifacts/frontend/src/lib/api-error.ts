import { ApiError } from '@workspace/api-client-react';

/**
 * Message lisible d'une erreur d'API : le backend renvoie un ProblemDetail
 * ({ title, detail }) ou, pour les ResponseStatusException, un { message }.
 */
export function apiErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof ApiError) {
    const data = error.data as { detail?: string; message?: string } | null;
    if (data?.detail) return data.detail;
    if (data?.message) return data.message;
  }
  return fallback;
}
