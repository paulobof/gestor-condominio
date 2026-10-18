import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Plus } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';
import {
  getCatalog,
  listBoard,
  listMine,
  type Catalog,
  type Complaint,
} from '../api/complaintsApi';
import { ComplaintCard } from '../components/ComplaintCard';

type Tab = 'board' | 'mine';
const SELECT =
  'flex h-11 w-full rounded-md border border-input bg-background px-3 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring';

export function ComplaintsPage() {
  const [tab, setTab] = useState<Tab>('board');
  const [catalog, setCatalog] = useState<Catalog>({ types: [], locations: [] });
  const [typeId, setTypeId] = useState('');
  const [locationId, setLocationId] = useState('');
  const [status, setStatus] = useState<'' | 'FORWARDED' | 'RESOLVED'>('');
  const [board, setBoard] = useState<Complaint[]>([]);
  const [mine, setMine] = useState<Complaint[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [boardPage, setBoardPage] = useState({ number: 0, totalPages: 1 });
  const [loadingMore, setLoadingMore] = useState(false);

  useEffect(() => {
    getCatalog()
      .then(setCatalog)
      .catch(() => {});
  }, []);

  useEffect(() => {
    if (tab !== 'board') return;
    let active = true;
    setLoading(true);
    setError(false);
    listBoard({
      typeId: typeId || undefined,
      locationId: locationId || undefined,
      status: status || undefined,
      page: 0,
    })
      .then((p) => {
        if (!active) return;
        setBoard(p.content);
        setBoardPage({ number: p.number, totalPages: p.totalPages });
      })
      .catch(() => active && setError(true))
      .finally(() => active && setLoading(false));
    return () => {
      active = false;
    };
  }, [tab, typeId, locationId, status]);

  useEffect(() => {
    if (tab !== 'mine') return;
    let active = true;
    setLoading(true);
    setError(false);
    listMine()
      .then((l) => active && setMine(l))
      .catch(() => active && setError(true))
      .finally(() => active && setLoading(false));
    return () => {
      active = false;
    };
  }, [tab]);

  const list = tab === 'board' ? board : mine;

  const loadMore = () => {
    setLoadingMore(true);
    listBoard({
      typeId: typeId || undefined,
      locationId: locationId || undefined,
      status: status || undefined,
      page: boardPage.number + 1,
    })
      .then((p) => {
        setBoard((prev) => [...prev, ...p.content]);
        setBoardPage({ number: p.number, totalPages: p.totalPages });
      })
      .catch(() => setError(true))
      .finally(() => setLoadingMore(false));
  };

  return (
    <main className="mx-auto max-w-3xl space-y-4 p-4">
      <div className="flex items-center justify-between gap-2">
        <h1 className="text-2xl font-semibold">Chamados</h1>
        <Button asChild className="min-h-[44px]">
          <Link to="/chamados/novo">
            <Plus aria-hidden="true" /> Abrir chamado
          </Link>
        </Button>
      </div>

      <div role="tablist" aria-label="Visualização" className="flex gap-2">
        {(
          [
            ['board', 'Mural'],
            ['mine', 'Meus chamados'],
          ] as const
        ).map(([key, label]) => (
          <button
            key={key}
            role="tab"
            aria-selected={tab === key}
            onClick={() => setTab(key)}
            className={`min-h-[44px] rounded-md px-4 text-sm font-medium ${
              tab === key ? 'bg-primary text-primary-foreground' : 'bg-muted'
            }`}
          >
            {label}
          </button>
        ))}
      </div>

      {tab === 'board' && (
        <div className="grid gap-3 sm:grid-cols-3">
          <div className="space-y-1">
            <Label htmlFor="f-type">Tipo</Label>
            <select
              id="f-type"
              className={SELECT}
              value={typeId}
              onChange={(e) => setTypeId(e.target.value)}
            >
              <option value="">Todos</option>
              {catalog.types.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.name}
                </option>
              ))}
            </select>
          </div>
          <div className="space-y-1">
            <Label htmlFor="f-location">Local</Label>
            <select
              id="f-location"
              className={SELECT}
              value={locationId}
              onChange={(e) => setLocationId(e.target.value)}
            >
              <option value="">Todos</option>
              {catalog.locations.map((l) => (
                <option key={l.id} value={l.id}>
                  {l.name}
                </option>
              ))}
            </select>
          </div>
          <div className="space-y-1">
            <Label htmlFor="f-status">Status</Label>
            <select
              id="f-status"
              className={SELECT}
              value={status}
              onChange={(e) => setStatus(e.target.value as typeof status)}
            >
              <option value="">Todos</option>
              <option value="FORWARDED">Encaminhado</option>
              <option value="RESOLVED">Resolvido</option>
            </select>
          </div>
        </div>
      )}

      {tab === 'board' && (
        <p className="text-sm text-muted-foreground">
          Chamados já encaminhados à síndica. Antes de abrir um novo, veja se o problema já está
          aqui.
        </p>
      )}

      {loading && <p>Carregando…</p>}
      {!loading && error && <p role="alert">Não foi possível carregar os chamados.</p>}
      {!loading && !error && list.length === 0 && (
        <p className="text-muted-foreground">
          {tab === 'board' ? 'Nenhum chamado encaminhado.' : 'Você ainda não abriu chamados.'}
        </p>
      )}
      <ul className="space-y-3">
        {list.map((c) => (
          <ComplaintCard
            key={c.id}
            c={c}
            to={`/chamados/${c.id}`}
            showThumbnail={tab === 'board'}
          />
        ))}
      </ul>
      {tab === 'board' && !loading && boardPage.number + 1 < boardPage.totalPages && (
        <Button
          variant="outline"
          className="min-h-[44px]"
          disabled={loadingMore}
          onClick={loadMore}
        >
          Carregar mais
        </Button>
      )}
    </main>
  );
}
