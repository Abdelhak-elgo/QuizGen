import { useState, type MouseEvent, type ReactNode } from 'react';
import { Loader2 } from 'lucide-react';
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from '@/components/ui/alert-dialog';
import { cn } from '@/lib/utils';

interface ConfirmDialogProps {
  title: string;
  description: ReactNode;
  onConfirm: () => void | Promise<unknown>;
  confirmLabel?: string;
  cancelLabel?: string;
  /** Action irréversible (suppression, annulation…) : bouton rouge. */
  destructive?: boolean;
  /** Bouton qui ouvre la boîte (mode non contrôlé). */
  trigger?: ReactNode;
  /** Mode contrôlé : ouverture pilotée par le parent (ex. soumission déclenchée par code). */
  open?: boolean;
  onOpenChange?: (open: boolean) => void;
}

/**
 * Boîte de confirmation de l'application — remplace window.confirm().
 * Pour une action asynchrone, reste ouverte avec un indicateur pendant
 * l'exécution, se ferme au succès et reste ouverte en cas d'erreur (l'appelant
 * affiche l'erreur via une notification) pour permettre de réessayer.
 */
export function ConfirmDialog({
  title,
  description,
  onConfirm,
  confirmLabel = 'Confirmer',
  cancelLabel = 'Annuler',
  destructive = false,
  trigger,
  open: controlledOpen,
  onOpenChange,
}: ConfirmDialogProps) {
  const [uncontrolledOpen, setUncontrolledOpen] = useState(false);
  const [pending, setPending] = useState(false);
  const open = controlledOpen ?? uncontrolledOpen;

  const setOpen = (value: boolean) => {
    if (pending) return; // pas de fermeture pendant l'action
    setUncontrolledOpen(value);
    onOpenChange?.(value);
  };

  const handleConfirm = async (event: MouseEvent) => {
    event.preventDefault(); // la fermeture est gérée après l'action
    setPending(true);
    try {
      await onConfirm();
      setPending(false);
      setUncontrolledOpen(false);
      onOpenChange?.(false);
    } catch {
      setPending(false);
    }
  };

  return (
    <AlertDialog open={open} onOpenChange={setOpen}>
      {trigger && <AlertDialogTrigger asChild>{trigger}</AlertDialogTrigger>}
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>{title}</AlertDialogTitle>
          <AlertDialogDescription asChild>
            <div>{description}</div>
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel disabled={pending}>{cancelLabel}</AlertDialogCancel>
          <AlertDialogAction
            onClick={handleConfirm}
            disabled={pending}
            className={cn(destructive && 'bg-destructive text-destructive-foreground hover:bg-destructive/90')}
          >
            {pending && <Loader2 size={14} className="animate-spin mr-1.5" />}
            {confirmLabel}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
