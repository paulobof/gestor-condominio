import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { toast } from 'sonner';
import { ArrowLeft } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { RichTextEditor } from '@/components/richtext/RichTextEditor';
import {
  listSections,
  createSection,
  updateSection,
  reorderSections,
  deleteSection,
  type InfoSection,
} from '../api/generalInfoApi';

interface FormState {
  id?: string;
  title: string;
  body: string;
}

const EMPTY_FORM: FormState = { title: '', body: '' };

export function InfoAdminPage() {
  const [items, setItems] = useState<InfoSection[]>([]);
  const [form, setForm] = useState<FormState>(EMPTY_FORM);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  const reload = () => {
    setLoading(true);
    return listSections()
      .then((data) => setItems(data))
      .catch(() => toast.error('Erro ao carregar informações.'))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    reload();
  }, []);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    try {
      if (form.id) {
        await updateSection(form.id, { title: form.title, body: form.body });
        toast.success('Seção atualizada.');
      } else {
        await createSection({ title: form.title, body: form.body });
        toast.success('Seção criada.');
      }
      setForm(EMPTY_FORM);
      await reload();
    } catch {
      toast.error('Erro ao salvar a seção.');
    } finally {
      setSaving(false);
    }
  };

  const remove = async (id: string) => {
    try {
      await deleteSection(id);
      toast.success('Seção excluída.');
      await reload();
    } catch {
      toast.error('Erro ao excluir.');
    }
  };

  const swap = async (i: number, j: number) => {
    if (j < 0 || j >= items.length) return;
    const a = items[i];
    const b = items[j];
    try {
      await reorderSections([
        { id: a.id, position: b.position },
        { id: b.id, position: a.position },
      ]);
      await reload();
    } catch {
      toast.error('Erro ao reordenar.');
    }
  };

  return (
    <main className="mx-auto max-w-3xl p-4">
      <div className="mb-4 flex items-center gap-2">
        <Button asChild variant="ghost" size="icon" className="min-h-[44px] min-w-[44px]">
          <Link to="/informacoes" aria-label="Voltar">
            <ArrowLeft className="h-5 w-5" />
          </Link>
        </Button>
        <h1 className="text-2xl font-heading font-semibold">Gerenciar informações</h1>
      </div>

      <Card className="mb-6">
        <CardHeader>
          <CardTitle>{form.id ? 'Editar seção' : 'Nova seção'}</CardTitle>
        </CardHeader>
        <CardContent>
          <form onSubmit={submit} className="space-y-4">
            <div className="space-y-1">
              <Label htmlFor="title">Título</Label>
              <Input
                id="title"
                value={form.title}
                maxLength={120}
                onChange={(e) => setForm((f) => ({ ...f, title: e.target.value }))}
                required
              />
            </div>
            <div className="space-y-1">
              <Label>Conteúdo</Label>
              <RichTextEditor
                value={form.body}
                onChange={(html) => setForm((f) => ({ ...f, body: html }))}
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

      {loading ? (
        <p className="text-muted-foreground">Carregando…</p>
      ) : (
        <ul className="space-y-2">
          {items.map((s, idx) => (
            <li
              key={s.id}
              className="flex items-center justify-between gap-3 rounded-lg border border-border p-3"
            >
              <span className="font-medium">{s.title}</span>
              <div className="flex gap-1">
                <Button
                  type="button"
                  variant="outline"
                  size="icon"
                  aria-label="↑"
                  className="min-h-[44px] min-w-[44px]"
                  disabled={idx === 0}
                  onClick={() => swap(idx, idx - 1)}
                >
                  ↑
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  size="icon"
                  aria-label="↓"
                  className="min-h-[44px] min-w-[44px]"
                  disabled={idx === items.length - 1}
                  onClick={() => swap(idx, idx + 1)}
                >
                  ↓
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  className="min-h-[44px]"
                  onClick={() => setForm({ id: s.id, title: s.title, body: s.body })}
                >
                  Editar
                </Button>
                <Button
                  type="button"
                  variant="destructive"
                  className="min-h-[44px]"
                  onClick={() => remove(s.id)}
                >
                  Excluir
                </Button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </main>
  );
}
