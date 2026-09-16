/**
 * Hooks de téléchargement des exports quiz (SCORM / Moodle).
 *
 * Utilise customFetch de @workspace/api-client-react qui injecte
 * automatiquement le token Keycloak et le baseUrl configuré.
 * Le résultat Blob est déclenché directement comme un téléchargement navigateur.
 */
import { useState } from 'react';
import { customFetch } from '@workspace/api-client-react';

interface ExportState {
  isPending: boolean;
  error: string | null;
}

function triggerDownload(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const a   = document.createElement('a');
  a.href     = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}

/**
 * Déclenche le téléchargement de l'export SCORM 2004 d'un quiz.
 */
export function useScormExport() {
  const [state, setState] = useState<ExportState>({ isPending: false, error: null });

  async function exportScorm(quizId: string, quizTitle: string) {
    setState({ isPending: true, error: null });
    try {
      const blob = await customFetch<Blob>(`/quizzes/${quizId}/export/scorm`, {
        method: 'GET',
        responseType: 'blob',
      });
      const safe = quizTitle.replace(/[^a-z0-9]/gi, '-').toLowerCase();
      triggerDownload(blob, `QuizGen-SCORM-${safe}.zip`);
      setState({ isPending: false, error: null });
    } catch (err) {
      const message = err instanceof Error ? err.message : "Erreur lors de l'export SCORM";
      setState({ isPending: false, error: message });
    }
  }

  return { exportScorm, ...state };
}

/**
 * Déclenche le téléchargement de l'export Moodle XML d'un quiz.
 */
export function useMoodleExport() {
  const [state, setState] = useState<ExportState>({ isPending: false, error: null });

  async function exportMoodle(quizId: string, quizTitle: string) {
    setState({ isPending: true, error: null });
    try {
      const blob = await customFetch<Blob>(`/quizzes/${quizId}/export/moodle`, {
        method: 'GET',
        responseType: 'blob',
      });
      const safe = quizTitle.replace(/[^a-z0-9]/gi, '-').toLowerCase();
      triggerDownload(blob, `QuizGen-Moodle-${safe}.xml`);
      setState({ isPending: false, error: null });
    } catch (err) {
      const message = err instanceof Error ? err.message : "Erreur lors de l'export Moodle";
      setState({ isPending: false, error: message });
    }
  }

  return { exportMoodle, ...state };
}
