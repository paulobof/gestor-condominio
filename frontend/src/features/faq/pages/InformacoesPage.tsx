import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { useAuth } from '@/features/auth/useAuth';
import { listFaq, type Faq } from '../api/faqApi';

/** Groups FAQs by category preserving the first-seen order. */
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

export function InformacoesPage() {
  const { user } = useAuth();
  const canManage = !!user && user.authorities.includes('FAQ_MANAGE');
  const [items, setItems] = useState<Faq[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    setLoading(true);
    listFaq()
      .then((data) => {
        if (active) setItems(data);
      })
      .catch(() => {
        if (active) toast.error('Erro ao carregar informações.');
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);

  const groups = groupByCategory(items);

  return (
    <main className="mx-auto max-w-3xl p-4">
      <div className="mb-4 flex items-center justify-between">
        <h1 className="flex items-center gap-2 text-2xl font-heading font-semibold">
          <span
            aria-hidden="true"
            className="inline-block h-6 w-1.5 rounded-full"
            style={{ backgroundColor: 'hsl(var(--brand-blue))' }}
          />
          Informações
        </h1>
        {canManage && (
          <Button asChild className="min-h-[44px]">
            <Link to="/informacoes/gerenciar">Gerenciar</Link>
          </Button>
        )}
      </div>

      {loading ? (
        <p className="text-muted-foreground">Carregando…</p>
      ) : groups.length === 0 ? (
        <p className="text-muted-foreground">Nenhuma informação publicada ainda.</p>
      ) : (
        <div className="space-y-6">
          {groups.map(({ category, items: faqs }) => (
            <section key={category}>
              <h2 className="mb-2 text-lg font-heading font-semibold">{category}</h2>
              <div className="space-y-1 rounded-lg border border-border overflow-hidden">
                {faqs.map((faq) => (
                  <details key={faq.id} className="group border-b border-border last:border-b-0">
                    <summary className="flex min-h-[44px] cursor-pointer list-none items-center justify-between gap-3 px-4 py-3 text-sm font-medium hover:bg-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-inset">
                      <span>{faq.question}</span>
                      <svg
                        aria-hidden="true"
                        className="h-4 w-4 shrink-0 transition-transform group-open:rotate-180"
                        fill="none"
                        stroke="currentColor"
                        strokeWidth={2}
                        viewBox="0 0 24 24"
                      >
                        <path strokeLinecap="round" strokeLinejoin="round" d="M19 9l-7 7-7-7" />
                      </svg>
                    </summary>
                    <div className="px-4 pb-4 pt-1 text-sm text-muted-foreground">
                      <p className="whitespace-pre-line">{faq.answer}</p>
                    </div>
                  </details>
                ))}
              </div>
            </section>
          ))}
        </div>
      )}
    </main>
  );
}
