import { useEffect, useState } from 'react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import {
  searchUsers,
  listAssignableRoles,
  getUserRoleIds,
  assignRole,
  removeRole,
  type AssignableRole,
  type UserSearchResult,
} from '../api/accessApi';

function errorMessage(err: unknown, fallback: string): string {
  const maybe = err as { response?: { data?: { message?: string } } };
  return maybe?.response?.data?.message ?? fallback;
}

export function AccessManagementPage() {
  const [roles, setRoles] = useState<AssignableRole[]>([]);
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<UserSearchResult[]>([]);
  const [selected, setSelected] = useState<UserSearchResult | null>(null);
  const [roleIds, setRoleIds] = useState<Set<number>>(new Set());
  const [searching, setSearching] = useState(false);
  const [pending, setPending] = useState<Set<number>>(new Set());
  const [searched, setSearched] = useState(false);

  useEffect(() => {
    listAssignableRoles()
      .then(setRoles)
      .catch(() => toast.error('Erro ao carregar os perfis de acesso.'));
  }, []);

  const doSearch = async (e: React.FormEvent) => {
    e.preventDefault();
    if (query.trim().length < 2) return;
    setSearching(true);
    setSelected(null);
    setSearched(false);
    try {
      setResults(await searchUsers(query.trim()));
      setSearched(true);
    } catch {
      toast.error('Erro ao buscar usuários.');
    } finally {
      setSearching(false);
    }
  };

  const selectUser = async (u: UserSearchResult) => {
    setSearched(false);
    setSelected(u);
    try {
      setRoleIds(new Set(await getUserRoleIds(u.id)));
    } catch {
      toast.error('Erro ao carregar acessos do usuário.');
    }
  };

  const toggle = async (role: AssignableRole) => {
    if (!selected) return;
    const has = roleIds.has(role.id);
    // otimista
    setRoleIds((prev) => {
      const next = new Set(prev);
      if (has) next.delete(role.id);
      else next.add(role.id);
      return next;
    });
    setPending((prev) => {
      const next = new Set(prev);
      next.add(role.id);
      return next;
    });
    try {
      if (has) await removeRole(selected.id, role.id);
      else await assignRole(selected.id, role.id);
      toast.success('Acesso atualizado.');
    } catch (err) {
      // reverte
      setRoleIds((prev) => {
        const next = new Set(prev);
        if (has) next.add(role.id);
        else next.delete(role.id);
        return next;
      });
      toast.error(errorMessage(err, 'Falha ao atualizar acesso.'));
    } finally {
      setPending((prev) => {
        const next = new Set(prev);
        next.delete(role.id);
        return next;
      });
    }
  };

  return (
    <main className="mx-auto max-w-2xl p-4">
      <h1 className="mb-4 flex items-center gap-2 text-2xl font-heading font-semibold">
        <span
          aria-hidden="true"
          className="inline-block h-6 w-1.5 rounded-full"
          style={{ backgroundColor: 'hsl(var(--brand-ink))' }}
        />
        Gerenciar acessos
      </h1>

      <form onSubmit={doSearch} className="mb-4 flex gap-2">
        <label htmlFor="user-search" className="sr-only">
          Buscar usuário por nome ou e-mail
        </label>
        <input
          id="user-search"
          type="search"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Buscar por nome ou e-mail"
          className="min-h-[44px] flex-1 rounded-lg border border-border bg-background px-3 text-sm"
        />
        <Button type="submit" className="min-h-[44px]" disabled={searching}>
          Buscar
        </Button>
      </form>

      {searched && !selected && results.length === 0 && (
        <p className="text-muted-foreground">Nenhum usuário encontrado.</p>
      )}

      {results.length > 0 && !selected && (
        <ul className="mb-4 space-y-2">
          {results.map((u) => (
            <li key={u.id}>
              <button
                type="button"
                onClick={() => selectUser(u)}
                className="flex min-h-[44px] w-full items-center justify-between rounded-lg border border-border px-3 text-left text-sm hover:bg-accent"
              >
                <span className="font-medium">{u.displayName}</span>
                {u.unitLabel && <span className="text-muted-foreground">{u.unitLabel}</span>}
              </button>
            </li>
          ))}
        </ul>
      )}

      {selected && (
        <Card>
          <CardHeader>
            <CardTitle className="text-base">
              {selected.displayName}
              {selected.unitLabel ? ` — ${selected.unitLabel}` : ''}
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            {roles.map((role) => (
              <label key={role.id} className="flex min-h-[44px] items-center gap-3 text-sm">
                <input
                  type="checkbox"
                  className="h-5 w-5"
                  checked={roleIds.has(role.id)}
                  onChange={() => toggle(role)}
                  aria-label={role.label}
                  disabled={pending.has(role.id)}
                />
                <span>{role.label}</span>
              </label>
            ))}
            <Button
              type="button"
              variant="outline"
              className="min-h-[44px]"
              onClick={() => setSelected(null)}
            >
              Voltar à busca
            </Button>
          </CardContent>
        </Card>
      )}
    </main>
  );
}
