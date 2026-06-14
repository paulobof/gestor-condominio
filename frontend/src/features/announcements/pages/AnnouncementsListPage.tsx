import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { toast } from 'sonner';
import { ArrowUp, ArrowDown } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { useAuth } from '@/features/auth/useAuth';
import {
  listAnnouncements,
  reorderAnnouncements,
  type Announcement,
  type AnnouncementImportance,
} from '../api/announcementsApi';

const IMPORTANCE_COLOR: Record<AnnouncementImportance, string> = {
  HIGH: 'hsl(var(--brand-red))',
  MEDIUM: 'hsl(var(--brand-yellow))',
  LOW: 'hsl(var(--brand-blue))',
};

const IMPORTANCE_LABEL: Record<AnnouncementImportance, string> = {
  HIGH: 'Urgente',
  MEDIUM: 'Importante',
  LOW: 'Informativo',
};

export function AnnouncementsListPage() {
  const { user } = useAuth();
  const canManage = !!user && user.authorities.includes('ANNOUNCEMENT_MANAGE');
  const [items, setItems] = useState<Announcement[]>([]);
  const [loading, setLoading] = useState(true);

  const load = () => {
    setLoading(true);
    return listAnnouncements()
      .then((p) => setItems(p.content))
      .catch(() => toast.error('Erro ao carregar avisos.'))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
  }, []);

  const move = async (i: number, j: number) => {
    if (j < 0 || j >= items.length) return;
    const a = items[i];
    const b = items[j];
    try {
      await reorderAnnouncements([
        { id: a.id, position: b.position },
        { id: b.id, position: a.position },
      ]);
      await load();
    } catch {
      toast.error('Erro ao reordenar.');
    }
  };

  return (
    <main className="mx-auto max-w-3xl p-4">
      <div className="mb-4 flex items-center justify-between">
        <h1 className="flex items-center gap-2 text-2xl font-heading font-semibold">
          <span
            aria-hidden="true"
            className="inline-block h-6 w-1.5 rounded-full"
            style={{ backgroundColor: 'hsl(var(--brand-red))' }}
          />
          Mural de avisos
        </h1>
        {canManage && (
          <Button asChild className="min-h-[44px]">
            <Link to="/avisos/novo">Novo aviso</Link>
          </Button>
        )}
      </div>

      {loading ? (
        <p className="text-muted-foreground">Carregando…</p>
      ) : items.length === 0 ? (
        <p className="text-muted-foreground">Nenhum aviso.</p>
      ) : (
        <ul className="space-y-3">
          {items.map((a, idx) => (
            <li key={a.id} className="flex items-stretch gap-2">
              <Link
                to={`/avisos/${a.id}`}
                className="block flex-1 rounded-lg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
              >
                <Card
                  className="h-full border-l-4 transition-colors hover:bg-accent"
                  style={{ borderLeftColor: IMPORTANCE_COLOR[a.importance] }}
                >
                  <CardHeader>
                    <div className="flex items-center justify-between gap-2">
                      <CardTitle className="text-base">{a.title}</CardTitle>
                      <span
                        className="shrink-0 rounded-full px-2 py-0.5 text-xs font-medium text-white"
                        style={{ backgroundColor: IMPORTANCE_COLOR[a.importance] }}
                      >
                        {IMPORTANCE_LABEL[a.importance]}
                      </span>
                    </div>
                  </CardHeader>
                  <CardContent>
                    <p className="line-clamp-2 text-sm text-muted-foreground">{a.body}</p>
                  </CardContent>
                </Card>
              </Link>
              {canManage && (
                <div className="flex flex-col justify-center gap-1">
                  <Button
                    type="button"
                    variant="outline"
                    size="icon"
                    aria-label="Mover para cima"
                    className="min-h-[44px] min-w-[44px]"
                    disabled={idx === 0}
                    onClick={() => move(idx, idx - 1)}
                  >
                    <ArrowUp className="h-4 w-4" aria-hidden="true" />
                  </Button>
                  <Button
                    type="button"
                    variant="outline"
                    size="icon"
                    aria-label="Mover para baixo"
                    className="min-h-[44px] min-w-[44px]"
                    disabled={idx === items.length - 1}
                    onClick={() => move(idx, idx + 1)}
                  >
                    <ArrowDown className="h-4 w-4" aria-hidden="true" />
                  </Button>
                </div>
              )}
            </li>
          ))}
        </ul>
      )}
    </main>
  );
}
