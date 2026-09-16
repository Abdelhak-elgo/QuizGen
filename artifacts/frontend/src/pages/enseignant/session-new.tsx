import { useLocation } from 'wouter';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import * as z from 'zod';
import { 
  useListQuizzes, 
  useCreateSession,
  getListSessionsQueryKey
} from '@workspace/api-client-react';
import { useQueryClient } from '@tanstack/react-query';
import { EnseignantLayout } from '@/components/layout/enseignant-layout';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Form, FormControl, FormDescription, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form';
import { PlayCircle } from 'lucide-react';
import { toast } from 'sonner';

const sessionSchema = z.object({
  quizId: z.string().min(1, "Veuillez sélectionner un quiz"),
  startTime: z.string().min(1, "Date de début requise"),
  endTime: z.string().min(1, "Date de fin requise"),
  accessCode: z.string().max(20).optional()
}).refine(data => {
  return new Date(data.startTime) < new Date(data.endTime);
}, {
  message: "La date de fin doit être après la date de début",
  path: ["endTime"]
});

export default function SessionNew() {
  const [, setLocation] = useLocation();
  const queryClient = useQueryClient();
  
  const { data: quizzesData, isLoading: isLoadingQuizzes } = useListQuizzes({ size: 100 });
  const quizzes = (quizzesData?.content || []).filter(q => q.quizStatus === 'PUBLISHED');

  const createSession = useCreateSession();

  const form = useForm<z.infer<typeof sessionSchema>>({
    resolver: zodResolver(sessionSchema),
    defaultValues: {
      quizId: '',
      startTime: '',
      endTime: '',
      accessCode: ''
    }
  });

  const onSubmit = async (values: z.infer<typeof sessionSchema>) => {
    try {
      const result = await createSession.mutateAsync({ 
        data: {
          quizId: values.quizId,
          startTime: new Date(values.startTime).toISOString(),
          endTime: new Date(values.endTime).toISOString(),
          accessCode: values.accessCode || undefined
        }
      });
      
      queryClient.invalidateQueries({ queryKey: getListSessionsQueryKey() });
      toast.success('Session créée avec succès !');
      setLocation(`/enseignant/sessions/${result.id}`);
      
    } catch (error) {
      console.error(error);
      toast.error('Erreur lors de la création de la session');
    }
  };

  return (
    <EnseignantLayout>
      <div className="max-w-2xl mx-auto space-y-6">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">Nouvelle Session</h1>
          <p className="text-muted-foreground mt-1">Ouvrez un quiz pour que les étudiants puissent y participer.</p>
        </div>

        <Card>
          <CardContent className="pt-6">
            <Form {...form}>
              <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-6">
                
                <FormField
                  control={form.control}
                  name="quizId"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Quiz à évaluer</FormLabel>
                      <Select onValueChange={field.onChange} defaultValue={field.value} disabled={isLoadingQuizzes}>
                        <FormControl>
                          <SelectTrigger>
                            <SelectValue placeholder="Sélectionnez un quiz publié" />
                          </SelectTrigger>
                        </FormControl>
                        <SelectContent>
                          {quizzes.length === 0 ? (
                            <SelectItem value="none" disabled>Aucun quiz publié disponible</SelectItem>
                          ) : (
                            quizzes.map(quiz => (
                              <SelectItem key={quiz.id} value={quiz.id}>
                                {quiz.title} ({quiz.nbQuestions} q.)
                              </SelectItem>
                            ))
                          )}
                        </SelectContent>
                      </Select>
                      <FormDescription>Seuls les quiz publiés peuvent être planifiés.</FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />

                <div className="grid sm:grid-cols-2 gap-6">
                  <FormField
                    control={form.control}
                    name="startTime"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>Début de la session</FormLabel>
                        <FormControl>
                          <Input type="datetime-local" {...field} />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />

                  <FormField
                    control={form.control}
                    name="endTime"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>Fin de la session</FormLabel>
                        <FormControl>
                          <Input type="datetime-local" {...field} />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                </div>

                <FormField
                  control={form.control}
                  name="accessCode"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Code d'accès (optionnel)</FormLabel>
                      <FormControl>
                        <Input placeholder="Ex: MIAGE2024" {...field} />
                      </FormControl>
                      <FormDescription>Limitez l'accès à la session avec un code.</FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />

                <div className="flex justify-end gap-4 pt-4 border-t">
                  <Button type="button" variant="outline" onClick={() => window.history.back()}>
                    Annuler
                  </Button>
                  <Button type="submit" disabled={createSession.isPending || quizzes.length === 0}>
                    {createSession.isPending ? 'Création...' : 'Créer la session'}
                    {!createSession.isPending && <PlayCircle size={16} className="ml-2" />}
                  </Button>
                </div>
              </form>
            </Form>
          </CardContent>
        </Card>
      </div>
    </EnseignantLayout>
  );
}
