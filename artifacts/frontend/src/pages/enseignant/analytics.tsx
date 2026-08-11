import { useRoute } from 'wouter';
import { useGetQuizAnalytics, getGetQuizAnalyticsQueryKey } from '@workspace/api-client-react';
import { EnseignantLayout } from '@/components/layout/enseignant-layout';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { ArrowLeft, Target, Users, TrendingUp, AlertTriangle } from 'lucide-react';
import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid } from 'recharts';

export default function Analytics() {
  const [match, params] = useRoute('/enseignant/analytics/:quizId');
  const quizId = params?.quizId || '';

  const { data: analytics, isLoading } = useGetQuizAnalytics(quizId, {
    query: {
      enabled: !!quizId,
      queryKey: getGetQuizAnalyticsQueryKey(quizId)
    }
  });

  if (!match) return null;

  if (isLoading) {
    return (
      <EnseignantLayout>
        <div className="flex justify-center p-12">Chargement des statistiques...</div>
      </EnseignantLayout>
    );
  }

  if (!analytics) {
    return (
      <EnseignantLayout>
        <div className="text-center p-12 text-muted-foreground">Aucune donnée disponible.</div>
      </EnseignantLayout>
    );
  }

  const chartData = analytics.questionStats.map((stat, i) => ({
    name: `Q${i + 1}`,
    taux: Math.round(stat.correctRate * 100),
    content: stat.content,
    isHard: stat.correctRate < 0.4
  }));

  const hardestQuestions = analytics.questionStats.filter(q => q.correctRate < 0.4);

  return (
    <EnseignantLayout>
      <div className="max-w-5xl mx-auto space-y-6">
        <div className="flex items-center gap-3">
          <Button variant="ghost" size="icon" onClick={() => window.history.back()}>
            <ArrowLeft size={20} />
          </Button>
          <div>
            <h1 className="text-2xl font-bold tracking-tight">Analytique : {analytics.quizTitle}</h1>
            <p className="text-sm text-muted-foreground mt-1">Performances globales des étudiants</p>
          </div>
        </div>

        <div className="grid sm:grid-cols-3 gap-6">
          <Card>
            <CardContent className="pt-6 flex items-center gap-4">
              <div className="bg-primary/10 p-4 rounded-full text-primary">
                <Users size={24} />
              </div>
              <div>
                <p className="text-sm font-medium text-muted-foreground">Total des tentatives</p>
                <h3 className="text-3xl font-bold">{analytics.totalAttempts}</h3>
              </div>
            </CardContent>
          </Card>
          
          <Card>
            <CardContent className="pt-6 flex items-center gap-4">
              <div className="bg-amber-100 p-4 rounded-full text-amber-600">
                <Target size={24} />
              </div>
              <div>
                <p className="text-sm font-medium text-muted-foreground">Score moyen</p>
                <h3 className="text-3xl font-bold">{Math.round(analytics.averageScore * 10) / 10} pts</h3>
              </div>
            </CardContent>
          </Card>

          <Card>
            <CardContent className="pt-6 flex items-center gap-4">
              <div className="bg-green-100 p-4 rounded-full text-green-600">
                <TrendingUp size={24} />
              </div>
              <div>
                <p className="text-sm font-medium text-muted-foreground">Taux de réussite (≥60%)</p>
                <h3 className="text-3xl font-bold">{Math.round(analytics.successRate * 100)}%</h3>
              </div>
            </CardContent>
          </Card>
        </div>

        <div className="grid lg:grid-cols-3 gap-6">
          <Card className="lg:col-span-2">
            <CardHeader>
              <CardTitle>Taux de réussite par question</CardTitle>
              <CardDescription>Pourcentage de bonnes réponses</CardDescription>
            </CardHeader>
            <CardContent>
              <div className="h-[300px] w-full">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={chartData} margin={{ top: 20, right: 30, left: 0, bottom: 5 }}>
                    <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#e2e8f0" />
                    <XAxis dataKey="name" axisLine={false} tickLine={false} />
                    <YAxis axisLine={false} tickLine={false} tickFormatter={(val) => `${val}%`} />
                    <Tooltip 
                      formatter={(value: number) => [`${value}% de réussite`, '']}
                      labelFormatter={(label, payload) => {
                        if (payload && payload[0]) return payload[0].payload.content;
                        return label;
                      }}
                      contentStyle={{ borderRadius: '8px', border: 'none', boxShadow: '0 4px 6px -1px rgba(0,0,0,0.1)' }}
                    />
                    <Bar 
                      dataKey="taux" 
                      fill="hsl(var(--primary))" 
                      radius={[4, 4, 0, 0]}
                      maxBarSize={40}
                    />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2 text-destructive">
                <AlertTriangle size={18} />
                Questions difficiles
              </CardTitle>
              <CardDescription>Taux de réussite inférieur à 40%</CardDescription>
            </CardHeader>
            <CardContent>
              {hardestQuestions.length === 0 ? (
                <div className="text-center py-8 text-muted-foreground text-sm">
                  Toutes les questions ont un bon taux de réussite.
                </div>
              ) : (
                <div className="space-y-4">
                  {hardestQuestions.map((q, i) => (
                    <div key={q.questionId} className="p-3 bg-red-50 border border-red-100 rounded-md">
                      <div className="flex justify-between items-start mb-2">
                        <span className="text-xs font-semibold text-red-800 bg-red-100 px-2 py-0.5 rounded">
                          {Math.round(q.correctRate * 100)}% de réussite
                        </span>
                      </div>
                      <p className="text-sm font-medium text-foreground line-clamp-3">{q.content}</p>
                    </div>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>
        </div>
      </div>
    </EnseignantLayout>
  );
}
