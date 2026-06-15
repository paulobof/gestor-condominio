import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { toast } from 'sonner';
import {
  ArrowLeft,
  BookOpen,
  Camera,
  EyeOff,
  MessageCircle,
  Pencil,
  ThumbsDown,
  ThumbsUp,
  Trash2,
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { useAuth } from '@/features/auth/useAuth';
import {
  addRecommendationComment,
  deleteRecommendation,
  deleteRecommendationComment,
  getRecommendation,
  getRecommendationPhotoUrl,
  hideRecommendation,
  listRecommendationComments,
  voteRecommendation,
  type Recommendation,
  type RecommendationComment,
  type VoteValue,
} from '../api/recommendationsApi';

const DAY_LABELS = ['Domingo', 'Segunda', 'Terça', 'Quarta', 'Quinta', 'Sexta', 'Sábado'];

function hhmm(time: string | null): string | null {
  // API retorna "HH:mm:ss"; exibe apenas "HH:mm".
  return time ? time.slice(0, 5) : null;
}

export function RecommendationDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { user } = useAuth();
  const [rec, setRec] = useState<Recommendation | null>(null);
  const [loading, setLoading] = useState(true);
  const [photoUrls, setPhotoUrls] = useState<Record<string, string>>({});
  const [deleting, setDeleting] = useState(false);
  const [hiding, setHiding] = useState(false);
  const [voting, setVoting] = useState(false);
  const [comments, setComments] = useState<RecommendationComment[]>([]);
  const [commentText, setCommentText] = useState('');
  const [postingComment, setPostingComment] = useState(false);

  const load = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    try {
      const data = await getRecommendation(id);
      setRec(data);
    } catch {
      toast.error('Erro ao carregar a indicação.');
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    if (!id || !rec) return;
    let active = true;
    setPhotoUrls({}); // reseta ao trocar de indicação: evita URLs antigas e crescimento ilimitado
    rec.photos.forEach((photo) => {
      getRecommendationPhotoUrl(id, photo.id)
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
  }, [id, rec]);

  const canManage =
    !!user &&
    !!rec &&
    (user.id === rec.recommendedByUserId || user.authorities.includes('RECOMMENDATION_MODERATE'));
  const canModerate = !!user && user.authorities.includes('RECOMMENDATION_MODERATE');

  const handleDelete = async () => {
    if (!id) return;
    if (!window.confirm('Tem certeza que deseja excluir esta indicação?')) return;
    setDeleting(true);
    try {
      await deleteRecommendation(id);
      toast.success('Indicação excluída.');
      navigate('/indicacoes', { replace: true });
    } catch {
      toast.error('Erro ao excluir a indicação.');
      setDeleting(false);
    }
  };

  const handleHide = async () => {
    if (!id) return;
    setHiding(true);
    try {
      await hideRecommendation(id);
      toast.success('Indicação ocultada.');
      await load();
    } catch {
      toast.error('Erro ao ocultar a indicação.');
    } finally {
      setHiding(false);
    }
  };

  const loadComments = useCallback(async () => {
    if (!id) return;
    try {
      setComments(await listRecommendationComments(id));
    } catch {
      /* lista de comentários é não-crítica */
    }
  }, [id]);

  useEffect(() => {
    loadComments();
  }, [loadComments]);

  const handleVote = async (value: VoteValue) => {
    if (!id || voting) return;
    setVoting(true);
    try {
      setRec(await voteRecommendation(id, value));
    } catch {
      toast.error('Erro ao registrar o voto.');
    } finally {
      setVoting(false);
    }
  };

  const handleAddComment = async (e: FormEvent) => {
    e.preventDefault();
    if (!id || !commentText.trim()) return;
    setPostingComment(true);
    try {
      await addRecommendationComment(id, commentText.trim());
      setCommentText('');
      await loadComments();
      setRec((prev) => (prev ? { ...prev, commentCount: prev.commentCount + 1 } : prev));
    } catch {
      toast.error('Erro ao enviar o comentário.');
    } finally {
      setPostingComment(false);
    }
  };

  const handleDeleteComment = async (commentId: string) => {
    if (!id) return;
    try {
      await deleteRecommendationComment(id, commentId);
      setComments((prev) => prev.filter((c) => c.id !== commentId));
      setRec((prev) =>
        prev ? { ...prev, commentCount: Math.max(0, prev.commentCount - 1) } : prev
      );
    } catch {
      toast.error('Erro ao remover o comentário.');
    }
  };

  if (loading) return <main className="mx-auto max-w-3xl p-4">Carregando…</main>;
  if (!rec)
    return (
      <main className="mx-auto max-w-3xl p-4">
        <p className="text-muted-foreground">Indicação não encontrada.</p>
        <Button asChild variant="link" className="mt-2 px-0">
          <Link to="/indicacoes">Voltar às indicações</Link>
        </Button>
      </main>
    );

  return (
    <main className="mx-auto max-w-3xl p-4 space-y-4">
      <Button asChild variant="ghost" className="min-h-[44px] px-2">
        <Link to="/indicacoes">
          <ArrowLeft aria-hidden="true" /> Voltar
        </Link>
      </Button>

      <div className="space-y-2">
        <div className="flex flex-wrap items-center gap-2">
          <h1 className="text-2xl font-heading font-semibold">{rec.serviceName}</h1>
          {rec.isResident && (
            <span className="rounded-full bg-primary/10 px-2 py-0.5 text-xs font-medium text-primary">
              Mora aqui
            </span>
          )}
          {rec.ownerUnitCode && (
            <span className="rounded-full bg-secondary px-2 py-0.5 text-xs font-medium text-secondary-foreground">
              Unidade {rec.ownerUnitCode}
            </span>
          )}
        </div>
        {rec.professionalName && <p className="text-muted-foreground">{rec.professionalName}</p>}
        {rec.rating != null && (
          <p className="text-sm font-medium" aria-label={`Nota ${rec.rating} de 5`}>
            {'★'.repeat(rec.rating)}
            <span className="text-muted-foreground">{'★'.repeat(5 - rec.rating)}</span>
          </p>
        )}
      </div>

      <dl className="grid grid-cols-1 gap-2 text-sm">
        {rec.phone && (
          <div className="flex gap-2">
            <dt className="font-medium">Telefone:</dt>
            <dd>{rec.phone}</dd>
          </div>
        )}
        {rec.addressLine && (
          <div className="flex gap-2">
            <dt className="font-medium">Endereço:</dt>
            <dd>{rec.addressLine}</dd>
          </div>
        )}
        {rec.priceRange && (
          <div className="flex gap-2">
            <dt className="font-medium">Faixa de preço:</dt>
            <dd>{rec.priceRange}</dd>
          </div>
        )}
      </dl>

      {/* Links sociais */}
      {(rec.instagramUrl || rec.facebookUrl || rec.whatsappUrl || rec.catalogUrl) && (
        <div className="flex flex-wrap gap-3">
          {rec.instagramUrl && (
            <a
              href={rec.instagramUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex min-h-[44px] items-center gap-1.5 rounded-md border px-3 py-2 text-sm hover:bg-accent"
              aria-label="Instagram"
            >
              <Camera className="h-4 w-4" aria-hidden="true" />
              Instagram
            </a>
          )}
          {rec.facebookUrl && (
            <a
              href={rec.facebookUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex min-h-[44px] items-center gap-1.5 rounded-md border px-3 py-2 text-sm hover:bg-accent"
              aria-label="Facebook"
            >
              <ThumbsUp className="h-4 w-4" aria-hidden="true" />
              Facebook
            </a>
          )}
          {rec.whatsappUrl && (
            <a
              href={rec.whatsappUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex min-h-[44px] items-center gap-1.5 rounded-md border px-3 py-2 text-sm hover:bg-accent"
              aria-label="WhatsApp"
            >
              <MessageCircle className="h-4 w-4" aria-hidden="true" />
              WhatsApp
            </a>
          )}
          {rec.catalogUrl && (
            <a
              href={rec.catalogUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex min-h-[44px] items-center gap-1.5 rounded-md border px-3 py-2 text-sm hover:bg-accent"
              aria-label="Cardápio / Catálogo"
            >
              <BookOpen className="h-4 w-4" aria-hidden="true" />
              Cardápio / Catálogo
            </a>
          )}
        </div>
      )}

      <div className="flex items-center gap-2">
        <Button
          type="button"
          variant={rec.myVote === 'LIKE' ? 'default' : 'outline'}
          size="sm"
          className="min-h-[44px]"
          onClick={() => handleVote('LIKE')}
          disabled={voting}
          aria-pressed={rec.myVote === 'LIKE'}
          aria-label="Curtir"
        >
          <ThumbsUp className="mr-1 h-4 w-4" aria-hidden="true" /> {rec.likeCount}
        </Button>
        <Button
          type="button"
          variant={rec.myVote === 'DISLIKE' ? 'default' : 'outline'}
          size="sm"
          className="min-h-[44px]"
          onClick={() => handleVote('DISLIKE')}
          disabled={voting}
          aria-pressed={rec.myVote === 'DISLIKE'}
          aria-label="Não curtir"
        >
          <ThumbsDown className="mr-1 h-4 w-4" aria-hidden="true" /> {rec.dislikeCount}
        </Button>
      </div>

      {rec.tags.length > 0 && (
        <div className="flex flex-wrap gap-1">
          {rec.tags.map((t) => (
            <span key={t.id} className="rounded-full bg-muted px-2 py-0.5 text-xs font-medium">
              {t.label}
            </span>
          ))}
        </div>
      )}

      {rec.comment && <p className="whitespace-pre-line text-sm leading-relaxed">{rec.comment}</p>}

      {rec.openingHours.length > 0 && (
        <section className="space-y-2">
          <h2 className="text-lg font-heading font-semibold">Horário de funcionamento</h2>
          <ul className="space-y-1 text-sm">
            {[...rec.openingHours]
              .sort((a, b) => a.dayOfWeek - b.dayOfWeek)
              .map((oh) => {
                const opens = hhmm(oh.opensAt);
                const closes = hhmm(oh.closesAt);
                return (
                  <li key={oh.dayOfWeek} className="flex flex-wrap gap-2">
                    <span className="w-24 font-medium">{DAY_LABELS[oh.dayOfWeek]}</span>
                    <span>
                      {opens && closes ? `${opens}–${closes}` : 'Fechado'}
                      {oh.notes && <span className="text-muted-foreground"> ({oh.notes})</span>}
                    </span>
                  </li>
                );
              })}
          </ul>
        </section>
      )}

      {rec.photos.length > 0 && (
        <ul className="grid grid-cols-2 gap-3 sm:grid-cols-3">
          {rec.photos.map((photo) => (
            <li key={photo.id} className="overflow-hidden rounded-lg border bg-muted">
              {photoUrls[photo.id] ? (
                <img
                  src={photoUrls[photo.id]}
                  alt={`Foto da indicação ${rec.serviceName}`}
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

      <section className="space-y-3 border-t pt-4">
        <h2 className="text-lg font-heading font-semibold">
          Comentários
          {rec.commentCount > 0 && (
            <span className="text-muted-foreground"> ({rec.commentCount})</span>
          )}
        </h2>
        <form onSubmit={handleAddComment} className="flex gap-2">
          <Input
            value={commentText}
            onChange={(e) => setCommentText(e.target.value)}
            placeholder="Escreva um comentário…"
            maxLength={1000}
            aria-label="Comentário"
          />
          <Button
            type="submit"
            className="min-h-[44px]"
            disabled={postingComment || !commentText.trim()}
          >
            Enviar
          </Button>
        </form>
        {comments.length === 0 ? (
          <p className="text-sm text-muted-foreground">Seja o primeiro a comentar.</p>
        ) : (
          <ul className="space-y-3">
            {comments.map((c) => (
              <li key={c.id} className="rounded-md border p-3">
                <div className="flex items-center justify-between gap-2">
                  <span className="text-sm font-medium">{c.authorName ?? 'Usuário'}</span>
                  {(user?.id === c.authorUserId || canModerate) && (
                    <button
                      type="button"
                      onClick={() => handleDeleteComment(c.id)}
                      aria-label="Excluir comentário"
                      className="text-muted-foreground hover:text-destructive"
                    >
                      <Trash2 className="h-4 w-4" aria-hidden="true" />
                    </button>
                  )}
                </div>
                <p className="mt-1 whitespace-pre-line text-sm">{c.text}</p>
              </li>
            ))}
          </ul>
        )}
      </section>

      {(canManage || canModerate) && (
        <div className="flex flex-wrap gap-2 pt-2">
          {canManage && (
            <Button asChild className="min-h-[44px]">
              <Link to={`/indicacoes/${rec.id}/editar`}>
                <Pencil aria-hidden="true" /> Editar
              </Link>
            </Button>
          )}
          {canModerate && (
            <Button
              variant="secondary"
              className="min-h-[44px]"
              onClick={handleHide}
              disabled={hiding}
            >
              <EyeOff aria-hidden="true" /> {hiding ? 'Ocultando…' : 'Ocultar'}
            </Button>
          )}
          {canManage && (
            <Button
              variant="destructive"
              className="min-h-[44px]"
              onClick={handleDelete}
              disabled={deleting}
            >
              <Trash2 aria-hidden="true" /> {deleting ? 'Excluindo…' : 'Excluir'}
            </Button>
          )}
        </div>
      )}
    </main>
  );
}
