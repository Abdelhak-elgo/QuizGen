import type { ReactNode } from 'react';
import {
  useGenerateRecommendation,
  getGetAttemptReviewQueryKey,
  getGetSessionResultsQueryKey,
  Recommendation,
} from '@workspace/api-client-react';
import { useQueryClient } from '@tanstack/react-query';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { CheckCircle2, Loader2, RotateCcw, Sparkles, Target, TriangleAlert, Wrench } from 'lucide-react';
import { format } from 'date-fns';
import { fr } from 'date-fns/locale';
import { toast } from 'sonner';
import { apiErrorMessage } from '@/lib/api-error';

interface RecommendationPanelProps {
  attemptId: string;
  sessionId: string;
  submitted: boolean;
  recommendation?: Recommendation | null;
}

function Section({ icon, title, items, tone }: {
  icon: ReactNode; title: string; items: string[]; tone: string;
}) {
  if (items.length === 0) return null;
  return (
    <div className="space-y-1.5">
      <p className={`text-sm font-semibold flex items-center gap-1.5 ${tone}`}>{icon}{title}</p>
      <ul className="space-y-1 pl-6 list-disc text-sm text-foreground/90">
        {items.map((item, i) => <li key={i}>{item}</li>)}
      </ul>
    </div>
  );
}

/**
 * Diagnostic pédagogique IA d'une copie : généré à la demande de l'enseignant
 * (la copie est anonymisée avant l'envoi au LLM), conservé ensuite.
 */
export function RecommendationPanel({ attemptId, sessionId, submitted, recommendation }: RecommendationPanelProps) {
  const queryClient = useQueryClient();
  const generate = useGenerateRecommendation({
    mutation: {
      onSuccess: () => {
        queryClient.invalidateQueries({ queryKey: getGetAttemptReviewQueryKey(attemptId) });
        queryClient.invalidateQueries({ queryKey: getGetSessionResultsQueryKey(sessionId) });
        toast.success('Recommandation générée');
      },
      onError: (e) => toast.error(apiErrorMessage(e, 'Impossible de générer la recommandation')),
    },
  });

  const run = () => generate.mutate({ id: attemptId });

  return (
    <Card className="border-violet-200">
      <CardHeader className="pb-3">
        <div className="flex flex-col sm:flex-row sm:items-start justify-between gap-3">
          <div>
            <CardTitle className="text-lg flex items-center gap-2">
              <Sparkles size={18} className="text-violet-600" />
              Recommandation pédagogique
            </CardTitle>
            <CardDescription>
              Analyse IA des réponses : lacunes, notions à retravailler et actions à proposer.
            </CardDescription>
          </div>
          {submitted && (
            <Button size="sm" variant={recommendation ? 'outline' : 'default'} onClick={run} disabled={generate.isPending}>
              {generate.isPending
                ? <Loader2 size={14} className="animate-spin mr-1.5" />
                : recommendation ? <RotateCcw size={14} className="mr-1.5" /> : <Sparkles size={14} className="mr-1.5" />}
              {recommendation ? 'Régénérer' : 'Analyser la copie'}
            </Button>
          )}
        </div>
      </CardHeader>
      <CardContent>
        {!submitted ? (
          <p className="text-sm text-muted-foreground">Disponible une fois la copie rendue.</p>
        ) : generate.isPending ? (
          <p className="text-sm text-muted-foreground flex items-center gap-2">
            <Loader2 size={14} className="animate-spin" /> Analyse de la copie en cours…
          </p>
        ) : !recommendation ? (
          <p className="text-sm text-muted-foreground">
            Aucune analyse pour l'instant. Le nom de l'étudiant n'est pas transmis à l'IA.
          </p>
        ) : (
          <div className="space-y-4">
            <p className="text-sm leading-relaxed bg-violet-50 border border-violet-100 rounded-md p-3">
              {recommendation.summary}
            </p>
            <div className="grid md:grid-cols-2 gap-4">
              <Section icon={<CheckCircle2 size={14} />} title="Points forts" items={recommendation.strengths} tone="text-green-700" />
              <Section icon={<TriangleAlert size={14} />} title="Lacunes observées" items={recommendation.weaknesses} tone="text-red-700" />
              <Section icon={<Target size={14} />} title="À retravailler en priorité" items={recommendation.focusAreas} tone="text-amber-700" />
              <Section icon={<Wrench size={14} />} title="Actions conseillées" items={recommendation.actions} tone="text-blue-700" />
            </div>
            <p className="text-xs text-muted-foreground">
              Générée{recommendation.generatedAt
                ? ` le ${format(new Date(recommendation.generatedAt), "d MMM yyyy 'à' HH:mm", { locale: fr })}`
                : ''}{recommendation.model ? ` par ${recommendation.model}` : ''} — à valider par l'enseignant.
            </p>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
