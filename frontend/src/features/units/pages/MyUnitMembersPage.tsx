import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { PhoneInput } from '@/components/ui/phone-input';
import { useAuth } from '@/features/auth/useAuth';
import {
  listMembers,
  listMyUnits,
  getMemberDetail,
  createMember,
  updateMember,
  deleteMember,
  type UnitMember,
  type MyUnit,
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
  const [units, setUnits] = useState<MyUnit[]>([]);
  const [loading, setLoading] = useState(true);
  const [editing, setEditing] = useState<UnitMember | null>(null);
  const [adding, setAdding] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState<string | null>(null);

  const multiUnit = units.length > 1;

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

  useEffect(() => {
    listMyUnits()
      .then(setUnits)
      .catch(() => {
        /* sem unidades extras: segue single-unit */
      });
  }, []);

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
                  <span className="font-medium">
                    {m.fullName}
                    {multiUnit && m.unitCode && (
                      <span className="ml-2 rounded-full bg-muted px-2 py-0.5 text-xs font-normal text-muted-foreground">
                        {m.unitCode}
                      </span>
                    )}
                  </span>
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
          units={units}
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

function AddMemberForm({
  units,
  onDone,
  onCancel,
}: {
  units: MyUnit[];
  onDone: () => void;
  onCancel: () => void;
}) {
  const multiUnit = units.length > 1;
  const [fullName, setFullName] = useState('');
  const [greetingName, setGreetingName] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [gender, setGender] = useState('');
  const [birthDate, setBirthDate] = useState('');
  const [unitId, setUnitId] = useState('');
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
        unitId: multiUnit ? unitId || null : null,
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
          {multiUnit && (
            <div className="space-y-1">
              <label htmlFor="nm-unit" className="text-sm font-medium">
                Unidade
              </label>
              <select
                id="nm-unit"
                value={unitId}
                onChange={(e) => setUnitId(e.target.value)}
                className="min-h-[44px] w-full rounded-lg border border-border bg-background px-3 text-sm"
              >
                <option value="">Selecione…</option>
                {units.map((u) => (
                  <option key={u.unitId} value={u.unitId}>
                    {u.code}
                  </option>
                ))}
              </select>
            </div>
          )}
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
          <div className="space-y-1">
            <label htmlFor="nm-phone" className="text-sm font-medium">
              Telefone
            </label>
            <PhoneInput id="nm-phone" value={phone} onChange={setPhone} />
          </div>
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
            <Button
              type="submit"
              className="min-h-[44px]"
              disabled={saving || (multiUnit && !unitId)}
            >
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

  // Gênero e data de nascimento não vêm na lista; busca o detalhe para pré-preencher
  // e evitar que um "Salvar" sem alterações apague os valores salvos.
  useEffect(() => {
    let active = true;
    void (async () => {
      try {
        const detail = await getMemberDetail(member.id);
        if (!active) return;
        setFullName(detail.fullName);
        setGreetingName(detail.greetingName ?? '');
        setEmail(detail.email ?? '');
        setPhone(detail.phone ?? '');
        setGender(detail.gender ?? '');
        setBirthDate(detail.birthDate ?? '');
      } catch {
        toast.error('Erro ao carregar os dados do morador.');
      }
    })();
    return () => {
      active = false;
    };
  }, [member.id]);

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
          <div className="space-y-1">
            <label htmlFor="em-phone" className="text-sm font-medium">
              Telefone
            </label>
            <PhoneInput id="em-phone" value={phone} onChange={setPhone} />
          </div>
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
