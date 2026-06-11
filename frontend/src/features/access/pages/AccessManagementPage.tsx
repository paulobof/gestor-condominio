import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { useAuth } from '@/features/auth/useAuth';
import {
  listUsers,
  listAssignableRoles,
  getUserRoleIds,
  assignRole,
  removeRole,
  getCreatableRoles,
  createUser,
  deleteUser,
  lookupUnit,
  type AssignableRole,
  type UserAccessRow,
} from '../api/accessApi';

function errorMessage(err: unknown, fallback: string): string {
  const maybe = err as { response?: { data?: { message?: string } } };
  return maybe?.response?.data?.message ?? fallback;
}

const PAGE_SIZE = 20;

export function AccessManagementPage() {
  const { user } = useAuth();
  const canManage = user?.authorities.includes('USER_MANAGE') ?? false;

  const [roles, setRoles] = useState<AssignableRole[]>([]);
  const [query, setQuery] = useState('');
  const [rows, setRows] = useState<UserAccessRow[]>([]);
  const [page, setPage] = useState(0);
  const [last, setLast] = useState(true);
  const [loading, setLoading] = useState(true);
  const [selected, setSelected] = useState<UserAccessRow | null>(null);
  const [roleIds, setRoleIds] = useState<Set<number>>(new Set());
  const [pending, setPending] = useState<Set<number>>(new Set());
  const [confirmDelete, setConfirmDelete] = useState<string | null>(null);
  const [adding, setAdding] = useState(false);

  useEffect(() => {
    listAssignableRoles()
      .then(setRoles)
      .catch(() => toast.error('Erro ao carregar os perfis de acesso.'));
  }, []);

  const load = useCallback(async (q: string, p: number, append: boolean) => {
    setLoading(true);
    try {
      const res = await listUsers(q, p, PAGE_SIZE);
      setRows((prev) => (append ? [...prev, ...res.content] : res.content));
      setPage(res.number);
      setLast(res.last);
    } catch {
      toast.error('Erro ao carregar usuários.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    const t = setTimeout(() => {
      void load(query, 0, false);
    }, 300);
    return () => clearTimeout(t);
  }, [query, load]);

  const selectUser = async (u: UserAccessRow) => {
    setSelected(u);
    try {
      setRoleIds(new Set(await getUserRoleIds(u.id)));
    } catch {
      toast.error('Erro ao carregar acessos do usuário.');
    }
  };

  const back = () => {
    if (selected) {
      const updated = roles
        .filter((r) => roleIds.has(r.id))
        .map((r) => ({ id: r.id, label: r.label }));
      setRows((prev) =>
        prev.map((row) => (row.id === selected.id ? { ...row, roles: updated } : row))
      );
    }
    setSelected(null);
  };

  const toggle = async (role: AssignableRole) => {
    if (!selected) return;
    const has = roleIds.has(role.id);
    setRoleIds((prev) => {
      const next = new Set(prev);
      if (has) next.delete(role.id);
      else next.add(role.id);
      return next;
    });
    setPending((prev) => new Set(prev).add(role.id));
    try {
      if (has) await removeRole(selected.id, role.id);
      else await assignRole(selected.id, role.id);
      toast.success('Acesso atualizado.');
    } catch (err) {
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

  const onDelete = async (id: string) => {
    try {
      await deleteUser(id);
      setRows((prev) => prev.filter((r) => r.id !== id));
      toast.success('Usuário excluído.');
    } catch (err) {
      toast.error(errorMessage(err, 'Falha ao excluir usuário.'));
    } finally {
      setConfirmDelete(null);
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

      {!selected && !adding && (
        <>
          <div className="mb-4 flex gap-2">
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
            {canManage && (
              <Button type="button" className="min-h-[44px]" onClick={() => setAdding(true)}>
                Adicionar usuário
              </Button>
            )}
          </div>

          {!loading && rows.length === 0 && (
            <p className="text-muted-foreground">Nenhum usuário encontrado.</p>
          )}

          <ul className="space-y-2">
            {rows.map((u) => (
              <li
                key={u.id}
                className="flex items-center gap-2 rounded-lg border border-border px-2 py-1"
              >
                <button
                  type="button"
                  onClick={() => selectUser(u)}
                  className="flex min-h-[44px] flex-1 flex-col items-start gap-1 px-1 py-1 text-left text-sm hover:bg-accent"
                >
                  <span className="flex w-full flex-wrap items-center gap-x-2">
                    <span className="font-medium">{u.displayName}</span>
                    {u.unitLabel && <span className="text-muted-foreground">{u.unitLabel}</span>}
                    {u.phone && <span className="text-muted-foreground">{u.phone}</span>}
                  </span>
                  {u.roles.length > 0 && (
                    <span className="flex flex-wrap gap-1">
                      {u.roles.map((r) => (
                        <span
                          key={r.id}
                          className="rounded-full bg-accent px-2 py-0.5 text-xs text-accent-foreground"
                        >
                          {r.label}
                        </span>
                      ))}
                    </span>
                  )}
                </button>
                {canManage &&
                  (confirmDelete === u.id ? (
                    <span className="flex shrink-0 gap-1">
                      <Button
                        type="button"
                        variant="destructive"
                        className="min-h-[44px]"
                        onClick={() => void onDelete(u.id)}
                      >
                        Confirmar
                      </Button>
                      <Button
                        type="button"
                        variant="outline"
                        className="min-h-[44px]"
                        onClick={() => setConfirmDelete(null)}
                      >
                        Cancelar
                      </Button>
                    </span>
                  ) : (
                    <Button
                      type="button"
                      variant="outline"
                      className="min-h-[44px] shrink-0"
                      aria-label={`Excluir ${u.displayName}`}
                      onClick={() => setConfirmDelete(u.id)}
                    >
                      Excluir
                    </Button>
                  ))}
              </li>
            ))}
          </ul>

          {!last && (
            <Button
              type="button"
              variant="outline"
              className="mt-4 min-h-[44px] w-full"
              disabled={loading}
              onClick={() => void load(query, page + 1, true)}
            >
              Carregar mais
            </Button>
          )}
        </>
      )}

      {adding && (
        <AddUserForm
          onDone={() => {
            setAdding(false);
            void load('', 0, false);
            setQuery('');
          }}
          onCancel={() => setAdding(false)}
        />
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
            <Button type="button" variant="outline" className="min-h-[44px]" onClick={back}>
              Voltar à busca
            </Button>
          </CardContent>
        </Card>
      )}
    </main>
  );
}

function AddUserForm({ onDone, onCancel }: { onDone: () => void; onCancel: () => void }) {
  const [creatable, setCreatable] = useState<AssignableRole[]>([]);
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [unitCode, setUnitCode] = useState('');
  const [picked, setPicked] = useState<Set<number>>(new Set());
  const [saving, setSaving] = useState(false);
  const [createdPassword, setCreatedPassword] = useState<string | null>(null);

  useEffect(() => {
    getCreatableRoles()
      .then((rs) => {
        setCreatable(rs);
        const resident = rs.find((r) => r.name === 'RESIDENT');
        if (resident) setPicked(new Set([resident.id]));
      })
      .catch(() => toast.error('Erro ao carregar os perfis.'));
  }, []);

  const toggle = (id: number) =>
    setPicked((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (picked.size === 0) {
      toast.error('Selecione ao menos um perfil.');
      return;
    }
    setSaving(true);
    try {
      let unitId: string | null = null;
      if (unitCode.trim()) {
        try {
          unitId = (await lookupUnit(unitCode.trim())).id;
        } catch {
          toast.error('Unidade não encontrada.');
          setSaving(false);
          return;
        }
      }
      const out = await createUser({
        fullName: fullName.trim(),
        email: email.trim(),
        phone: phone.trim(),
        unitId,
        roleIds: [...picked],
      });
      setCreatedPassword(out.password);
      toast.success('Usuário criado.');
    } catch (err) {
      toast.error(errorMessage(err, 'Falha ao criar usuário.'));
    } finally {
      setSaving(false);
    }
  };

  if (createdPassword) {
    return (
      <Card>
        <CardHeader>
          <CardTitle className="text-base">Usuário criado</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3 text-sm">
          <p>Copie a senha provisória e repasse ao usuário. Ela não será mostrada de novo:</p>
          <code className="block rounded-md bg-accent px-3 py-2 font-mono text-base">
            {createdPassword}
          </code>
          <Button type="button" className="min-h-[44px]" onClick={onDone}>
            Concluir
          </Button>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">Adicionar usuário</CardTitle>
      </CardHeader>
      <CardContent>
        <form className="space-y-3" onSubmit={(e) => void submit(e)}>
          <div className="space-y-1">
            <label htmlFor="nu-name" className="text-sm font-medium">
              Nome
            </label>
            <input
              id="nu-name"
              required
              value={fullName}
              onChange={(e) => setFullName(e.target.value)}
              className="min-h-[44px] w-full rounded-lg border border-border bg-background px-3 text-sm"
            />
          </div>
          <div className="space-y-1">
            <label htmlFor="nu-email" className="text-sm font-medium">
              E-mail
            </label>
            <input
              id="nu-email"
              type="email"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className="min-h-[44px] w-full rounded-lg border border-border bg-background px-3 text-sm"
            />
          </div>
          <div className="space-y-1">
            <label htmlFor="nu-phone" className="text-sm font-medium">
              Telefone
            </label>
            <input
              id="nu-phone"
              required
              value={phone}
              onChange={(e) => setPhone(e.target.value)}
              placeholder="+5511999999999"
              className="min-h-[44px] w-full rounded-lg border border-border bg-background px-3 text-sm"
            />
          </div>
          <div className="space-y-1">
            <label htmlFor="nu-unit" className="text-sm font-medium">
              Unidade (código, opcional)
            </label>
            <input
              id="nu-unit"
              value={unitCode}
              onChange={(e) => setUnitCode(e.target.value)}
              className="min-h-[44px] w-full rounded-lg border border-border bg-background px-3 text-sm"
            />
          </div>
          <fieldset className="space-y-2">
            <legend className="text-sm font-medium">Perfis</legend>
            {creatable.map((r) => (
              <label key={r.id} className="flex min-h-[44px] items-center gap-3 text-sm">
                <input
                  type="checkbox"
                  className="h-5 w-5"
                  checked={picked.has(r.id)}
                  onChange={() => toggle(r.id)}
                  aria-label={r.label}
                />
                <span>{r.label}</span>
              </label>
            ))}
          </fieldset>
          <div className="flex gap-2">
            <Button type="submit" className="min-h-[44px]" disabled={saving}>
              Criar
            </Button>
            <Button type="button" variant="outline" className="min-h-[44px]" onClick={onCancel}>
              Cancelar
            </Button>
          </div>
        </form>
      </CardContent>
    </Card>
  );
}
