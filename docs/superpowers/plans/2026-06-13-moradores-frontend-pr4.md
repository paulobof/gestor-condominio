## ⚠️ Premissas e perguntas em aberto

> Resolver com o Paulo antes de executar; os defaults abaixo são o que o plano implementa se nada mudar.

1. **Rota e label.** Default deste plano: rota `/minha-unidade/moradores`, label do menu **"Moradores"**, título da página **"Moradores da minha unidade"**. Confirmar wording. (Alternativas descartadas: `/moradores` colide com o conceito amplo de "morador" do app; `/admin/moradores` sugere área de admin, mas a tela é do morador master.)
2. **Reusar componentes do `AccessManagementPage` ou duplicar?** Default: **duplicar** os helpers locais (`Field`, `errorMessage`, lista `GENDERS`) dentro de `MyUnitMembersPage.tsx`. Motivo: `AccessManagementPage` não exporta esses helpers (são `function` privadas no módulo), e extrair para um shared agora aumenta o diff e mexe numa tela já mergeada/estável. Se o Paulo preferir DRY, abrir um PR de refactor separado extraindo `Field`/`GENDERS`/`errorMessage` para `@/components/form` e `@/lib/genders` — **fora do escopo deste PR**.
3. **Mostrar o status do morador na lista?** O backend devolve `status` em `UnitMemberResponse`. Default: a lista escopada do master só traz `ACTIVE` (o service filtra `status<>DISABLED`), então **não** exibimos um badge de status para não poluir. Tipamos `status` no client mesmo assim. Confirmar se há outros status visíveis (ex.: pendente).
4. **Busca / paginação?** O endpoint `GET /api/units/me/members` devolve um **`List` simples** (sem `Page`, sem `q`), porque uma unidade tem poucos moradores. Default: **sem busca e sem paginação** — render direto da lista. Se uma unidade puder ter muitos moradores, isso vira outra história (mudaria o contrato backend).
5. **`whatsappOptIn` no cadastro.** `CreateUnitMemberRequest` tem `boolean whatsappOptIn`. Default: checkbox "Avisar por WhatsApp" **marcado** por padrão no form de criar (o master cadastra alguém da família e normalmente quer notificações). Confirmar default.
6. **`greetingName` obrigatório no criar, opcional no editar.** Reflete os DTOs: `CreateUnitMemberRequest.greetingName` é `@NotBlank`; `UpdateUnitMemberRequest.greetingName` é só `@Size`. O plano espelha isso (required no Add, opcional no Edit).
7. **Sem campo de unidade.** Diferente do `AccessManagementPage`, esta tela **não** tem campo "Unidade" (o escopo é fixo na unidade do master — backend ignora/não aceita `unitId`). Por isso **não** reusamos `lookupUnit`/`resolveUnitId`.
8. **Gating do card no home + item na Sidebar:** authority **`RESIDENT_MANAGE`** (aparece para o morador master e para admins que a tenham). Confirmar que é exatamente essa string (bate com `@PreAuthorize("hasAuthority('RESIDENT_MANAGE')")` do `UnitMemberController`).

---

# Moradores da minha unidade (frontend, PR4) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Entregar a tela do **morador master** para gerir os moradores da sua própria unidade (listar, adicionar com senha provisória mostrada 1x, editar dados, excluir), consumindo os endpoints já mergeados `GET/POST/PUT/DELETE /api/units/me/members` gated por `RESIDENT_MANAGE`, com rota, item de menu na Sidebar e card no home, tudo gated por `RESIDENT_MANAGE`.

**Architecture:** Espelha o padrão já estabelecido em `features/access` (tela `/admin/acessos`): um client fino `unitMembersApi.ts` (axios via `@/lib/api`) com tipos derivados dos DTOs do backend, e uma página `MyUnitMembersPage.tsx` com estados de UI mutuamente exclusivos (lista | adicionar | editar) usando `useState`, toasts `sonner`, e gating por `useAuth().user.authorities`. A lista é um `List` simples (sem busca/paginação) porque o endpoint não pagina. Navegação (rota, Sidebar, card do App) reusa o mecanismo `requires: <authority>` já existente.

**Tech Stack:** React 18 + TypeScript + Vite, Vitest + @testing-library/react + @testing-library/user-event, axios (`@/lib/api`), sonner (toasts), shadcn/ui (`Button`, `Card`), Tailwind, react-router-dom. npm. Mobile-first, touch ≥44px, WCAG AA, UI em português.

---

## File Structure

**Frontend (criar):**
- `frontend/src/features/units/api/unitMembersApi.ts` — client dos endpoints `/units/me/members` (listMembers, createMember, updateMember, deleteMember) + tipos espelhando os DTOs.
- `frontend/src/features/units/api/unitMembersApi.test.ts` — testes de contrato (paths, payloads, retorno da senha).
- `frontend/src/features/units/pages/MyUnitMembersPage.tsx` — a tela (lista + adicionar + editar + excluir), com helpers locais `Field`, `errorMessage`, `GENDERS` (duplicados de `AccessManagementPage`, ver Premissa 2).
- `frontend/src/features/units/pages/MyUnitMembersPage.test.tsx` — testes da página (lista, fluxo de criar com senha 1x, editar, excluir, gating).

**Frontend (modificar):**
- `frontend/src/router.tsx` — rota `/minha-unidade/moradores` → `<MyUnitMembersPage />` dentro da casca autenticada.
- `frontend/src/components/layout/Sidebar.tsx` — item "Moradores" no `ITEMS`, gated `RESIDENT_MANAGE`.
- `frontend/src/components/layout/Sidebar.test.tsx` — testes de gating do novo item.
- `frontend/src/App.tsx` — card "Moradores" no `NAV`, gated `RESIDENT_MANAGE`.
- `frontend/src/App.test.tsx` — testes de gating do novo card.

**Comandos (a partir de `frontend/`):**
- Rodar um arquivo de teste: `npm run test -- src/features/units/api/unitMembersApi.test.ts`
- Rodar um teste por nome: `npm run test -- -t "createMember"`
- Type-check: `npm run typecheck` (ou `npx tsc --noEmit` se o script não existir)

> **Contrato do backend (referência, NÃO criar):**
> `backend/.../feature/user/UnitMemberController.java` — base `/api/units/me/members`; `@/lib/api` já prefixa `/api`, então no client os paths são `/units/me/members`.
> - `GET /units/me/members` → `List<UnitMemberResponse>` = `{ id, fullName, greetingName, email, phone, status }`.
> - `POST /units/me/members` body `CreateUnitMemberRequest` = `{ fullName, greetingName, email, phone, gender, birthDate, whatsappOptIn }` (sem password, sem unitId) → 201 `CreatedUnitMemberResponse` = `{ id, fullName, password }`.
> - `PUT /units/me/members/{id}` body `UpdateUnitMemberRequest` = `{ fullName, greetingName, phone, email, gender, birthDate }` → 204.
> - `DELETE /units/me/members/{id}` → 204.
> `gender` é o enum `Gender` = `MALE | FEMALE | OTHER | NOT_INFORMED` (string no JSON; `null`/omitido permitido).

---

## Task 1: Client `unitMembersApi.ts` + testes de contrato

**Files:**
- Create: `frontend/src/features/units/api/unitMembersApi.ts`
- Test: `frontend/src/features/units/api/unitMembersApi.test.ts`

- [ ] **Step 1: Escrever o teste de contrato que falha**

Espelha `frontend/src/features/access/api/accessApi.test.ts`: mocka `@/lib/api` e verifica path + payload de cada função.

```ts
import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('@/lib/api', () => ({
  api: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}));

import { api } from '@/lib/api';
import {
  listMembers,
  createMember,
  updateMember,
  deleteMember,
  type CreateMemberPayload,
  type UpdateMemberPayload,
} from './unitMembersApi';

const get = vi.mocked(api.get);
const post = vi.mocked(api.post);
const put = vi.mocked(api.put);
const del = vi.mocked(api.delete);

beforeEach(() => {
  vi.clearAllMocks();
});

describe('unitMembersApi — contrato com o backend', () => {
  it('listMembers faz GET em /units/me/members e devolve a lista', async () => {
    get.mockResolvedValue({
      data: [
        {
          id: 'm1',
          fullName: 'Bia Souza',
          greetingName: 'Bia',
          email: 'bia@x.com',
          phone: '+5511988887777',
          status: 'ACTIVE',
        },
      ],
    });
    const out = await listMembers();
    expect(get).toHaveBeenCalledWith('/units/me/members');
    expect(out).toHaveLength(1);
    expect(out[0].fullName).toBe('Bia Souza');
  });

  it('createMember faz POST em /units/me/members com o payload e devolve a senha 1x', async () => {
    post.mockResolvedValue({ data: { id: 'm9', fullName: 'Bia Souza', password: 'X1y!aaaa' } });
    const payload: CreateMemberPayload = {
      fullName: 'Bia Souza',
      greetingName: 'Bia',
      email: 'bia@x.com',
      phone: '+5511988887777',
      gender: 'FEMALE',
      birthDate: '1990-01-02',
      whatsappOptIn: true,
    };
    const out = await createMember(payload);
    expect(post).toHaveBeenCalledWith('/units/me/members', payload);
    expect(out.password).toBe('X1y!aaaa');
  });

  it('updateMember faz PUT em /units/me/members/:id com o payload', async () => {
    put.mockResolvedValue({ data: undefined });
    const payload: UpdateMemberPayload = {
      fullName: 'Bia Souza',
      greetingName: 'Bia',
      phone: '+5511988887777',
      email: 'bia@x.com',
      gender: 'FEMALE',
      birthDate: '1990-01-02',
    };
    await updateMember('m1', payload);
    expect(put).toHaveBeenCalledWith('/units/me/members/m1', payload);
  });

  it('deleteMember faz DELETE no path do morador', async () => {
    del.mockResolvedValue({ data: undefined });
    await deleteMember('m9');
    expect(del).toHaveBeenCalledWith('/units/me/members/m9');
  });
});
```

- [ ] **Step 2: Rodar o teste e ver falhar**

Run (em `frontend/`): `npm run test -- src/features/units/api/unitMembersApi.test.ts`
Expected: FAIL — `Failed to resolve import './unitMembersApi'` (o módulo ainda não existe).

- [ ] **Step 3: Implementar o client mínimo**

Cria `frontend/src/features/units/api/unitMembersApi.ts`. Os tipos espelham os DTOs do backend exatamente. `gender` aceita `string` (enum serializado) ou `null`; `birthDate` é `'YYYY-MM-DD'` ou `null`.

```ts
import { api } from '@/lib/api';

/** Espelha UnitMemberResponse (id, fullName, greetingName, email, phone, status). */
export interface UnitMember {
  id: string;
  fullName: string;
  greetingName: string;
  email: string;
  phone: string;
  status: string;
}

/** Espelha CreateUnitMemberRequest (sem password, sem unitId). */
export interface CreateMemberPayload {
  fullName: string;
  greetingName: string;
  email: string;
  phone: string;
  gender: string | null;
  birthDate: string | null;
  whatsappOptIn: boolean;
}

/** Espelha CreatedUnitMemberResponse: senha provisória mostrada uma única vez. */
export interface CreatedMember {
  id: string;
  fullName: string;
  password: string;
}

/** Espelha UpdateUnitMemberRequest (sem password, sem unitId; greetingName opcional). */
export interface UpdateMemberPayload {
  fullName: string;
  greetingName: string;
  phone: string;
  email: string;
  gender: string | null;
  birthDate: string | null;
}

export async function listMembers() {
  const r = await api.get('/units/me/members');
  return r.data as UnitMember[];
}

export async function createMember(payload: CreateMemberPayload) {
  const r = await api.post('/units/me/members', payload);
  return r.data as CreatedMember;
}

export async function updateMember(id: string, payload: UpdateMemberPayload) {
  await api.put(`/units/me/members/${id}`, payload);
}

export async function deleteMember(id: string) {
  await api.delete(`/units/me/members/${id}`);
}
```

- [ ] **Step 4: Rodar o teste e ver passar**

Run: `npm run test -- src/features/units/api/unitMembersApi.test.ts`
Expected: PASS (4 testes).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/features/units/api/unitMembersApi.ts frontend/src/features/units/api/unitMembersApi.test.ts
git commit -m "feat(units): client unitMembersApi para /units/me/members"
```

---

## Task 2: Página `MyUnitMembersPage.tsx` — lista + estados base

**Files:**
- Create: `frontend/src/features/units/pages/MyUnitMembersPage.tsx`
- Test: `frontend/src/features/units/pages/MyUnitMembersPage.test.tsx`

- [ ] **Step 1: Escrever o teste da lista (falha)**

Mocka o client `../api/unitMembersApi`, `sonner` e `useAuth` — mesmo padrão de `AccessManagementPage.test.tsx`.

```tsx
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('../api/unitMembersApi', () => ({
  listMembers: vi.fn(),
  createMember: vi.fn(),
  updateMember: vi.fn(),
  deleteMember: vi.fn(),
}));
vi.mock('sonner', () => ({ toast: { error: vi.fn(), success: vi.fn() } }));
vi.mock('@/features/auth/useAuth', () => ({ useAuth: vi.fn() }));

import { MyUnitMembersPage } from './MyUnitMembersPage';
import {
  listMembers,
  createMember,
  updateMember,
  deleteMember,
  type UnitMember,
} from '../api/unitMembersApi';
import { useAuth } from '@/features/auth/useAuth';
import { toast } from 'sonner';

const listMock = vi.mocked(listMembers);
const createMock = vi.mocked(createMember);
const updateMock = vi.mocked(updateMember);
const deleteMock = vi.mocked(deleteMember);
const authMock = vi.mocked(useAuth);

const MEMBER: UnitMember = {
  id: 'm1',
  fullName: 'Bia Souza',
  greetingName: 'Bia',
  email: 'bia@x.com',
  phone: '+5511988887777',
  status: 'ACTIVE',
};

function setAuth(authorities: string[]) {
  authMock.mockReturnValue({ user: { authorities } } as unknown as ReturnType<typeof useAuth>);
}

beforeEach(() => {
  vi.clearAllMocks();
  setAuth(['RESIDENT_MANAGE']);
  listMock.mockResolvedValue([MEMBER]);
  createMock.mockResolvedValue({ id: 'm9', fullName: 'Novo Morador', password: 'X1y!aaaa' });
  updateMock.mockResolvedValue(undefined);
  deleteMock.mockResolvedValue(undefined);
});

describe('MyUnitMembersPage — lista', () => {
  it('mostra o título e a lista de moradores com nome, e-mail e telefone', async () => {
    render(<MyUnitMembersPage />);
    expect(
      screen.getByRole('heading', { name: /moradores da minha unidade/i })
    ).toBeInTheDocument();
    expect(await screen.findByText('Bia Souza')).toBeInTheDocument();
    expect(screen.getByText('bia@x.com')).toBeInTheDocument();
    expect(screen.getByText('+5511988887777')).toBeInTheDocument();
  });

  it('mostra estado vazio quando não há moradores', async () => {
    listMock.mockResolvedValue([]);
    render(<MyUnitMembersPage />);
    expect(await screen.findByText(/nenhum morador/i)).toBeInTheDocument();
  });

  it('avisa quando a listagem falha', async () => {
    listMock.mockRejectedValue(new Error('boom'));
    render(<MyUnitMembersPage />);
    await waitFor(() => expect(toast.error).toHaveBeenCalled());
  });
});
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `npm run test -- src/features/units/pages/MyUnitMembersPage.test.tsx`
Expected: FAIL — `Failed to resolve import './MyUnitMembersPage'`.

- [ ] **Step 3: Implementar a página (lista + estados + helpers locais)**

Cria `frontend/src/features/units/pages/MyUnitMembersPage.tsx` completo. Inclui já os sub-forms `AddMemberForm` e `EditMemberForm` (exercitados nas Tasks 3–5) e os helpers locais `Field`, `errorMessage`, `GENDERS`. Tudo num arquivo, espelhando `AccessManagementPage.tsx`.

```tsx
import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { useAuth } from '@/features/auth/useAuth';
import {
  listMembers,
  createMember,
  updateMember,
  deleteMember,
  type UnitMember,
} from '../api/unitMembersApi';

function errorMessage(err: unknown, fallback: string): string {
  const maybe = err as { response?: { data?: { message?: string } } };
  return maybe?.response?.data?.message ?? fallback;
}

const GENDERS = [
  { value: '', label: '—' },
  { value: 'MALE', label: 'Masculino' },
  { value: 'FEMALE', label: 'Feminino' },
  { value: 'OTHER', label: 'Outro' },
  { value: 'NOT_INFORMED', label: 'Não informado' },
];

export function MyUnitMembersPage() {
  const { user } = useAuth();
  const canManage = user?.authorities.includes('RESIDENT_MANAGE') ?? false;

  const [rows, setRows] = useState<UnitMember[]>([]);
  const [loading, setLoading] = useState(true);
  const [editing, setEditing] = useState<UnitMember | null>(null);
  const [adding, setAdding] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setRows(await listMembers());
    } catch {
      toast.error('Erro ao carregar os moradores.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const onDelete = async (id: string) => {
    try {
      await deleteMember(id);
      setRows((prev) => prev.filter((r) => r.id !== id));
      toast.success('Morador removido.');
    } catch (err) {
      toast.error(errorMessage(err, 'Falha ao remover morador.'));
    } finally {
      setConfirmDelete(null);
    }
  };

  const showList = !editing && !adding;

  return (
    <main className="mx-auto max-w-2xl p-4">
      <h1 className="mb-4 flex items-center gap-2 text-2xl font-heading font-semibold">
        <span
          aria-hidden="true"
          className="inline-block h-6 w-1.5 rounded-full"
          style={{ backgroundColor: 'hsl(var(--brand-ink))' }}
        />
        Moradores da minha unidade
      </h1>

      {showList && (
        <>
          {canManage && (
            <div className="mb-4 flex justify-end">
              <Button type="button" className="min-h-[44px]" onClick={() => setAdding(true)}>
                Adicionar morador
              </Button>
            </div>
          )}

          {!loading && rows.length === 0 && (
            <p className="text-muted-foreground">Nenhum morador cadastrado nesta unidade.</p>
          )}

          <ul className="space-y-2">
            {rows.map((m) => (
              <li
                key={m.id}
                className="flex flex-wrap items-center gap-2 rounded-lg border border-border px-3 py-2"
              >
                <span className="flex min-w-0 flex-1 flex-col gap-1 text-sm">
                  <span className="font-medium">{m.fullName}</span>
                  <span className="flex flex-wrap items-center gap-x-2 text-muted-foreground">
                    <span>{m.email}</span>
                    <span>{m.phone}</span>
                  </span>
                </span>
                {canManage && (
                  <span className="flex shrink-0 flex-wrap gap-1">
                    <Button
                      type="button"
                      variant="outline"
                      className="min-h-[44px]"
                      onClick={() => setEditing(m)}
                    >
                      Dados
                    </Button>
                    {confirmDelete === m.id ? (
                      <>
                        <Button
                          type="button"
                          variant="destructive"
                          className="min-h-[44px]"
                          onClick={() => void onDelete(m.id)}
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
                      </>
                    ) : (
                      <Button
                        type="button"
                        variant="outline"
                        className="min-h-[44px]"
                        aria-label={`Excluir ${m.fullName}`}
                        onClick={() => setConfirmDelete(m.id)}
                      >
                        Excluir
                      </Button>
                    )}
                  </span>
                )}
              </li>
            ))}
          </ul>
        </>
      )}

      {adding && (
        <AddMemberForm
          onDone={() => {
            setAdding(false);
            void load();
          }}
          onCancel={() => setAdding(false)}
        />
      )}

      {editing && (
        <EditMemberForm
          member={editing}
          onDone={() => {
            setEditing(null);
            void load();
          }}
          onCancel={() => setEditing(null)}
        />
      )}
    </main>
  );
}

function AddMemberForm({ onDone, onCancel }: { onDone: () => void; onCancel: () => void }) {
  const [fullName, setFullName] = useState('');
  const [greetingName, setGreetingName] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [gender, setGender] = useState('');
  const [birthDate, setBirthDate] = useState('');
  const [whatsappOptIn, setWhatsappOptIn] = useState(true);
  const [saving, setSaving] = useState(false);
  const [createdPassword, setCreatedPassword] = useState<string | null>(null);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setSaving(true);
    try {
      const out = await createMember({
        fullName: fullName.trim(),
        greetingName: greetingName.trim(),
        email: email.trim(),
        phone: phone.trim(),
        gender: gender || null,
        birthDate: birthDate || null,
        whatsappOptIn,
      });
      setCreatedPassword(out.password);
      toast.success('Morador cadastrado.');
    } catch (err) {
      toast.error(errorMessage(err, 'Falha ao cadastrar morador.'));
    } finally {
      setSaving(false);
    }
  };

  if (createdPassword) {
    return (
      <Card>
        <CardHeader>
          <CardTitle className="text-base">Morador cadastrado</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3 text-sm">
          <p>Copie a senha provisória e repasse ao morador. Ela não será mostrada de novo:</p>
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
        <CardTitle className="text-base">Adicionar morador</CardTitle>
      </CardHeader>
      <CardContent>
        <form className="space-y-3" onSubmit={(e) => void submit(e)}>
          <Field id="nm-name" label="Nome" value={fullName} onChange={setFullName} required />
          <Field
            id="nm-greeting"
            label="Como chamar"
            value={greetingName}
            onChange={setGreetingName}
            required
          />
          <Field
            id="nm-email"
            label="E-mail"
            type="email"
            value={email}
            onChange={setEmail}
            required
          />
          <Field
            id="nm-phone"
            label="Telefone"
            value={phone}
            onChange={setPhone}
            required
            placeholder="+5511999999999"
          />
          <div className="space-y-1">
            <label htmlFor="nm-gender" className="text-sm font-medium">
              Gênero
            </label>
            <select
              id="nm-gender"
              value={gender}
              onChange={(e) => setGender(e.target.value)}
              className="min-h-[44px] w-full rounded-lg border border-border bg-background px-3 text-sm"
            >
              {GENDERS.map((g) => (
                <option key={g.value} value={g.value}>
                  {g.label}
                </option>
              ))}
            </select>
          </div>
          <div className="space-y-1">
            <label htmlFor="nm-birth" className="text-sm font-medium">
              Data de nascimento
            </label>
            <input
              id="nm-birth"
              type="date"
              value={birthDate}
              onChange={(e) => setBirthDate(e.target.value)}
              className="min-h-[44px] w-full rounded-lg border border-border bg-background px-3 text-sm"
            />
          </div>
          <label className="flex min-h-[44px] items-center gap-3 text-sm">
            <input
              type="checkbox"
              className="h-5 w-5"
              checked={whatsappOptIn}
              onChange={(e) => setWhatsappOptIn(e.target.checked)}
            />
            <span>Avisar por WhatsApp</span>
          </label>
          <div className="flex gap-2">
            <Button type="submit" className="min-h-[44px]" disabled={saving}>
              Cadastrar
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

function EditMemberForm({
  member,
  onDone,
  onCancel,
}: {
  member: UnitMember;
  onDone: () => void;
  onCancel: () => void;
}) {
  const [fullName, setFullName] = useState(member.fullName);
  const [greetingName, setGreetingName] = useState(member.greetingName ?? '');
  const [email, setEmail] = useState(member.email ?? '');
  const [phone, setPhone] = useState(member.phone ?? '');
  const [gender, setGender] = useState('');
  const [birthDate, setBirthDate] = useState('');
  const [saving, setSaving] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setSaving(true);
    try {
      await updateMember(member.id, {
        fullName: fullName.trim(),
        greetingName: greetingName.trim(),
        phone: phone.trim(),
        email: email.trim(),
        gender: gender || null,
        birthDate: birthDate || null,
      });
      toast.success('Dados atualizados.');
      onDone();
    } catch (err) {
      toast.error(errorMessage(err, 'Falha ao atualizar os dados.'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">Editar dados</CardTitle>
      </CardHeader>
      <CardContent>
        <form className="space-y-3" onSubmit={(e) => void submit(e)}>
          <Field id="em-name" label="Nome" value={fullName} onChange={setFullName} required />
          <Field
            id="em-greeting"
            label="Como chamar (opcional)"
            value={greetingName}
            onChange={setGreetingName}
          />
          <Field
            id="em-email"
            label="E-mail"
            type="email"
            value={email}
            onChange={setEmail}
            required
          />
          <Field
            id="em-phone"
            label="Telefone"
            value={phone}
            onChange={setPhone}
            required
            placeholder="+5511999999999"
          />
          <div className="space-y-1">
            <label htmlFor="em-gender" className="text-sm font-medium">
              Gênero
            </label>
            <select
              id="em-gender"
              value={gender}
              onChange={(e) => setGender(e.target.value)}
              className="min-h-[44px] w-full rounded-lg border border-border bg-background px-3 text-sm"
            >
              {GENDERS.map((g) => (
                <option key={g.value} value={g.value}>
                  {g.label}
                </option>
              ))}
            </select>
          </div>
          <div className="space-y-1">
            <label htmlFor="em-birth" className="text-sm font-medium">
              Data de nascimento
            </label>
            <input
              id="em-birth"
              type="date"
              value={birthDate}
              onChange={(e) => setBirthDate(e.target.value)}
              className="min-h-[44px] w-full rounded-lg border border-border bg-background px-3 text-sm"
            />
          </div>
          <div className="flex gap-2">
            <Button type="submit" className="min-h-[44px]" disabled={saving}>
              Salvar
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

function Field({
  id,
  label,
  value,
  onChange,
  type = 'text',
  required = false,
  placeholder,
}: {
  id: string;
  label: string;
  value: string;
  onChange: (v: string) => void;
  type?: string;
  required?: boolean;
  placeholder?: string;
}) {
  return (
    <div className="space-y-1">
      <label htmlFor={id} className="text-sm font-medium">
        {label}
      </label>
      <input
        id={id}
        type={type}
        required={required}
        placeholder={placeholder}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="min-h-[44px] w-full rounded-lg border border-border bg-background px-3 text-sm"
      />
    </div>
  );
}
```

> Nota: `EditMemberForm` preenche o form a partir do próprio `UnitMember` da lista (não há `GET /{id}` para um membro). Como `UnitMemberResponse` **não** traz `gender`/`birthDate`, esses dois campos começam vazios na edição — o backend pode então sobrescrevê-los com `null` ao salvar. **Verificar no `UnitMemberService`/`UserProvisioning`**: se o `PUT` aplica `gender`/`birthDate` como `null` quando vêm vazios, isso apagaria dados existentes. Se for o caso, abrir questão (Premissa) e considerar (a) expor `gender`/`birthDate` no `UnitMemberResponse` para pré-preencher, ou (b) o client enviar esses campos só quando preenchidos. Os testes deste plano cobrem o caminho em que o master os preenche.

- [ ] **Step 4: Rodar e ver passar (lista)**

Run: `npm run test -- src/features/units/pages/MyUnitMembersPage.test.tsx`
Expected: PASS (3 testes da lista).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/features/units/pages/MyUnitMembersPage.tsx frontend/src/features/units/pages/MyUnitMembersPage.test.tsx
git commit -m "feat(units): tela de moradores do master (lista + estado vazio)"
```

---

## Task 3: Fluxo "Adicionar morador" com senha provisória 1x

**Files:**
- Modify: `frontend/src/features/units/pages/MyUnitMembersPage.test.tsx`
- (implementação já presente da Task 2 — esta task só adiciona testes do fluxo)

- [ ] **Step 1: Adicionar os testes do fluxo de criar (confirmam o comportamento da impl da Task 2)**

Acrescentar este `describe` ao final do arquivo de teste.

```tsx
describe('MyUnitMembersPage — adicionar', () => {
  it('cadastra e mostra a senha provisória uma única vez', async () => {
    const user = userEvent.setup();
    render(<MyUnitMembersPage />);
    await screen.findByText('Bia Souza');
    await user.click(screen.getByRole('button', { name: /adicionar morador/i }));

    await user.type(screen.getByLabelText('Nome'), 'Novo Morador');
    await user.type(screen.getByLabelText('Como chamar'), 'Novo');
    await user.type(screen.getByLabelText('E-mail'), 'novo@x.com');
    await user.type(screen.getByLabelText('Telefone'), '+5511977776666');
    await user.click(screen.getByRole('button', { name: /^cadastrar$/i }));

    await waitFor(() => expect(createMock).toHaveBeenCalled());
    const payload = createMock.mock.calls[0][0];
    expect(payload.fullName).toBe('Novo Morador');
    expect(payload.greetingName).toBe('Novo');
    expect(payload.email).toBe('novo@x.com');
    expect(payload.whatsappOptIn).toBe(true);

    expect(await screen.findByText('X1y!aaaa')).toBeInTheDocument();
    expect(screen.getByText(/não será mostrada de novo/i)).toBeInTheDocument();
  });

  it('"Concluir" volta para a lista e recarrega', async () => {
    const user = userEvent.setup();
    render(<MyUnitMembersPage />);
    await screen.findByText('Bia Souza');
    await user.click(screen.getByRole('button', { name: /adicionar morador/i }));
    await user.type(screen.getByLabelText('Nome'), 'Novo Morador');
    await user.type(screen.getByLabelText('Como chamar'), 'Novo');
    await user.type(screen.getByLabelText('E-mail'), 'novo@x.com');
    await user.type(screen.getByLabelText('Telefone'), '+5511977776666');
    await user.click(screen.getByRole('button', { name: /^cadastrar$/i }));

    await screen.findByText('X1y!aaaa');
    await user.click(screen.getByRole('button', { name: /concluir/i }));

    // volta à lista (botão "Adicionar morador" reaparece) e chama listMembers de novo (load inicial + reload)
    await screen.findByRole('button', { name: /adicionar morador/i });
    await waitFor(() => expect(listMock).toHaveBeenCalledTimes(2));
  });

  it('avisa quando o cadastro falha', async () => {
    createMock.mockRejectedValue({ response: { data: { message: 'E-mail já usado.' } } });
    const user = userEvent.setup();
    render(<MyUnitMembersPage />);
    await screen.findByText('Bia Souza');
    await user.click(screen.getByRole('button', { name: /adicionar morador/i }));
    await user.type(screen.getByLabelText('Nome'), 'Novo Morador');
    await user.type(screen.getByLabelText('Como chamar'), 'Novo');
    await user.type(screen.getByLabelText('E-mail'), 'novo@x.com');
    await user.type(screen.getByLabelText('Telefone'), '+5511977776666');
    await user.click(screen.getByRole('button', { name: /^cadastrar$/i }));
    await waitFor(() => expect(toast.error).toHaveBeenCalledWith('E-mail já usado.'));
  });
});
```

- [ ] **Step 2: Rodar e ver passar**

Run: `npm run test -- src/features/units/pages/MyUnitMembersPage.test.tsx`
Expected: PASS (lista + adicionar). Se algum teste falhar, ajustar a implementação da Task 2 (não os testes) até verde.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/features/units/pages/MyUnitMembersPage.test.tsx
git commit -m "test(units): fluxo de adicionar morador com senha provisoria 1x"
```

---

## Task 4: Fluxo "Editar dados"

**Files:**
- Modify: `frontend/src/features/units/pages/MyUnitMembersPage.test.tsx`

- [ ] **Step 1: Adicionar os testes de editar**

```tsx
describe('MyUnitMembersPage — editar', () => {
  it('"Dados" abre o form preenchido e salvar chama updateMember', async () => {
    const user = userEvent.setup();
    render(<MyUnitMembersPage />);
    await screen.findByText('Bia Souza');
    await user.click(screen.getByRole('button', { name: /^dados$/i }));

    expect(await screen.findByDisplayValue('bia@x.com')).toBeInTheDocument();
    expect(screen.getByDisplayValue('Bia Souza')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /^salvar$/i }));
    await waitFor(() => expect(updateMock).toHaveBeenCalled());
    expect(updateMock.mock.calls[0][0]).toBe('m1');
    expect(updateMock.mock.calls[0][1].email).toBe('bia@x.com');
  });

  it('cancelar a edição volta para a lista sem salvar', async () => {
    const user = userEvent.setup();
    render(<MyUnitMembersPage />);
    await screen.findByText('Bia Souza');
    await user.click(screen.getByRole('button', { name: /^dados$/i }));
    await screen.findByDisplayValue('bia@x.com');
    await user.click(screen.getByRole('button', { name: /^cancelar$/i }));
    await screen.findByRole('button', { name: /adicionar morador/i });
    expect(updateMock).not.toHaveBeenCalled();
  });
});
```

- [ ] **Step 2: Rodar e ver passar**

Run: `npm run test -- src/features/units/pages/MyUnitMembersPage.test.tsx`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/features/units/pages/MyUnitMembersPage.test.tsx
git commit -m "test(units): fluxo de editar dados do morador"
```

---

## Task 5: Fluxo "Excluir" + gating sem `RESIDENT_MANAGE`

**Files:**
- Modify: `frontend/src/features/units/pages/MyUnitMembersPage.test.tsx`

- [ ] **Step 1: Adicionar os testes de excluir e de gating**

```tsx
describe('MyUnitMembersPage — excluir e gating', () => {
  it('excluir pede confirmação e então chama deleteMember', async () => {
    const user = userEvent.setup();
    render(<MyUnitMembersPage />);
    await screen.findByText('Bia Souza');
    await user.click(screen.getByRole('button', { name: /excluir bia souza/i }));
    await user.click(screen.getByRole('button', { name: /^confirmar$/i }));
    await waitFor(() => expect(deleteMock).toHaveBeenCalledWith('m1'));
    await waitFor(() => expect(screen.queryByText('Bia Souza')).not.toBeInTheDocument());
  });

  it('cancelar a confirmação não exclui', async () => {
    const user = userEvent.setup();
    render(<MyUnitMembersPage />);
    await screen.findByText('Bia Souza');
    await user.click(screen.getByRole('button', { name: /excluir bia souza/i }));
    await user.click(screen.getByRole('button', { name: /^cancelar$/i }));
    expect(deleteMock).not.toHaveBeenCalled();
    expect(screen.getByText('Bia Souza')).toBeInTheDocument();
  });

  it('sem RESIDENT_MANAGE esconde os botões de ação mas mostra a lista', async () => {
    setAuth([]);
    render(<MyUnitMembersPage />);
    await screen.findByText('Bia Souza');
    expect(screen.queryByRole('button', { name: /adicionar morador/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^dados$/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /excluir bia souza/i })).not.toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Rodar e ver passar**

Run: `npm run test -- src/features/units/pages/MyUnitMembersPage.test.tsx`
Expected: PASS (todos os describes da página).

- [ ] **Step 3: Commit**

```bash
git add frontend/src/features/units/pages/MyUnitMembersPage.test.tsx
git commit -m "test(units): fluxo de excluir morador e gating por RESIDENT_MANAGE"
```

---

## Task 6: Rota no router

**Files:**
- Modify: `frontend/src/router.tsx`

- [ ] **Step 1: Adicionar o import da página**

No bloco de imports de `frontend/src/router.tsx`, logo após a linha do `AccessManagementPage` (linha 24):

```tsx
import { MyUnitMembersPage } from '@/features/units/pages/MyUnitMembersPage';
```

- [ ] **Step 2: Adicionar a rota dentro da casca autenticada**

No array `children`, logo após a linha `{ path: '/admin/acessos', element: <AccessManagementPage /> },`:

```tsx
      { path: '/minha-unidade/moradores', element: <MyUnitMembersPage /> },
```

- [ ] **Step 3: Verificar o type-check**

Run (em `frontend/`): `npm run typecheck`
Expected: sem erros (a página e a rota tipam corretamente).

> Não há teste de router dedicado no repo; o gating de navegação é coberto nas Tasks 7–8 (Sidebar/App). A proteção real do acesso é server-side via `@PreAuthorize`.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/router.tsx
git commit -m "feat(units): rota /minha-unidade/moradores"
```

---

## Task 7: Item "Moradores" na Sidebar (gated `RESIDENT_MANAGE`)

**Files:**
- Modify: `frontend/src/components/layout/Sidebar.test.tsx`
- Modify: `frontend/src/components/layout/Sidebar.tsx`

- [ ] **Step 1: Escrever os testes de gating (falham)**

Adicionar ao `describe('Sidebar', ...)` em `Sidebar.test.tsx`:

```tsx
  it('esconde "Moradores" sem RESIDENT_MANAGE', () => {
    renderSidebar([]);
    expect(screen.queryByRole('link', { name: /^moradores$/i })).not.toBeInTheDocument();
  });

  it('mostra "Moradores" com RESIDENT_MANAGE', () => {
    renderSidebar(['RESIDENT_MANAGE']);
    expect(screen.getAllByRole('link', { name: /^moradores$/i })[0]).toHaveAttribute(
      'href',
      '/minha-unidade/moradores'
    );
  });
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `npm run test -- src/components/layout/Sidebar.test.tsx`
Expected: FAIL — o teste "mostra Moradores" não encontra o link (`/minha-unidade/moradores`).

- [ ] **Step 3: Adicionar o ícone e o item ao `ITEMS`**

Em `Sidebar.tsx`, incluir `Users` no import de `lucide-react` (após `UserCog`):

```tsx
  UserCog,
  Users,
```

E adicionar ao array `ITEMS`, após o item `/admin/acessos`:

```tsx
  {
    to: '/minha-unidade/moradores',
    label: 'Moradores',
    icon: Users,
    brand: 'ink',
    requires: 'RESIDENT_MANAGE',
  },
```

- [ ] **Step 4: Rodar e ver passar**

Run: `npm run test -- src/components/layout/Sidebar.test.tsx`
Expected: PASS (incluindo os 2 novos testes; os existentes seguem verdes).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/layout/Sidebar.tsx frontend/src/components/layout/Sidebar.test.tsx
git commit -m "feat(units): item Moradores na Sidebar gated RESIDENT_MANAGE"
```

---

## Task 8: Card "Moradores" no home (App.tsx, gated `RESIDENT_MANAGE`)

**Files:**
- Modify: `frontend/src/App.test.tsx`
- Modify: `frontend/src/App.tsx`

- [ ] **Step 1: Escrever os testes de gating do card (falham)**

Adicionar ao `describe('App', ...)` em `App.test.tsx`:

```tsx
  it('esconde "Moradores" sem RESIDENT_MANAGE', () => {
    renderApp(['USER_VIEW']);
    expect(screen.queryByRole('link', { name: /^moradores/i })).not.toBeInTheDocument();
  });

  it('mostra "Moradores" com RESIDENT_MANAGE', () => {
    renderApp(['RESIDENT_MANAGE']);
    expect(screen.getByRole('link', { name: /^moradores/i })).toHaveAttribute(
      'href',
      '/minha-unidade/moradores'
    );
  });
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `npm run test -- src/App.test.tsx`
Expected: FAIL — "mostra Moradores" não encontra o link.

- [ ] **Step 3: Adicionar o ícone e o card ao `NAV`**

Em `App.tsx`, incluir `Users` no import de `lucide-react` (após `UserCog`):

```tsx
  UserCog,
  Users,
```

E adicionar ao array `NAV`, após o item `/admin/acessos`:

```tsx
  {
    to: '/minha-unidade/moradores',
    title: 'Moradores',
    desc: 'Cadastre e gerencie os moradores da sua unidade.',
    icon: Users,
    brand: 'ink',
    requires: 'RESIDENT_MANAGE',
  },
```

- [ ] **Step 4: Rodar e ver passar**

Run: `npm run test -- src/App.test.tsx`
Expected: PASS (incluindo os 2 novos testes).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/App.tsx frontend/src/App.test.tsx
git commit -m "feat(units): card Moradores no home gated RESIDENT_MANAGE"
```

---

## Task 9: Sanidade final (suíte + type-check)

**Files:** nenhum (validação).

- [ ] **Step 1: Rodar a suíte de testes do frontend inteira**

Run (em `frontend/`): `npm run test -- --run`
Expected: PASS, sem regressões nos arquivos existentes (`accessApi.test.ts`, `AccessManagementPage.test.tsx`, `Sidebar.test.tsx`, `App.test.tsx`).

- [ ] **Step 2: Type-check**

Run: `npm run typecheck`
Expected: sem erros.

- [ ] **Step 3: Lint (se configurado)**

Run: `npm run lint`
Expected: sem erros novos.

- [ ] **Step 4: Commit (se algo precisou de ajuste; senão pular)**

```bash
git add -A
git commit -m "chore(units): ajustes de sanidade da tela de moradores"
```

---

## Self-Review

**1. Cobertura do objetivo (PR4):**
- (1) `unitMembersApi.ts` + testes de contrato (listMembers, createMember [senha], updateMember, deleteMember) → **Task 1**. ✅
- (2) `MyUnitMembersPage.tsx`: lista (Task 2), adicionar c/ senha 1x (Task 3), editar (Task 4), excluir + gating (Task 5). ✅
- (3) rota (Task 6) + Sidebar gated `RESIDENT_MANAGE` + teste (Task 7) + card no App gated `RESIDENT_MANAGE` + teste (Task 8). ✅
- Sanidade (Task 9). ✅

**2. Placeholders:** Nenhum "TODO"/"similar a"/"add validation". Todo passo com código mostra o código completo. A única ressalva técnica é a **nota sobre `gender`/`birthDate` na edição** (`UnitMemberResponse` não traz esses campos) — está sinalizada como questão a verificar no service, não como código por escrever.

**3. Consistência de tipos com os DTOs do backend:**
- `UnitMember` = `UnitMemberResponse` (id, fullName, greetingName, email, phone, status). ✅
- `CreateMemberPayload` = `CreateUnitMemberRequest` (fullName, greetingName, email, phone, gender, birthDate, whatsappOptIn) — sem password/unitId. ✅
- `CreatedMember` = `CreatedUnitMemberResponse` (id, fullName, password). ✅
- `UpdateMemberPayload` = `UpdateUnitMemberRequest` (fullName, greetingName, phone, email, gender, birthDate) — sem password/unitId. ✅
- Nomes de função usados nos testes (`listMembers`, `createMember`, `updateMember`, `deleteMember`) batem com os exports do client. ✅
- Authority usada na UI (`RESIDENT_MANAGE`) bate com o `@PreAuthorize` do `UnitMemberController`. ✅
- Paths do client (`/units/me/members`) + prefixo `/api` do `@/lib/api` = `/api/units/me/members` do controller. ✅

**Riscos conhecidos:** (a) edição pode zerar `gender`/`birthDate` no backend se o `PUT` os sobrescrever com `null` — ver nota na Task 2; (b) wording de rota/label depende da confirmação do Paulo (Premissa 1).
