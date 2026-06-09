import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { toast } from 'sonner';
import { ArrowLeft, Pencil, Trash2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { useAuth } from '@/features/auth/useAuth';
import { deleteAnnouncement, getAnnouncement, type Announcement } from '../api/announcementsApi';

export function AnnouncementDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { user } = useAuth();
  const [a, setA] = useState<Announcement | null>(null);
  const [loading, setLoading] = useState(true);
  const [deleting, setDeleting] = useState(false);

  const load = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    try {
      setA(await getAnnouncement(id));
    } catch {
      toast.error('Erro ao carregar o aviso.');
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    load();
  }, [load]);

  const canManage = !!user && user.authorities.includes('ANNOUNCEMENT_MANAGE');

  const handleDelete = async () => {
    if (!id) return;
    if (!window.confirm('Tem certeza que deseja excluir este aviso?')) return;
    setDeleting(true);
    try {
      await deleteAnnouncement(id);
      toast.success('Aviso excluído.');
      navigate('/avisos', { replace: true });
    } catch {
      toast.error('Erro ao excluir o aviso.');
      setDeleting(false);
    }
  };

  if (loading) return <main className="mx-auto max-w-3xl p-4">Carregando…</main>;
  if (!a)
    return (
      <main className="mx-auto max-w-3xl p-4">
        <p className="text-muted-foreground">Aviso não encontrado.</p>
        <Button asChild variant="link" className="mt-2 px-0">
          <Link to="/avisos">Voltar ao mural</Link>
        </Button>
      </main>
    );

  return (
    <main className="mx-auto max-w-3xl p-4 space-y-4">
      <Button asChild variant="ghost" className="min-h-[44px] px-2">
        <Link to="/avisos">
          <ArrowLeft aria-hidden="true" /> Voltar
        </Link>
      </Button>

      <h1 className="text-2xl font-heading font-semibold">{a.title}</h1>

      <p className="whitespace-pre-line text-sm leading-relaxed">{a.body}</p>

      {canManage && (
        <div className="flex flex-wrap gap-2 pt-2">
          <Button asChild className="min-h-[44px]">
            <Link to={`/avisos/${a.id}/editar`}>
              <Pencil aria-hidden="true" /> Editar
            </Link>
          </Button>
          <Button
            variant="destructive"
            className="min-h-[44px]"
            onClick={handleDelete}
            disabled={deleting}
          >
            <Trash2 aria-hidden="true" /> {deleting ? 'Excluindo…' : 'Excluir'}
          </Button>
        </div>
      )}
    </main>
  );
}
