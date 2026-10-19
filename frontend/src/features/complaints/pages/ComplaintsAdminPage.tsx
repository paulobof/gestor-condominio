import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Send, ListTree } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Page, PageHeader } from '@/components/layout/Page';
import { adminQueue, type AdminComplaintRow, type ComplaintStatus } from '../api/complaintsApi';
import { StatusBadge } from '../components/StatusBadge';

const TABS: { key: string; label: string; statuses: ComplaintStatus[] }[] = [
  { key: 'pending', label: 'Pendentes', statuses: ['OPEN', 'IN_REVIEW', 'READY'] },
  { key: 'forwarded', label: 'Encaminhadas', statuses: ['FORWARDED'] },
  { key: 'resolved', label: 'Resolvidas', statuses: ['RESOLVED'] },
  { key: 'rejected', label: 'Recusadas', statuses: ['REJECTED'] },
];

export function ComplaintsAdminPage() {
  const [tab, setTab] = useState(TABS[0]);
  const [rows, setRows] = useState<AdminComplaintRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState({ number: 0, totalPages: 1 });
  const [loadingMore, setLoadingMore] = useState(false);

  useEffect(() => {
    let active = true;
    setLoading(true);
    adminQueue(tab.statuses, 0)
      .then((p) => {
        if (!active) return;
        setRows(p.content);
        setPage({ number: p.number, totalPages: p.totalPages });
      })
      .catch(() => {
        if (!active) return;
        setRows([]);
        setPage({ number: 0, totalPages: 1 });
      })
      .finally(() => active && setLoading(false));
    return () => {
      active = false;
    };
  }, [tab]);

  const loadMore = () => {
    setLoadingMore(true);
    adminQueue(tab.statuses, page.number + 1)
      .then((p) => {
        setRows((prev) => [...prev, ...p.content]);
        setPage({ number: p.number, totalPages: p.totalPages });
      })
      .catch(() => {})
      .finally(() => setLoadingMore(false));
  };

  return (
    <Page variant="list">
      <PageHeader
        title="Gestão de reclamações"
        actions={
          <>
            <Button asChild variant="outline" className="min-h-[44px]">
              <Link to="/admin/reclamacoes/catalogo">
                <ListTree aria-hidden="true" /> Catálogo
              </Link>
            </Button>
            <Button asChild className="min-h-[44px]">
              <Link to="/admin/reclamacoes/encaminhar">
                <Send aria-hidden="true" /> Encaminhar à síndica
              </Link>
            </Button>
          </>
        }
      />

      <div role="tablist" aria-label="Status" className="flex flex-wrap gap-2">
        {TABS.map((t) => (
          <button
            key={t.key}
            role="tab"
            aria-selected={tab.key === t.key}
            onClick={() => setTab(t)}
            className={`min-h-[44px] rounded-md px-4 text-sm font-medium ${
              tab.key === t.key ? 'bg-primary text-primary-foreground' : 'bg-muted'
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {loading && <p>Carregando…</p>}
      {!loading && rows.length === 0 && (
        <p className="text-muted-foreground">Nenhuma reclamação.</p>
      )}
      <ul className="grid grid-cols-1 gap-2 xl:grid-cols-2">
        {rows.map(({ complaint: c, authorName, unitCode }) => (
          <li key={c.id}>
            <Link
              to={`/admin/reclamacoes/${c.id}`}
              className="flex min-h-[44px] flex-col gap-1 rounded-lg border p-3 hover:bg-muted/50 sm:flex-row sm:items-center sm:justify-between"
            >
              <div>
                <p className="font-medium">
                  #{c.code} {c.typeName} › {c.subtypeName} — {c.locationName}
                </p>
                <p className="text-sm text-muted-foreground">
                  {authorName ?? '—'} · {unitCode ?? '—'}
                </p>
              </div>
              <StatusBadge status={c.status} />
            </Link>
          </li>
        ))}
      </ul>
      {!loading && page.number + 1 < page.totalPages && (
        <Button
          variant="outline"
          className="min-h-[44px]"
          disabled={loadingMore}
          onClick={loadMore}
        >
          Carregar mais
        </Button>
      )}
    </Page>
  );
}
