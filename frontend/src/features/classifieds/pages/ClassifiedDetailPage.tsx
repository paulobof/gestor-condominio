import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { toast } from 'sonner';
import { ArrowLeft, Pencil, Trash2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { useAuth } from '@/features/auth/useAuth';
import {
  deleteClassified,
  getClassified,
  getClassifiedPhotoUrl,
  type Classified,
  type ClassifiedStatus,
} from '../api/classifiedsApi';

const STATUS_LABEL: Record<ClassifiedStatus, string> = {
  ACTIVE: 'Ativo',
  SOLD: 'Vendido',
  ARCHIVED: 'Arquivado',
};

export function ClassifiedDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { user } = useAuth();
  const [c, setC] = useState<Classified | null>(null);
  const [loading, setLoading] = useState(true);
  const [photoUrls, setPhotoUrls] = useState<Record<string, string>>({});
  const [deleting, setDeleting] = useState(false);

  const load = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    try {
      const data = await getClassified(id);
      setC(data);
    } catch {
      toast.error('Erro ao carregar o anúncio.');
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    if (!id || !c) return;
    let active = true;
    setPhotoUrls({}); // reseta ao trocar de anúncio: evita URLs antigas/rotacionadas e crescimento ilimitado
    c.photos.forEach((photo) => {
      getClassifiedPhotoUrl(id, photo.id)
        .then((url) => {
          if (active) setPhotoUrls((prev) => ({ ...prev, [photo.id]: url }));
        })
        .catch(() => {
          /* ignore individual photo url failures */
        });
    });
    return () => {
      active = false;
    };
  }, [id, c]);

  const isOwnerOrMod =
    !!user &&
    !!c &&
    (user.id === c.authorUserId || user.authorities.includes('CLASSIFIED_MODERATE'));

  const handleDelete = async () => {
    if (!id) return;
    if (!window.confirm('Tem certeza que deseja excluir este anúncio?')) return;
    setDeleting(true);
    try {
      await deleteClassified(id);
      toast.success('Anúncio excluído.');
      navigate('/classificados', { replace: true });
    } catch {
      toast.error('Erro ao excluir o anúncio.');
      setDeleting(false);
    }
  };

  if (loading) return <main className="mx-auto max-w-3xl p-4">Carregando…</main>;
  if (!c)
    return (
      <main className="mx-auto max-w-3xl p-4">
        <p className="text-muted-foreground">Anúncio não encontrado.</p>
        <Button asChild variant="link" className="mt-2 px-0">
          <Link to="/classificados">Voltar aos classificados</Link>
        </Button>
      </main>
    );

  return (
    <main className="mx-auto max-w-3xl p-4 space-y-4">
      <Button asChild variant="ghost" className="min-h-[44px] px-2">
        <Link to="/classificados">
          <ArrowLeft aria-hidden="true" /> Voltar
        </Link>
      </Button>

      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="space-y-1">
          <h1 className="text-2xl font-heading font-semibold">{c.title}</h1>
          <span className="inline-block rounded-full bg-muted px-3 py-1 text-xs font-medium">
            {STATUS_LABEL[c.status]}
          </span>
        </div>
        {c.price != null && (
          <p className="text-xl font-semibold">
            {c.price.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })}
          </p>
        )}
      </div>

      {c.photos.length > 0 && (
        <ul className="grid grid-cols-2 gap-3 sm:grid-cols-3">
          {c.photos.map((photo) => (
            <li key={photo.id} className="overflow-hidden rounded-lg border bg-muted">
              {photoUrls[photo.id] ? (
                <img
                  src={photoUrls[photo.id]}
                  alt={`Foto do anúncio ${c.title}`}
                  className="aspect-square w-full object-cover"
                  loading="lazy"
                />
              ) : (
                <div className="flex aspect-square w-full items-center justify-center text-sm text-muted-foreground">
                  Carregando…
                </div>
              )}
            </li>
          ))}
        </ul>
      )}

      {c.description && (
        <p className="whitespace-pre-line text-sm leading-relaxed">{c.description}</p>
      )}

      {isOwnerOrMod && (
        <div className="flex flex-wrap gap-2 pt-2">
          <Button asChild className="min-h-[44px]">
            <Link to={`/classificados/${c.id}/editar`}>
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
