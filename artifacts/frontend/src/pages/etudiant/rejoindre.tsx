import { useState } from 'react';
import { useLocation } from 'wouter';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import * as z from 'zod';
import { useJoinSession } from '@workspace/api-client-react';
import { EtudiantLayout } from '@/components/layout/etudiant-layout';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form';
import { ArrowRight, KeyRound, LogIn } from 'lucide-react';
import { toast } from 'sonner';

const joinSchema = z.object({
  sessionId: z.string().min(5, "L'identifiant de session est requis"),
  accessCode: z.string().optional()
});

export default function Rejoindre() {
  const [, setLocation] = useLocation();
  const joinSession = useJoinSession();

  const form = useForm<z.infer<typeof joinSchema>>({
    resolver: zodResolver(joinSchema),
    defaultValues: {
      sessionId: '',
      accessCode: ''
    }
  });

  const onSubmit = async (values: z.infer<typeof joinSchema>) => {
    try {
      const result = await joinSession.mutateAsync({
        id: values.sessionId.trim(),
        data: {
          accessCode: values.accessCode?.trim() || undefined
        }
      });
      
      toast.success('Session rejointe avec succès !');
      // Redirect to exam mode
      setLocation(`/etudiant/examens/${result.id}`);
      
    } catch (error: any) {
      console.error(error);
      const msg = error.data?.message || error.message || 'Erreur lors de la connexion à la session';
      toast.error(msg);
    }
  };

  return (
    <EtudiantLayout>
      <div className="max-w-md mx-auto pt-8 md:pt-16">
        <Card className="shadow-lg border-primary/10">
          <CardHeader className="text-center space-y-2 pb-8">
            <div className="mx-auto w-12 h-12 bg-primary/10 rounded-full flex items-center justify-center mb-2">
              <LogIn size={24} className="text-primary" />
            </div>
            <CardTitle className="text-2xl">Rejoindre une session</CardTitle>
            <CardDescription>
              Entrez l'identifiant fourni par votre enseignant pour commencer l'évaluation.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <Form {...form}>
              <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-6">
                
                <FormField
                  control={form.control}
                  name="sessionId"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Identifiant de la session</FormLabel>
                      <FormControl>
                        <Input 
                          placeholder="Ex: 550e8400-e29b-41d4-a716-446655440000" 
                          className="font-mono text-sm"
                          autoComplete="off"
                          autoCorrect="off"
                          {...field} 
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />

                <FormField
                  control={form.control}
                  name="accessCode"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel className="flex items-center gap-2">
                        <KeyRound size={16} className="text-muted-foreground" />
                        Code d'accès <span className="text-muted-foreground font-normal text-xs">(Optionnel)</span>
                      </FormLabel>
                      <FormControl>
                        <Input 
                          placeholder="Ex: MIAGE2024" 
                          className="uppercase font-mono tracking-widest"
                          autoComplete="off"
                          {...field} 
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />

                <Button 
                  type="submit" 
                  className="w-full h-12 text-base" 
                  disabled={joinSession.isPending}
                >
                  {joinSession.isPending ? 'Connexion en cours...' : 'Accéder au quiz'}
                  {!joinSession.isPending && <ArrowRight size={18} className="ml-2" />}
                </Button>
              </form>
            </Form>
          </CardContent>
        </Card>
      </div>
    </EtudiantLayout>
  );
}
