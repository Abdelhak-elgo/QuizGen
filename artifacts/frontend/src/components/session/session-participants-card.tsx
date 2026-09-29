import { useState } from 'react';
import {
  useUpdateSessionParticipants,
  getGetSessionQueryKey,
  getListSessionsQueryKey,
  SessionParticipant,
} from '@workspace/api-client-react';
import { useQueryClient } from '@tanstack/react-query';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Loader2, UserPlus, Users } from 'lucide-react';
import { toast } from 'sonner';
import { StudentPicker } from '@/components/session/student-picker';
import { apiErrorMessage } from '@/lib/api-error';

interface SessionParticipantsCardProps {
  sessionId: string;
  participants: SessionParticipant[];
  editable: boolean;
}

function displayName(p: SessionParticipant): string {
  return [p.firstName, p.lastName].filter(Boolean).join(' ') || p.email;
}

/** Étudiants assignés à la session, modifiables tant qu'elle n'est ni close ni annulée. */
export function SessionParticipantsCard({ sessionId, participants, editable }: SessionParticipantsCardProps) {
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [selection, setSelection] = useState<string[]>([]);

  const updateParticipants = useUpdateSessionParticipants({
    mutation: {
      onSuccess: () => {
        queryClient.invalidateQueries({ queryKey: getGetSessionQueryKey(sessionId) });
        queryClient.invalidateQueries({ queryKey: getListSessionsQueryKey() });
        toast.success('Étudiants assignés mis à jour');
        setOpen(false);
      },
      onError: (e) => toast.error(apiErrorMessage(e, 'Erreur lors de la mise à jour des étudiants')),
    },
  });

  const openEditor = () => {
    setSelection(participants.map(p => p.id));
    setOpen(true);
  };

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <p className="text-sm font-medium text-muted-foreground flex items-center gap-1.5">
          <Users size={14} /> Étudiants assignés ({participants.length})
        </p>
        {editable && (
          <Button size="sm" variant="outline" onClick={openEditor}>
            <UserPlus size={14} className="mr-1.5" />
            {participants.length === 0 ? 'Assigner des étudiants' : 'Modifier'}
          </Button>
        )}
      </div>

      {participants.length === 0 ? (
        <p className="text-sm text-muted-foreground">
          Aucun — les étudiants rejoignent avec l'identifiant de la session.
        </p>
      ) : (
        <div className="flex flex-wrap gap-1.5">
          {participants.map(p => (
            <Badge key={p.id} variant="secondary" title={p.email}>{displayName(p)}</Badge>
          ))}
        </div>
      )}

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle>Étudiants assignés</DialogTitle>
            <DialogDescription>
              Les étudiants sélectionnés verront cette session sur leur tableau de bord et y accéderont
              directement, sans code.
            </DialogDescription>
          </DialogHeader>
          <StudentPicker selectedIds={selection} onChange={setSelection} />
          <DialogFooter>
            <Button variant="outline" onClick={() => setOpen(false)}>Annuler</Button>
            <Button
              onClick={() => updateParticipants.mutate({ id: sessionId, data: { studentIds: selection } })}
              disabled={updateParticipants.isPending}
            >
              {updateParticipants.isPending && <Loader2 size={14} className="animate-spin mr-1.5" />}
              Enregistrer
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
