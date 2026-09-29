import { AlertTriangle, Loader2, RotateCcw, Download } from 'lucide-react';
import { Button } from '@/components/ui/button';

interface QuizStatusBannerProps {
  status: string;
  isGenerating: boolean;
  questionCount: number;
  isRegenerating: boolean;
  onRegenerate: () => void;
}

/**
 * Explique l'état d'un quiz et ce que l'enseignant peut faire :
 * génération en cours (il peut quitter la page), échec (relancer ou
 * rédiger les questions à la main), ou brouillon à publier.
 */
export function QuizStatusBanner({
  status,
  isGenerating,
  questionCount,
  isRegenerating,
  onRegenerate,
}: QuizStatusBannerProps) {
  if (isGenerating) {
    return (
      <div className="flex items-start gap-3 p-4 bg-blue-50 border border-blue-100 rounded-lg text-sm text-blue-900">
        <Loader2 size={18} className="mt-0.5 shrink-0 animate-spin" />
        <div>
          <p className="font-medium">Génération des questions en cours…</p>
          <p className="text-blue-800/80 mt-0.5">
            Vous pouvez quitter cette page : la génération continue et le quiz sera prêt à votre retour.
            Pour l'abandonner, supprimez le quiz.
          </p>
        </div>
      </div>
    );
  }

  if (status === 'FAILED') {
    return (
      <div className="flex flex-col sm:flex-row sm:items-center gap-3 p-4 bg-red-50 border border-red-100 rounded-lg text-sm text-red-900">
        <AlertTriangle size={18} className="shrink-0" />
        <div className="flex-1">
          <p className="font-medium">La génération automatique a échoué.</p>
          <p className="text-red-800/80 mt-0.5">
            Relancez-la, ou ajoutez vos questions manuellement ci-dessous.
          </p>
        </div>
        <Button size="sm" variant="outline" onClick={onRegenerate} disabled={isRegenerating}
          className="bg-white border-red-200 text-red-700 hover:bg-red-100">
          {isRegenerating
            ? <Loader2 size={14} className="animate-spin mr-1.5" />
            : <RotateCcw size={14} className="mr-1.5" />}
          Relancer la génération
        </Button>
      </div>
    );
  }

  if (status !== 'PUBLISHED' && questionCount > 0) {
    return (
      <div className="flex items-start gap-2 p-3 bg-amber-50 border border-amber-100 rounded-lg text-sm text-amber-800">
        <Download size={15} className="mt-0.5 shrink-0" />
        <span>Publiez ce quiz pour activer les exports SCORM et Moodle XML.</span>
      </div>
    );
  }

  return null;
}
