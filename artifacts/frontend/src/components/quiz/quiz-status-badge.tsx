import { Loader2 } from 'lucide-react';
import { Badge } from '@/components/ui/badge';

interface QuizStatusBadgeProps {
  status: string;
  isGenerating?: boolean;
}

/** Libellés des statuts de quiz, partagés par les filtres et les badges. */
export const QUIZ_STATUS_LABELS: Record<string, string> = {
  DRAFT: 'Brouillon',
  REVIEWING: 'En révision',
  PUBLISHED: 'Publié',
  FAILED: 'Échec',
  ARCHIVED: 'Archivé',
};

export function QuizStatusBadge({ status, isGenerating = false }: QuizStatusBadgeProps) {
  if (isGenerating) {
    return (
      <Badge variant="outline" className="bg-blue-50 text-blue-700 border-blue-200">
        <Loader2 size={11} className="animate-spin mr-1" /> Génération…
      </Badge>
    );
  }
  switch (status) {
    case 'DRAFT': return <Badge variant="secondary">{QUIZ_STATUS_LABELS.DRAFT}</Badge>;
    case 'FAILED': return <Badge variant="outline" className="bg-red-50 text-red-700 border-red-200">{QUIZ_STATUS_LABELS.FAILED}</Badge>;
    case 'REVIEWING': return <Badge variant="outline" className="bg-yellow-50 text-yellow-700 border-yellow-200">{QUIZ_STATUS_LABELS.REVIEWING}</Badge>;
    case 'PUBLISHED': return <Badge variant="default" className="bg-green-600 hover:bg-green-700">{QUIZ_STATUS_LABELS.PUBLISHED}</Badge>;
    case 'ARCHIVED': return <Badge variant="secondary" className="opacity-50">{QUIZ_STATUS_LABELS.ARCHIVED}</Badge>;
    default: return <Badge>{status}</Badge>;
  }
}
