import { Badge } from '@/components/ui/badge';

interface SessionStatusBadgeProps {
  status: string;
}

export function SessionStatusBadge({ status }: SessionStatusBadgeProps) {
  switch (status) {
    case 'SCHEDULED': return <Badge variant="outline" className="bg-blue-50 text-blue-700 border-blue-200">Planifié</Badge>;
    case 'OPEN': return <Badge variant="default" className="bg-green-600 hover:bg-green-700">Ouvert</Badge>;
    case 'CLOSED': return <Badge variant="secondary">Fermé</Badge>;
    case 'CANCELLED': return <Badge variant="destructive">Annulé</Badge>;
    default: return <Badge>{status}</Badge>;
  }
}
