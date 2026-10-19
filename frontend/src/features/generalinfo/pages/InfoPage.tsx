import { useEffect, useState } from 'react';
import { Page, PageHeader } from '@/components/layout/Page';
import { Link } from 'react-router-dom';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { useAuth } from '@/features/auth/useAuth';
import { RichTextView } from '@/components/richtext/RichTextView';
import { listSections, type InfoSection } from '../api/generalInfoApi';

export function InfoPage() {
  const { user } = useAuth();
  const canManage = !!user && user.authorities.includes('INFO_MANAGE');
  const [items, setItems] = useState<InfoSection[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    setLoading(true);
    listSections()
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

  return (
    <Page variant="reading">
      <PageHeader
        title="Informações"
        accent="blue"
        actions={
          canManage && (
            <Button asChild className="min-h-[44px]">
              <Link to="/informacoes/gerenciar">Gerenciar</Link>
            </Button>
          )
        }
      />

      {loading ? (
        <p className="text-muted-foreground">Carregando…</p>
      ) : items.length === 0 ? (
        <p className="text-muted-foreground">Nenhuma informação publicada ainda.</p>
      ) : (
        <div className="space-y-4">
          {items.map((s) => (
            <section key={s.id} className="rounded-lg border border-border p-4">
              <h2 className="mb-2 text-lg font-heading font-semibold">{s.title}</h2>
              <RichTextView html={s.body} />
            </section>
          ))}
        </div>
      )}
    </Page>
  );
}
