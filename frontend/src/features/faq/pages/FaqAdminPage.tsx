import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { toast } from 'sonner';
import { ArrowLeft } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  listAllFaq,
  createFaq,
  updateFaq,
  setFaqPublished,
  reorderFaq,
  deleteFaq,
  type Faq,
} from '../api/faqApi';

interface FormState {
  id?: string;
  question: string;
  answer: string;
  category: string;
  published: boolean;
}

const EMPTY_FORM: FormState = {
  question: '',
  answer: '',
  category: '',
  published: false,
};

/** Groups FAQs by category preserving order. */
function groupByCategory(faqs: Faq[]): { category: string; items: Faq[] }[] {
  const order: string[] = [];
  const map: Record<string, Faq[]> = {};
  for (const faq of faqs) {
    if (!map[faq.category]) {
      order.push(faq.category);
      map[faq.category] = [];
    }
    map[faq.category].push(faq);
  }
  return order.map((cat) => ({ category: cat, items: map[cat] }));
}

export function FaqAdminPage() {
  const [items, setItems] = useState<Faq[]>([]);
  const [form, setForm] = useState<FormState>(EMPTY_FORM);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  const reload = () => {
    setLoading(true);
    return listAllFaq()
      .then((data) => setItems(data))
      .catch(() => toast.error('Erro ao carregar FAQ.'))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    reload();
  }, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.question.trim()) {
      toast.error('Informe a pergunta.');
      return;
    }
    if (!form.answer.trim()) {
      toast.error('Informe a resposta.');
      return;
    }
    if (!form.category.trim()) {
      toast.error('Informe a categoria.');
      return;
    }
    setSaving(true);
    const body = {
      question: form.question.trim(),
      answer: form.answer.trim(),
      category: form.category.trim(),
      published: form.published,
    };
    try {
      if (form.id) {
        await updateFaq(form.id, body);
        toast.success('FAQ atualizada.');
      } else {
        await createFaq(body);
        toast.success('FAQ criada.');
      }
      setForm(EMPTY_FORM);
      await reload();
    } catch {
      toast.error('Erro ao salvar FAQ.');
    } finally {
      setSaving(false);
    }
  };

  const handleEdit = (item: Faq) => {
    setForm({
      id: item.id,
      question: item.question,
      answer: item.answer,
      category: item.category,
      published: item.published,
    });
  };

  const handleDelete = async (id: string) => {
    try {
      await deleteFaq(id);
      toast.success('FAQ excluída.');
      await reload();
    } catch {
      toast.error('Erro ao excluir FAQ.');
    }
  };

  const handleTogglePublish = async (item: Faq) => {
    try {
      await setFaqPublished(item.id, !item.published);
      toast.success(item.published ? 'FAQ despublicada.' : 'FAQ publicada.');
      await reload();
    } catch {
      toast.error('Erro ao alterar publicação.');
    }
  };

  const handleMove = async (item: Faq, direction: 'up' | 'down') => {
    const sameCategory = items.filter((i) => i.category === item.category);
    const idx = sameCategory.findIndex((i) => i.id === item.id);
    const targetIdx = direction === 'up' ? idx - 1 : idx + 1;
    if (targetIdx < 0 || targetIdx >= sameCategory.length) return;

    const a = sameCategory[idx];
    const b = sameCategory[targetIdx];
    try {
      await reorderFaq([
        { id: a.id, ordering: b.ordering },
        { id: b.id, ordering: a.ordering },
      ]);
      await reload();
    } catch {
      toast.error('Erro ao reordenar FAQ.');
    }
  };

  const groups = groupByCategory(items);

  return (
    <main className="mx-auto max-w-3xl p-4 space-y-6">
      <Button asChild variant="ghost" className="min-h-[44px] px-2">
        <Link to="/faq">
          <ArrowLeft aria-hidden="true" /> Voltar
        </Link>
      </Button>

      <h1 className="flex items-center gap-2 text-2xl font-heading font-semibold">
        <span
          aria-hidden="true"
          className="inline-block h-6 w-1.5 rounded-full"
          style={{ backgroundColor: 'hsl(var(--brand-blue))' }}
        />
        Gerenciar FAQ
      </h1>

      {/* Create / Edit form */}
      <Card>
        <CardHeader>
          <CardTitle>{form.id ? 'Editar FAQ' : 'Nova FAQ'}</CardTitle>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} className="space-y-4" noValidate>
            <div className="space-y-2">
              <Label htmlFor="question">Pergunta</Label>
              <Input
                id="question"
                value={form.question}
                onChange={(e) => setForm((f) => ({ ...f, question: e.target.value }))}
                maxLength={200}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="answer">Resposta</Label>
              <textarea
                id="answer"
                value={form.answer}
                onChange={(e) => setForm((f) => ({ ...f, answer: e.target.value }))}
                rows={4}
                maxLength={5000}
                className="flex w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
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
            <label className="flex min-h-[44px] cursor-pointer items-center gap-2">
              <input
                id="published"
                type="checkbox"
                className="h-5 w-5"
                checked={form.published}
                onChange={(e) => setForm((f) => ({ ...f, published: e.target.checked }))}
                aria-label="Publicado"
              />
              <span className="text-sm font-medium">Publicado</span>
            </label>
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
      ) : groups.length === 0 ? (
        <p className="text-muted-foreground">Nenhuma FAQ cadastrada.</p>
      ) : (
        <div className="space-y-6">
          {groups.map(({ category, items: faqs }) => (
            <section key={category}>
              <h2 className="mb-2 text-lg font-heading font-semibold">{category}</h2>
              <ul className="space-y-2">
                {faqs.map((item, idx) => {
                  const isFirst = idx === 0;
                  const isLast = idx === faqs.length - 1;
                  return (
                    <li key={item.id}>
                      <Card>
                        <CardContent className="pt-4">
                          <div className="flex items-start justify-between gap-2">
                            <div className="flex-1 min-w-0">
                              <p className="font-medium text-sm">{item.question}</p>
                              <p className="mt-1 text-xs text-muted-foreground line-clamp-2">
                                {item.answer}
                              </p>
                              <div className="mt-2">
                                {item.published ? (
                                  <span className="rounded-full bg-green-100 px-2 py-0.5 text-xs font-medium text-green-700 dark:bg-green-900/30 dark:text-green-400">
                                    Publicado
                                  </span>
                                ) : (
                                  <span className="rounded-full bg-muted px-2 py-0.5 text-xs font-medium text-muted-foreground">
                                    Rascunho
                                  </span>
                                )}
                              </div>
                            </div>
                            <div className="flex shrink-0 flex-wrap gap-1 justify-end">
                              <Button
                                size="sm"
                                variant="outline"
                                className="min-h-[44px]"
                                onClick={() => handleMove(item, 'up')}
                                disabled={isFirst}
                                aria-label="↑"
                              >
                                ↑
                              </Button>
                              <Button
                                size="sm"
                                variant="outline"
                                className="min-h-[44px]"
                                onClick={() => handleMove(item, 'down')}
                                disabled={isLast}
                                aria-label="↓"
                              >
                                ↓
                              </Button>
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
                                variant="outline"
                                className="min-h-[44px]"
                                onClick={() => handleTogglePublish(item)}
                              >
                                {item.published ? 'Despublicar' : 'Publicar'}
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
                  );
                })}
              </ul>
            </section>
          ))}
        </div>
      )}
    </main>
  );
}
