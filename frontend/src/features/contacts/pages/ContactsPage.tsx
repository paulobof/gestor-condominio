import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { useAuth } from '@/features/auth/useAuth';
import { OpeningHoursDisplay } from '@/components/openinghours/OpeningHoursDisplay';
import { listContacts, type Contact } from '../api/contactsApi';

/** Groups contacts by category preserving the first-seen order. */
function groupByCategory(contacts: Contact[]): { category: string; items: Contact[] }[] {
  const order: string[] = [];
  const map: Record<string, Contact[]> = {};
  for (const c of contacts) {
    if (!map[c.category]) {
      order.push(c.category);
      map[c.category] = [];
    }
    map[c.category].push(c);
  }
  return order.map((cat) => ({ category: cat, items: map[cat] }));
}

export function ContactsPage() {
  const { user } = useAuth();
  const canManage = !!user && user.authorities.includes('CONTACT_MANAGE');
  const [items, setItems] = useState<Contact[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    setLoading(true);
    listContacts()
      .then((data) => {
        if (active) setItems(data);
      })
      .catch(() => {
        if (active) toast.error('Erro ao carregar contatos.');
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
          Contatos
        </h1>
        {canManage && (
          <Button asChild className="min-h-[44px]">
            <Link to="/contatos/gerenciar">Gerenciar</Link>
          </Button>
        )}
      </div>

      {loading ? (
        <p className="text-muted-foreground">Carregando…</p>
      ) : groups.length === 0 ? (
        <p className="text-muted-foreground">Nenhum contato cadastrado ainda.</p>
      ) : (
        <div className="space-y-6">
          {groups.map(({ category, items: contacts }) => (
            <section key={category}>
              <h2 className="mb-2 text-lg font-heading font-semibold">{category}</h2>
              <div className="space-y-3">
                {contacts.map((c) => (
                  <div key={c.id} className="rounded-lg border border-border bg-card p-4 shadow-sm">
                    <p className="font-medium">{c.name}</p>
                    <a
                      href={`tel:${c.phone}`}
                      className="mt-1 inline-block text-sm text-primary hover:underline"
                    >
                      {c.phone}
                    </a>
                    {c.notes && (
                      <p className="mt-2 text-sm text-muted-foreground whitespace-pre-line">
                        {c.notes}
                      </p>
                    )}
                    <div className="mt-2">
                      <OpeningHoursDisplay openingHours={c.openingHours} is24h={c.is24h} />
                    </div>
                  </div>
                ))}
              </div>
            </section>
          ))}
        </div>
      )}
    </main>
  );
}
