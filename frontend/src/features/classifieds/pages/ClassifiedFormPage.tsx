import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { toast } from 'sonner';
import imageCompression from 'browser-image-compression';
import { ArrowLeft, Trash2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  createClassified,
  deleteClassifiedPhoto,
  getClassified,
  getClassifiedPhotoUrl,
  updateClassified,
  uploadClassifiedPhoto,
  type Classified,
  type ClassifiedPhoto,
  type ClassifiedStatus,
} from '../api/classifiedsApi';

const MAX_PHOTOS = 5;

const STATUS_OPTIONS: { value: ClassifiedStatus; label: string }[] = [
  { value: 'ACTIVE', label: 'Ativo' },
  { value: 'SOLD', label: 'Vendido' },
  { value: 'ARCHIVED', label: 'Arquivado' },
];

function backendMessage(e: unknown, fallback: string): string {
  return (e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? fallback;
}

export function ClassifiedFormPage() {
  const { id } = useParams<{ id: string }>();
  const isEdit = !!id;
  const navigate = useNavigate();
  const fileRef = useRef<HTMLInputElement>(null);

  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [price, setPrice] = useState('');
  const [status, setStatus] = useState<ClassifiedStatus>('ACTIVE');
  const [photos, setPhotos] = useState<ClassifiedPhoto[]>([]);
  const [photoUrls, setPhotoUrls] = useState<Record<string, string>>({});

  const [loading, setLoading] = useState(isEdit);
  const [saving, setSaving] = useState(false);
  const [uploading, setUploading] = useState(false);

  const applyClassified = (c: Classified) => {
    setTitle(c.title);
    setDescription(c.description ?? '');
    setPrice(c.price != null ? String(c.price) : '');
    setStatus(c.status);
    setPhotos(c.photos);
  };

  const loadPhotoUrls = useCallback((classifiedId: string, list: ClassifiedPhoto[]) => {
    list.forEach((photo) => {
      getClassifiedPhotoUrl(classifiedId, photo.id)
        .then((url) => setPhotoUrls((prev) => ({ ...prev, [photo.id]: url })))
        .catch(() => {
          /* ignore individual photo url failures */
        });
    });
  }, []);

  useEffect(() => {
    if (!id) return;
    let active = true;
    setLoading(true);
    setPhotoUrls({}); // estado limpo ao carregar o anúncio em edição
    getClassified(id)
      .then((c) => {
        if (!active) return;
        applyClassified(c);
        loadPhotoUrls(id, c.photos);
      })
      .catch(() => {
        if (active) toast.error('Erro ao carregar o anúncio.');
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [id, loadPhotoUrls]);

  const parsedPrice = (): number | null => {
    const trimmed = price.trim().replace(',', '.');
    if (!trimmed) return null;
    const n = Number(trimmed);
    return Number.isFinite(n) ? n : null;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim()) {
      toast.error('Informe um título.');
      return;
    }
    setSaving(true);
    try {
      const body = {
        title: title.trim(),
        description: description.trim() || undefined,
        price: parsedPrice(),
      };
      if (isEdit && id) {
        await updateClassified(id, { ...body, status });
        toast.success('Anúncio atualizado.');
        navigate(`/classificados/${id}`);
      } else {
        const created = await createClassified(body);
        toast.success('Anúncio criado. Adicione fotos, se desejar.');
        navigate(`/classificados/${created.id}/editar`, { replace: true });
      }
    } catch (err) {
      toast.error(backendMessage(err, 'Erro ao salvar o anúncio.'));
    } finally {
      setSaving(false);
    }
  };

  const handleUpload = async (file: File | null) => {
    if (!file || !id) return;
    if (photos.length >= MAX_PHOTOS) {
      toast.error(`Máximo de ${MAX_PHOTOS} fotos por anúncio.`);
      return;
    }
    setUploading(true);
    try {
      let toSend = file;
      try {
        toSend = await imageCompression(file, { maxSizeMB: 1, maxWidthOrHeight: 1920 });
      } catch {
        // compression failed; fall back to the raw file (backend validates size/type).
        toSend = file;
      }
      const photo = await uploadClassifiedPhoto(id, toSend);
      setPhotos((prev) => [...prev, photo]);
      loadPhotoUrls(id, [photo]);
      toast.success('Foto adicionada.');
    } catch (err) {
      toast.error(backendMessage(err, 'Erro ao enviar a foto.'));
    } finally {
      setUploading(false);
      if (fileRef.current) fileRef.current.value = '';
    }
  };

  const handleRemovePhoto = async (photoId: string) => {
    if (!id) return;
    try {
      await deleteClassifiedPhoto(id, photoId);
      setPhotos((prev) => prev.filter((p) => p.id !== photoId));
      setPhotoUrls((prev) => {
        const next = { ...prev };
        delete next[photoId];
        return next;
      });
      toast.success('Foto removida.');
    } catch (err) {
      toast.error(backendMessage(err, 'Erro ao remover a foto.'));
    }
  };

  if (loading) return <main className="mx-auto max-w-2xl p-4">Carregando…</main>;

  return (
    <main className="mx-auto max-w-2xl p-4 space-y-4">
      <Button asChild variant="ghost" className="min-h-[44px] px-2">
        <Link to={isEdit && id ? `/classificados/${id}` : '/classificados'}>
          <ArrowLeft aria-hidden="true" /> Voltar
        </Link>
      </Button>

      <Card>
        <CardHeader>
          <CardTitle>{isEdit ? 'Editar anúncio' : 'Novo anúncio'}</CardTitle>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} className="space-y-4" noValidate>
            <div className="space-y-2">
              <Label htmlFor="title">Título</Label>
              <Input
                id="title"
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                aria-invalid={!title.trim()}
                required
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="description">Descrição (opcional)</Label>
              <textarea
                id="description"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                rows={4}
                className="flex w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="price">Preço em R$ (opcional)</Label>
              <Input
                id="price"
                type="text"
                inputMode="decimal"
                placeholder="Ex.: 150,00"
                value={price}
                onChange={(e) => setPrice(e.target.value)}
              />
            </div>
            {isEdit && (
              <div className="space-y-2">
                <Label htmlFor="status">Status</Label>
                <select
                  id="status"
                  className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
                  value={status}
                  onChange={(e) => setStatus(e.target.value as ClassifiedStatus)}
                >
                  {STATUS_OPTIONS.map((o) => (
                    <option key={o.value} value={o.value}>
                      {o.label}
                    </option>
                  ))}
                </select>
              </div>
            )}
            <Button type="submit" disabled={saving} className="min-h-[44px] w-full">
              {saving ? 'Salvando…' : isEdit ? 'Salvar alterações' : 'Criar anúncio'}
            </Button>
          </form>
        </CardContent>
      </Card>

      {isEdit && (
        <Card>
          <CardHeader>
            <CardTitle className="text-lg">
              Fotos ({photos.length}/{MAX_PHOTOS})
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="photo">Adicionar foto</Label>
              <input
                id="photo"
                ref={fileRef}
                type="file"
                accept="image/*"
                disabled={uploading || photos.length >= MAX_PHOTOS}
                className="block w-full rounded-md border border-input bg-background px-3 py-2 text-sm disabled:cursor-not-allowed disabled:opacity-50"
                onChange={(e) => handleUpload(e.target.files?.[0] ?? null)}
              />
              {photos.length >= MAX_PHOTOS && (
                <p className="text-sm text-muted-foreground">
                  Limite de {MAX_PHOTOS} fotos atingido.
                </p>
              )}
              {uploading && <p className="text-sm text-muted-foreground">Enviando…</p>}
            </div>
            {photos.length > 0 && (
              <ul className="grid grid-cols-2 gap-3 sm:grid-cols-3">
                {photos.map((photo) => (
                  <li key={photo.id} className="space-y-2">
                    <div className="overflow-hidden rounded-lg border bg-muted">
                      {photoUrls[photo.id] ? (
                        <img
                          src={photoUrls[photo.id]}
                          alt="Foto do anúncio"
                          className="aspect-square w-full object-cover"
                          loading="lazy"
                        />
                      ) : (
                        <div className="flex aspect-square w-full items-center justify-center text-sm text-muted-foreground">
                          Carregando…
                        </div>
                      )}
                    </div>
                    <Button
                      type="button"
                      variant="destructive"
                      size="sm"
                      className="min-h-[44px] w-full"
                      onClick={() => handleRemovePhoto(photo.id)}
                    >
                      <Trash2 aria-hidden="true" /> Remover
                    </Button>
                  </li>
                ))}
              </ul>
            )}
          </CardContent>
        </Card>
      )}
    </main>
  );
}
