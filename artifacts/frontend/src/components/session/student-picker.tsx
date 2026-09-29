import { useMemo, useState } from 'react';
import { useListStudents } from '@workspace/api-client-react';
import { Checkbox } from '@/components/ui/checkbox';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { Loader2, Search, Users } from 'lucide-react';

interface StudentPickerProps {
  selectedIds: string[];
  onChange: (ids: string[]) => void;
}

function displayName(s: { firstName?: string | null; lastName?: string | null; email: string }): string {
  const name = [s.firstName, s.lastName].filter(Boolean).join(' ');
  return name || s.email;
}

/**
 * Sélection des étudiants assignés à une session. Un étudiant n'apparaît
 * qu'après s'être connecté une première fois à QuizGen.
 */
export function StudentPicker({ selectedIds, onChange }: StudentPickerProps) {
  const { data: students, isLoading } = useListStudents();
  const [search, setSearch] = useState('');

  const filtered = useMemo(() => {
    const term = search.trim().toLowerCase();
    const all = students ?? [];
    if (!term) return all;
    return all.filter(s => `${displayName(s)} ${s.email}`.toLowerCase().includes(term));
  }, [students, search]);

  const selected = new Set(selectedIds);
  const toggle = (id: string, checked: boolean) =>
    onChange(checked ? [...selectedIds, id] : selectedIds.filter(x => x !== id));
  const allFilteredSelected = filtered.length > 0 && filtered.every(s => selected.has(s.id));
  const toggleAllFiltered = () => {
    const ids = filtered.map(s => s.id);
    onChange(allFilteredSelected
      ? selectedIds.filter(id => !ids.includes(id))
      : Array.from(new Set([...selectedIds, ...ids])));
  };

  if (isLoading) {
    return (
      <div className="flex items-center gap-2 text-sm text-muted-foreground py-4">
        <Loader2 size={14} className="animate-spin" /> Chargement des étudiants…
      </div>
    );
  }

  if (!students || students.length === 0) {
    return (
      <div className="flex items-center gap-2 text-sm text-muted-foreground bg-slate-50 border border-dashed rounded-md p-4">
        <Users size={16} />
        Aucun étudiant inscrit pour l'instant — ils apparaissent ici après leur première connexion.
      </div>
    );
  }

  return (
    <div className="space-y-2">
      <div className="flex items-center gap-2">
        <div className="relative flex-1">
          <Search size={14} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-muted-foreground" />
          <Input
            value={search}
            onChange={e => setSearch(e.target.value)}
            placeholder="Rechercher un étudiant (nom ou e-mail)"
            className="pl-8"
          />
        </div>
        <Button type="button" variant="outline" size="sm" onClick={toggleAllFiltered} disabled={filtered.length === 0}>
          {allFilteredSelected ? 'Tout désélectionner' : 'Tout sélectionner'}
        </Button>
      </div>

      <div className="max-h-64 overflow-y-auto rounded-md border divide-y">
        {filtered.length === 0 ? (
          <p className="text-sm text-muted-foreground p-3">Aucun étudiant ne correspond à « {search} ».</p>
        ) : filtered.map(s => (
          <label key={s.id} className="flex items-center gap-3 px-3 py-2 cursor-pointer hover:bg-slate-50">
            <Checkbox checked={selected.has(s.id)} onCheckedChange={c => toggle(s.id, c === true)} />
            <div className="min-w-0">
              <p className="text-sm font-medium truncate">{displayName(s)}</p>
              <p className="text-xs text-muted-foreground truncate">{s.email}</p>
            </div>
          </label>
        ))}
      </div>

      <p className="text-xs text-muted-foreground">
        {selectedIds.length === 0
          ? 'Aucun étudiant assigné : la session reste ouverte à tout étudiant ayant son identifiant (et le code éventuel).'
          : `${selectedIds.length} étudiant(s) assigné(s) : la session apparaîtra sur leur tableau de bord et leur sera réservée.`}
      </p>
    </div>
  );
}
