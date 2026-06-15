import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { toast } from 'sonner';
import { ArrowLeft } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { OpeningHoursEditor } from '@/components/openinghours/OpeningHoursEditor';
import type { OpeningHoursDto } from '@/components/openinghours/openingHours';
import {
  listContacts,
  createContact,
  updateContact,
  deleteContact,
  type Contact,
} from '../api/contactsApi';

interface FormState {
  id?: string;
  name: string;
  category: string;
  phone: string;
  notes: string;
  is24h: boolean;
  openingHours: OpeningHoursDto[];
}

const EMPTY_FORM: FormState = {
  name: '',
  category: '',
  phone: '',
  notes: '',
  is24h: false,
  openingHours: [],
};

export function ContactsAdminPage() {
  const [items, setItems] = useState<Contact[]>([]);
  const [form, setForm] = useState<FormState>(EMPTY_FORM);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  const reload = () => {
    setLoading(true);
    return listContacts()
      .then((data) => setItems(data))
      .catch(() => toast.error('Erro ao carregar contatos.'))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    reload();
  }, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.name.trim()) {
      toast.error('Informe o nome.');
      return;
    }
    if (!form.category.trim()) {
      toast.error('Informe a categoria.');
      return;
    }
    if (!form.phone.trim()) {
      toast.error('Informe o telefone.');
      return;
    }
    setSaving(true);
    const body = {
      name: form.name.trim(),
      category: form.category.trim(),
      phone: form.phone.trim(),
      notes: form.notes.trim(),
      is24h: form.is24h,
      openingHours: form.openingHours,
    };
    try {
      if (form.id) {
        await updateContact(form.id, body);
        toast.success('Contato atualizado.');
      } else {
        await createContact(body);
        toast.success('Contato criado.');
      }
      setForm(EMPTY_FORM);
      await reload();
    } catch {
      toast.error('Erro ao salvar contato.');
    } finally {
      setSaving(false);
    }
  };

  const handleEdit = (item: Contact) => {
    setForm({
      id: item.id,
      name: item.name,
      category: item.category,
      phone: item.phone,
      notes: item.notes ?? '',
      is24h: item.is24h,
      openingHours: item.openingHours,
    });
  };

  const handleDelete = async (id: string) => {
    try {
      await deleteContact(id);
      toast.success('Contato excluído.');
      await reload();
    } catch {
      toast.error('Erro ao excluir contato.');
    }
  };

  return (
    <main className="mx-auto max-w-3xl p-4 space-y-6">
      <Button asChild variant="ghost" className="min-h-[44px] px-2">
        <Link to="/contatos">
          <ArrowLeft aria-hidden="true" /> Voltar
        </Link>
      </Button>

      <h1 className="flex items-center gap-2 text-2xl font-heading font-semibold">
        <span
          aria-hidden="true"
          className="inline-block h-6 w-1.5 rounded-full"
          style={{ backgroundColor: 'hsl(var(--brand-blue))' }}
        />
        Gerenciar contatos
      </h1>

      {/* Create / Edit form */}
      <Card>
        <CardHeader>
          <CardTitle>{form.id ? 'Editar contato' : 'Novo contato'}</CardTitle>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} className="space-y-4" noValidate>
            <div className="space-y-2">
              <Label htmlFor="name">Nome</Label>
              <Input
                id="name"
                value={form.name}
                onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
                maxLength={120}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="category">Categoria</Label>
              <Input
                id="category"
                value={form.category}
                onChange={(e) => setForm((f) => ({ ...f, category: e.target.value }))}
                maxLength={80}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="phone">Telefone</Label>
              <Input
                id="phone"
                value={form.phone}
                onChange={(e) => setForm((f) => ({ ...f, phone: e.target.value }))}
                maxLength={30}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="notes">Observações</Label>
              <textarea
                id="notes"
                value={form.notes}
                onChange={(e) => setForm((f) => ({ ...f, notes: e.target.value }))}
                rows={3}
                maxLength={500}
                className="flex w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
              />
            </div>
            <div className="space-y-2">
              <Label>Horário de funcionamento</Label>
              <OpeningHoursEditor
                key={form.id ?? 'new'}
                value={form.openingHours}
                is24h={form.is24h}
                onChange={(hours, is24h) =>
                  setForm((f) => ({ ...f, openingHours: hours as OpeningHoursDto[], is24h }))
                }
              />
            </div>
            <div className="flex gap-2">
              <Button type="submit" disabled={saving} className="min-h-[44px]">
                {saving ? 'Salvando…' : 'Salvar'}
              </Button>
              {form.id && (
                <Button
                  type="button"
                  variant="outline"
                  className="min-h-[44px]"
                  onClick={() => setForm(EMPTY_FORM)}
                >
                  Cancelar
                </Button>
              )}
            </div>
          </form>
        </CardContent>
      </Card>

      {/* List */}
      {loading ? (
        <p className="text-muted-foreground">Carregando…</p>
      ) : items.length === 0 ? (
        <p className="text-muted-foreground">Nenhum contato cadastrado.</p>
      ) : (
        <ul className="space-y-2">
          {items.map((item) => (
            <li key={item.id}>
              <Card>
                <CardContent className="pt-4">
                  <div className="flex items-start justify-between gap-2">
                    <div className="flex-1 min-w-0">
                      <p className="font-medium text-sm">{item.name}</p>
                      <p className="mt-0.5 text-xs text-muted-foreground">{item.category}</p>
                      <p className="mt-0.5 text-xs text-muted-foreground">{item.phone}</p>
                      {item.is24h && (
                        <span className="mt-1 inline-block rounded-full bg-green-100 px-2 py-0.5 text-xs font-medium text-green-700 dark:bg-green-900/30 dark:text-green-400">
                          24h
                        </span>
                      )}
                    </div>
                    <div className="flex shrink-0 flex-wrap gap-1 justify-end">
                      <Button
                        size="sm"
                        variant="outline"
                        className="min-h-[44px]"
                        onClick={() => handleEdit(item)}
                      >
                        Editar
                      </Button>
                      <Button
                        size="sm"
                        variant="destructive"
                        className="min-h-[44px]"
                        onClick={() => handleDelete(item.id)}
                      >
                        Excluir
                      </Button>
                    </div>
                  </div>
                </CardContent>
              </Card>
            </li>
          ))}
        </ul>
      )}
    </main>
  );
}
