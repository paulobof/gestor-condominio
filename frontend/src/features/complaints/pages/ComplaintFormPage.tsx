import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { toast } from 'sonner';
import imageCompression from 'browser-image-compression';
import { ArrowLeft, Trash2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  backendMessage,
  createComplaint,
  deleteComplaintPhoto,
  getCatalog,
  getComplaint,
  getComplaintPhotoUrl,
  updateComplaint,
  uploadComplaintPhoto,
  type Catalog,
  type ComplaintPhoto,
} from '../api/complaintsApi';
import { CatalogSelects, type CatalogSelection } from '../components/CatalogSelects';

const MAX_PHOTOS = 3;
const MAX_BYTES = 1_048_576;
const DESC_MIN = 10;
const DESC_MAX = 2000;

async function compress(file: File): Promise<File> {
  try {
    return await imageCompression(file, { maxSizeMB: 0.95, maxWidthOrHeight: 1920 });
  } catch {
    return file; // backend valida tamanho/tipo
  }
}

export function ComplaintFormPage() {
  const { id } = useParams<{ id: string }>();
  const isEdit = !!id;
  const navigate = useNavigate();
  const fileRef = useRef<HTMLInputElement>(null);

  const [catalog, setCatalog] = useState<Catalog | null>(null);
  const [selection, setSelection] = useState<CatalogSelection>({
    typeId: '',
    subtypeId: '',
    locationId: '',
  });
  const [fallback, setFallback] = useState<Record<string, string>>({});
  const [locationDetail, setLocationDetail] = useState('');
  const [description, setDescription] = useState('');
  const [photos, setPhotos] = useState<ComplaintPhoto[]>([]);
  const [photoUrls, setPhotoUrls] = useState<Record<string, string>>({});
  const [pending, setPending] = useState<{ file: File; url: string }[]>([]);
  const [saving, setSaving] = useState(false);
  const [uploading, setUploading] = useState(false);

  useEffect(() => {
    getCatalog()
      .then(setCatalog)
      .catch(() => toast.error('Erro ao carregar as opções da reclamação.'));
  }, []);

  useEffect(() => {
    if (!id) return;
    getComplaint(id)
      .then((c) => {
        setSelection({ typeId: c.typeId, subtypeId: c.subtypeId, locationId: c.locationId });
        setFallback({
          typeId: c.typeId,
          typeName: c.typeName,
          subtypeId: c.subtypeId,
          subtypeName: c.subtypeName,
          locationId: c.locationId,
          locationName: c.locationName,
        });
        setLocationDetail(c.locationDetail ?? '');
        setDescription(c.description);
        setPhotos(c.photos);
        c.photos.forEach((p) =>
          getComplaintPhotoUrl(id, p.id)
            .then((url) => setPhotoUrls((prev) => ({ ...prev, [p.id]: url })))
            .catch(() => {})
        );
      })
      .catch(() => toast.error('Erro ao carregar a reclamação.'));
  }, [id]);

  const photoCount = isEdit ? photos.length : pending.length;

  const pickPhoto = async (raw: File | null) => {
    if (!raw) return;
    if (fileRef.current) fileRef.current.value = '';
    if (photoCount >= MAX_PHOTOS) {
      toast.error(`Máximo de ${MAX_PHOTOS} fotos por reclamação.`);
      return;
    }
    const file = await compress(raw);
    if (file.size > MAX_BYTES) {
      toast.error('A foto ficou maior que 1MB mesmo comprimida. Tente outra.');
      return;
    }
    if (!isEdit || !id) {
      setPending((prev) => [...prev, { file, url: URL.createObjectURL(file) }]);
      return;
    }
    setUploading(true);
    try {
      const photo = await uploadComplaintPhoto(id, file);
      setPhotos((prev) => [...prev, photo]);
      getComplaintPhotoUrl(id, photo.id)
        .then((url) => setPhotoUrls((prev) => ({ ...prev, [photo.id]: url })))
        .catch(() => {});
    } catch (err) {
      toast.error(backendMessage(err, 'Erro ao enviar a foto.'));
    } finally {
      setUploading(false);
    }
  };

  const removePending = (index: number) => {
    setPending((prev) => {
      const next = [...prev];
      const [removed] = next.splice(index, 1);
      if (removed) URL.revokeObjectURL(removed.url);
      return next;
    });
  };

  const removePhoto = async (photoId: string) => {
    if (!id) return;
    try {
      await deleteComplaintPhoto(id, photoId);
      setPhotos((prev) => prev.filter((p) => p.id !== photoId));
    } catch (err) {
      toast.error(backendMessage(err, 'Erro ao remover a foto.'));
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selection.typeId || !selection.subtypeId || !selection.locationId) {
      toast.error('Escolha tipo, subtipo e local.');
      return;
    }
    const desc = description.trim();
    if (desc.length < DESC_MIN) {
      toast.error('A descrição precisa ter pelo menos 10 caracteres.');
      return;
    }
    const body = {
      ...selection,
      locationDetail: locationDetail.trim() || null,
      description: desc,
    };
    setSaving(true);
    try {
      if (isEdit && id) {
        await updateComplaint(id, body);
        toast.success('Reclamação atualizada.');
        navigate(`/reclamacoes/${id}`);
        return;
      }
      const created = await createComplaint(body);
      let failed = 0;
      for (const p of pending) {
        try {
          await uploadComplaintPhoto(created.id, p.file);
        } catch {
          failed += 1;
        }
        URL.revokeObjectURL(p.url);
      }
      if (failed > 0) {
        toast.error(
          `Reclamação enviada, mas ${failed} foto(s) não foram enviadas. Adicione na edição.`
        );
      } else {
        toast.success('Reclamação enviada.');
      }
      navigate(`/reclamacoes/${created.id}`, { replace: true });
    } catch (err) {
      toast.error(backendMessage(err, 'Erro ao salvar a reclamação.'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <main className="mx-auto max-w-2xl space-y-4 p-4">
      <Button asChild variant="ghost" className="min-h-[44px] px-2">
        <Link to={isEdit && id ? `/reclamacoes/${id}` : '/reclamacoes'}>
          <ArrowLeft aria-hidden="true" /> Voltar
        </Link>
      </Button>

      <Card>
        <CardHeader>
          <CardTitle>{isEdit ? 'Editar reclamação' : 'Nova reclamação'}</CardTitle>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} className="space-y-4" noValidate>
            {catalog ? (
              <CatalogSelects
                catalog={catalog}
                value={selection}
                onChange={setSelection}
                fallback={fallback}
              />
            ) : (
              <p className="text-sm text-muted-foreground">Carregando opções…</p>
            )}
            <div className="space-y-2">
              <Label htmlFor="location-detail">Complemento do local (opcional)</Label>
              <Input
                id="location-detail"
                maxLength={200}
                placeholder="Ex.: 5º andar, perto do elevador"
                value={locationDetail}
                onChange={(e) => setLocationDetail(e.target.value)}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="description">Descrição</Label>
              <textarea
                id="description"
                rows={5}
                maxLength={DESC_MAX}
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                className="flex w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
              />
              <p className="text-right text-xs text-muted-foreground">
                {description.length}/{DESC_MAX}
              </p>
            </div>
            <Button type="submit" disabled={saving} className="min-h-[44px] w-full">
              {saving ? 'Salvando…' : isEdit ? 'Salvar alterações' : 'Enviar reclamação'}
            </Button>
          </form>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-lg">
            Fotos ({photoCount}/{MAX_PHOTOS})
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          <p className="text-sm text-muted-foreground">
            Fotografe só o problema: não inclua pessoas, placas de carro ou documentos.
          </p>
          <Label htmlFor="photo">Adicionar foto</Label>
          <input
            id="photo"
            ref={fileRef}
            type="file"
            accept="image/jpeg,image/png,image/webp"
            disabled={uploading || photoCount >= MAX_PHOTOS}
            className="block min-h-[44px] w-full rounded-md border border-input bg-background px-3 py-2 text-sm disabled:cursor-not-allowed disabled:opacity-50"
            onChange={(e) => pickPhoto(e.target.files?.[0] ?? null)}
          />
          {photoCount >= MAX_PHOTOS && (
            <p className="text-sm text-muted-foreground">Limite de {MAX_PHOTOS} fotos atingido.</p>
          )}
          {uploading && <p className="text-sm text-muted-foreground">Enviando…</p>}
          <ul className="grid grid-cols-3 gap-2">
            {(isEdit
              ? photos.map((p) => ({
                  key: p.id,
                  url: photoUrls[p.id],
                  onRemove: () => removePhoto(p.id),
                }))
              : pending.map((p, i) => ({
                  key: p.url + i,
                  url: p.url,
                  onRemove: () => removePending(i),
                }))
            ).map((p, i) => (
              <li key={p.key} className="space-y-1">
                <div className="aspect-square overflow-hidden rounded-md border bg-muted">
                  {p.url && (
                    <img src={p.url} alt={`Foto ${i + 1}`} className="h-full w-full object-cover" />
                  )}
                </div>
                <Button
                  type="button"
                  variant="outline"
                  className="min-h-[44px] w-full"
                  onClick={p.onRemove}
                  aria-label={`Remover foto ${i + 1}`}
                >
                  <Trash2 aria-hidden="true" />
                </Button>
              </li>
            ))}
          </ul>
        </CardContent>
      </Card>
    </main>
  );
}
