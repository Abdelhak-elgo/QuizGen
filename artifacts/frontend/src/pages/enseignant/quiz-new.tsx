import { useEffect, useState } from 'react';
import { useLocation, useSearch } from 'wouter';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import * as z from 'zod';
import { 
  useListDocuments, 
  useCreateQuiz, 
  useGetQuiz, 
  getListQuizzesQueryKey,
  getGetQuizQueryKey,
  QuizInputDifficulty,
  QuizInputQuestionTypesItem
} from '@workspace/api-client-react';
import { useQueryClient } from '@tanstack/react-query';
import { EnseignantLayout } from '@/components/layout/enseignant-layout';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Form, FormControl, FormDescription, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form';
import { Slider } from '@/components/ui/slider';
import { Checkbox } from '@/components/ui/checkbox';
import { BrainCircuit, Loader2 } from 'lucide-react';
import { toast } from 'sonner';

const quizSchema = z.object({
  documentId: z.string().min(1, "Veuillez sélectionner un document"),
  title: z.string().min(3, "Le titre doit faire au moins 3 caractères").max(255),
  description: z.string().optional(),
  difficulty: z.enum(['FACILE', 'MOYEN', 'DIFFICILE']),
  nbQuestions: z.number().min(3).max(25),
  questionTypes: z.array(z.enum(['QCM', 'OUVERTE', 'EXERCICE'])).min(1, "Sélectionnez au moins un type de question")
});

export default function QuizNew() {
  const [, setLocation] = useLocation();
  const searchString = useSearch();
  const searchParams = new URLSearchParams(searchString);
  const initialDocId = searchParams.get('documentId') || '';

  const queryClient = useQueryClient();
  const { data: documentsData, isLoading: isLoadingDocs } = useListDocuments({ size: 100 });
  const documents = (documentsData?.content || []).filter(doc => doc.isProcessed);

  const createQuiz = useCreateQuiz();
  
  const [generatingQuizId, setGeneratingQuizId] = useState<string | null>(null);

  // Poll for completion if we have an ID
  const { data: pollingQuiz } = useGetQuiz(generatingQuizId || '', {
    query: {
      enabled: !!generatingQuizId,
      queryKey: getGetQuizQueryKey(generatingQuizId || ''),
      refetchInterval: (query) => {
        const status = query.state.data?.quizStatus;
        if (status && status !== 'DRAFT') return false;
        return 3000;
      },
    }
  });

  useEffect(() => {
    if (pollingQuiz && pollingQuiz.quizStatus !== 'DRAFT') {
      toast.success('Génération du quiz terminée !');
      setLocation(`/enseignant/quizzes/${pollingQuiz.id}`);
    }
  }, [pollingQuiz, setLocation]);

  const form = useForm<z.infer<typeof quizSchema>>({
    resolver: zodResolver(quizSchema),
    defaultValues: {
      documentId: initialDocId,
      title: '',
      description: '',
      difficulty: 'MOYEN',
      nbQuestions: 5,
      questionTypes: ['QCM']
    }
  });

  const onSubmit = async (values: z.infer<typeof quizSchema>) => {
    try {
      const result = await createQuiz.mutateAsync({ 
        data: {
          documentId: values.documentId,
          title: values.title,
          description: values.description,
          difficulty: values.difficulty as QuizInputDifficulty,
          nbQuestions: values.nbQuestions,
          questionTypes: values.questionTypes as QuizInputQuestionTypesItem[]
        }
      });
      
      queryClient.invalidateQueries({ queryKey: getListQuizzesQueryKey() });
      setGeneratingQuizId(result.id);
      
    } catch (error) {
      console.error(error);
      toast.error('Erreur lors de la création du quiz');
    }
  };

  if (generatingQuizId) {
    return (
      <EnseignantLayout>
        <div className="flex flex-col items-center justify-center min-h-[60vh] text-center space-y-6">
          <div className="relative">
            <div className="absolute inset-0 bg-primary/20 blur-xl rounded-full animate-pulse"></div>
            <div className="bg-card p-4 rounded-full relative shadow-sm border border-primary/20">
              <BrainCircuit size={48} className="text-primary animate-bounce" />
            </div>
          </div>
          <div className="space-y-2 max-w-md">
            <h2 className="text-2xl font-bold">Génération en cours...</h2>
            <p className="text-muted-foreground">
              Mistral AI analyse votre document et génère des questions sur mesure. 
              Cela peut prendre jusqu'à une minute selon la taille du document.
            </p>
          </div>
          <div className="flex items-center gap-2 text-sm text-primary font-medium">
            <Loader2 size={16} className="animate-spin" />
            Veuillez patienter
          </div>
        </div>
      </EnseignantLayout>
    );
  }

  return (
    <EnseignantLayout>
      <div className="max-w-2xl mx-auto space-y-6">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">Nouveau Quiz</h1>
          <p className="text-muted-foreground mt-1">Générez un quiz automatiquement à partir de vos documents.</p>
        </div>

        <Card>
          <CardContent className="pt-6">
            <Form {...form}>
              <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-6">
                
                <FormField
                  control={form.control}
                  name="documentId"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Document source</FormLabel>
                      <Select onValueChange={field.onChange} defaultValue={field.value} disabled={isLoadingDocs}>
                        <FormControl>
                          <SelectTrigger>
                            <SelectValue placeholder="Sélectionnez un document PDF" />
                          </SelectTrigger>
                        </FormControl>
                        <SelectContent>
                          {documents.map(doc => (
                            <SelectItem key={doc.id} value={doc.id}>
                              {doc.originalFilename}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                      <FormDescription>Seuls les documents traités sont disponibles.</FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />

                <FormField
                  control={form.control}
                  name="title"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Titre du quiz</FormLabel>
                      <FormControl>
                        <Input placeholder="Ex: QCM sur les bases de données relationnelles" {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />

                <FormField
                  control={form.control}
                  name="description"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Description (optionnel)</FormLabel>
                      <FormControl>
                        <Textarea placeholder="Instructions ou objectifs du quiz..." className="resize-none" {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />

                <div className="grid sm:grid-cols-2 gap-6">
                  <FormField
                    control={form.control}
                    name="difficulty"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>Niveau de difficulté</FormLabel>
                        <Select onValueChange={field.onChange} defaultValue={field.value}>
                          <FormControl>
                            <SelectTrigger>
                              <SelectValue placeholder="Sélectionnez un niveau" />
                            </SelectTrigger>
                          </FormControl>
                          <SelectContent>
                            <SelectItem value="FACILE">Facile</SelectItem>
                            <SelectItem value="MOYEN">Moyen</SelectItem>
                            <SelectItem value="DIFFICILE">Difficile</SelectItem>
                          </SelectContent>
                        </Select>
                        <FormMessage />
                      </FormItem>
                    )}
                  />

                  <FormField
                    control={form.control}
                    name="nbQuestions"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>Nombre de questions: {field.value}</FormLabel>
                        <FormControl>
                          <div className="pt-2">
                            <Slider
                              min={3}
                              max={25}
                              step={1}
                              value={[field.value]}
                              onValueChange={(val) => field.onChange(val[0])}
                            />
                          </div>
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                </div>

                <FormField
                  control={form.control}
                  name="questionTypes"
                  render={() => (
                    <FormItem>
                      <div className="mb-4">
                        <FormLabel className="text-base">Types de questions</FormLabel>
                        <FormDescription>Sélectionnez au moins un type à inclure dans ce quiz.</FormDescription>
                      </div>
                      <div className="grid sm:grid-cols-3 gap-4">
                        {[
                          { id: "QCM", label: "QCM", desc: "Choix multiples" },
                          { id: "OUVERTE", label: "Ouverte", desc: "Réponse courte" },
                          { id: "EXERCICE", label: "Exercice", desc: "Application pratique" }
                        ].map((item) => (
                          <FormField
                            key={item.id}
                            control={form.control}
                            name="questionTypes"
                            render={({ field }) => {
                              return (
                                <FormItem
                                  key={item.id}
                                  className="flex flex-row items-start space-x-3 space-y-0 rounded-md border p-4 shadow-sm bg-card"
                                >
                                  <FormControl>
                                    <Checkbox
                                      checked={field.value?.includes(item.id as any)}
                                      onCheckedChange={(checked) => {
                                        return checked
                                          ? field.onChange([...field.value, item.id])
                                          : field.onChange(
                                              field.value?.filter(
                                                (value) => value !== item.id
                                              )
                                            )
                                      }}
                                    />
                                  </FormControl>
                                  <div className="space-y-1 leading-none">
                                    <FormLabel className="font-medium cursor-pointer">
                                      {item.label}
                                    </FormLabel>
                                    <p className="text-xs text-muted-foreground">
                                      {item.desc}
                                    </p>
                                  </div>
                                </FormItem>
                              )
                            }}
                          />
                        ))}
                      </div>
                      <FormMessage />
                    </FormItem>
                  )}
                />

                <div className="flex justify-end gap-4 pt-4 border-t">
                  <Button type="button" variant="outline" onClick={() => window.history.back()}>
                    Annuler
                  </Button>
                  <Button type="submit" disabled={createQuiz.isPending}>
                    {createQuiz.isPending ? 'Initialisation...' : 'Générer le quiz avec l\'IA'}
                    {!createQuiz.isPending && <BrainCircuit size={16} className="ml-2" />}
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
