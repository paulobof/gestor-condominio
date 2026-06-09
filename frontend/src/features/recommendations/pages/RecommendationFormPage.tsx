import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { toast } from 'sonner';
import imageCompression from 'browser-image-compression';
import { ArrowLeft, Trash2, X } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  createRecommendation,
  deleteRecommendationPhoto,
  getRecommendation,
  getRecommendationPhotoUrl,
  updateRecommendation,
  uploadRecommendationPhoto,
  type OpeningHours,
  type Recommendation,
  type RecommendationPhoto,
} from '../api/recommendationsApi';
import { searchTags, type Tag } from '../api/tagsApi';

const MAX_PHOTOS = 5;
const DAY_LABELS = ['Domingo', 'Segunda', 'Terça', 'Quarta', 'Quinta', 'Sexta', 'Sábado'];

interface HoursRow {
  opensAt: string; // "HH:mm" ou ""
  closesAt: string;
  notes: string;
}

function emptyHoursRows(): HoursRow[] {
  return DAY_LABELS.map(() => ({ opensAt: '', closesAt: '', notes: '' }));
}

function backendMessage(e: unknown, fallback: string): string {
  return (e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? fallback;
}

export function RecommendationFormPage() {
  const { id } = useParams<{ id: string }>();
  const isEdit = !!id;
  const navigate = useNavigate();
  const fileRef = useRef<HTMLInputElement>(null);

  const [serviceName, setServiceName] = useState('');
  const [professionalName, setProfessionalName] = useState('');
  const [phone, setPhone] = useState('');
  const [addressLine, setAddressLine] = useState('');
  const [priceRange, setPriceRange] = useState('');
  const [rating, setRating] = useState(''); // "" = sem nota
  const [comment, setComment] = useState('');

  // morador (apenas na criação)
  const [isResident, setIsResident] = useState(false);
  const [residentUserId, setResidentUserId] = useState('');

  // tags
  const [tagSlugs, setTagSlugs] = useState<string[]>([]);
  const [tagQuery, setTagQuery] = useState('');
  const [tagSuggestions, setTagSuggestions] = useState<Tag[]>([]);

  // horários
  const [hours, setHours] = useState<HoursRow[]>(emptyHoursRows);

  // fotos (edição)
  const [photos, setPhotos] = useState<RecommendationPhoto[]>([]);
  const [photoUrls, setPhotoUrls] = useState<Record<string, string>>({});

  const [loading, setLoading] = useState(isEdit);
  const [saving, setSaving] = useState(false);
  const [uploading, setUploading] = useState(false);

  const loadPhotoUrls = useCallback((recId: string, list: RecommendationPhoto[]) => {
    list.forEach((photo) => {
      getRecommendationPhotoUrl(recId, photo.id)
        .then((url) => setPhotoUrls((prev) => ({ ...prev, [photo.id]: url })))
        .catch(() => {
          /* ignore individual photo url failures */
        });
    });
  }, []);

  const applyRecommendation = useCallback((rec: Recommendation) => {
    setServiceName(rec.serviceName);
    setProfessionalName(rec.professionalName ?? '');
    setPhone(rec.phone ?? '');
    setAddressLine(rec.addressLine ?? '');
    setPriceRange(rec.priceRange ?? '');
    setRating(rec.rating != null ? String(rec.rating) : '');
    setComment(rec.comment ?? '');
    setIsResident(rec.isResident);
    setResidentUserId(rec.residentUserId ?? '');
    setTagSlugs(rec.tags.map((t) => t.slug));
    setPhotos(rec.photos);
    const rows = emptyHoursRows();
    rec.openingHours.forEach((oh) => {
      if (oh.dayOfWeek >= 0 && oh.dayOfWeek <= 6) {
        rows[oh.dayOfWeek] = {
          opensAt: oh.opensAt ? oh.opensAt.slice(0, 5) : '',
          closesAt: oh.closesAt ? oh.closesAt.slice(0, 5) : '',
          notes: oh.notes ?? '',
        };
      }
    });
    setHours(rows);
  }, []);

  useEffect(() => {
    if (!id) return;
    let active = true;
    setLoading(true);
    setPhotoUrls({});
    getRecommendation(id)
      .then((rec) => {
        if (!active) return;
        applyRecommendation(rec);
        loadPhotoUrls(id, rec.photos);
      })
      .catch(() => {
        if (active) toast.error('Erro ao carregar a indicação.');
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [id, applyRecommendation, loadPhotoUrls]);

  // autocomplete de tags com debounce
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  useEffect(() => {
    const q = tagQuery.trim();
    if (debounceRef.current) clearTimeout(debounceRef.current);
    if (!q) {
      setTagSuggestions([]);
      return;
    }
    debounceRef.current = setTimeout(() => {
      searchTags(q)
        .then(setTagSuggestions)
        .catch(() => setTagSuggestions([]));
    }, 250);
    return () => {
      if (debounceRef.current) clearTimeout(debounceRef.current);
    };
  }, [tagQuery]);

  const addTag = (slug: string) => {
    const normalized = slug.trim().toLowerCase();
    if (!normalized) return;
    setTagSlugs((prev) => (prev.includes(normalized) ? prev : [...prev, normalized]));
    setTagQuery('');
    setTagSuggestions([]);
  };

  const removeTag = (slug: string) => {
    setTagSlugs((prev) => prev.filter((s) => s !== slug));
  };

  const setHourField = (day: number, field: keyof HoursRow, value: string) => {
    setHours((prev) => prev.map((row, i) => (i === day ? { ...row, [field]: value } : row)));
  };

  const buildOpeningHours = (): OpeningHours[] =>
    hours
      .map((row, day) => ({ row, day }))
      .filter(({ row }) => row.opensAt || row.closesAt || row.notes.trim())
      .map(({ row, day }) => ({
        dayOfWeek: day,
        opensAt: row.opensAt || null,
        closesAt: row.closesAt || null,
        notes: row.notes.trim() || null,
      }));

  const parsedRating = (): number | null => {
    if (!rating) return null;
    const n = Number(rating);
    return Number.isFinite(n) ? n : null;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!serviceName.trim()) {
      toast.error('Informe o nome do serviço.');
      return;
    }
    if (!isEdit && isResident && !residentUserId.trim()) {
      toast.error('Informe o UUID do morador indicado.');
      return;
    }
    setSaving(true);
    try {
      const common = {
        serviceName: serviceName.trim(),
        professionalName: professionalName.trim() || undefined,
        phone: phone.trim() || undefined,
        addressLine: addressLine.trim() || undefined,
        priceRange: priceRange.trim() || undefined,
        rating: parsedRating(),
        comment: comment.trim() || undefined,
        tagSlugs,
        openingHours: buildOpeningHours(),
      };
      if (isEdit && id) {
        await updateRecommendation(id, common);
        toast.success('Indicação atualizada.');
        navigate(`/indicacoes/${id}`);
      } else {
        const created = await createRecommendation({
          ...common,
          isResident,
          residentUserId: isResident ? residentUserId.trim() : null,
        });
        toast.success('Indicação criada. Adicione fotos e horários, se desejar.');
        navigate(`/indicacoes/${created.id}/editar`, { replace: true });
      }
    } catch (err) {
      toast.error(backendMessage(err, 'Erro ao salvar a indicação.'));
    } finally {
      setSaving(false);
    }
  };

  const handleUpload = async (file: File | null) => {
    if (!file || !id) return;
    if (photos.length >= MAX_PHOTOS) {
      toast.error(`Máximo de ${MAX_PHOTOS} fotos por indicação.`);
      return;
    }
    setUploading(true);
    try {
      let toSend = file;
      try {
        toSend = await imageCompression(file, { maxSizeMB: 1, maxWidthOrHeight: 1920 });
      } catch {
        toSend = file;
      }
      const photo = await uploadRecommendationPhoto(id, toSend);
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
      await deleteRecommendationPhoto(id, photoId);
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
        <Link to={isEdit && id ? `/indicacoes/${id}` : '/indicacoes'}>
          <ArrowLeft aria-hidden="true" /> Voltar
        </Link>
      </Button>

      <Card>
        <CardHeader>
          <CardTitle>{isEdit ? 'Editar indicação' : 'Nova indicação'}</CardTitle>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} className="space-y-4" noValidate>
            <div className="space-y-2">
              <Label htmlFor="serviceName">Serviço</Label>
              <Input
                id="serviceName"
                value={serviceName}
                onChange={(e) => setServiceName(e.target.value)}
                aria-invalid={!serviceName.trim()}
                required
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="professionalName">Profissional (opcional)</Label>
              <Input
                id="professionalName"
                value={professionalName}
                onChange={(e) => setProfessionalName(e.target.value)}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="phone">Telefone (opcional)</Label>
              <Input id="phone" value={phone} onChange={(e) => setPhone(e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="addressLine">Endereço (opcional)</Label>
              <Input
                id="addressLine"
                value={addressLine}
                onChange={(e) => setAddressLine(e.target.value)}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="priceRange">Faixa de preço (opcional)</Label>
              <Input
                id="priceRange"
                value={priceRange}
                onChange={(e) => setPriceRange(e.target.value)}
                placeholder="Ex.: R$ 100–200"
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="rating">Nota (opcional)</Label>
              <select
                id="rating"
                className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
                value={rating}
                onChange={(e) => setRating(e.target.value)}
              >
                <option value="">Sem nota</option>
                {[1, 2, 3, 4, 5].map((n) => (
                  <option key={n} value={n}>
                    {n} {n === 1 ? 'estrela' : 'estrelas'}
                  </option>
                ))}
              </select>
            </div>
            <div className="space-y-2">
              <Label htmlFor="comment">Comentário (opcional)</Label>
              <textarea
                id="comment"
                value={comment}
                onChange={(e) => setComment(e.target.value)}
                rows={4}
                className="flex w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
              />
            </div>

            {!isEdit && (
              <div className="space-y-2 rounded-md border p-3">
                <label className="flex min-h-[44px] cursor-pointer items-center gap-2">
                  <input
                    type="checkbox"
                    className="h-5 w-5"
                    checked={isResident}
                    onChange={(e) => setIsResident(e.target.checked)}
                  />
                  <span className="text-sm font-medium">É morador do condomínio?</span>
                </label>
                {isResident && (
                  <div className="space-y-2">
                    <Label htmlFor="residentUserId">UUID do morador indicado</Label>
                    <Input
                      id="residentUserId"
                      value={residentUserId}
                      onChange={(e) => setResidentUserId(e.target.value)}
                      aria-invalid={isResident && !residentUserId.trim()}
                      placeholder="00000000-0000-0000-0000-000000000000"
                    />
                    <p className="text-xs text-muted-foreground">
                      Identifica que o profissional indicado é um morador do prédio.
                    </p>
                  </div>
                )}
              </div>
            )}

            {/* Tags */}
            <div className="space-y-2">
              <Label htmlFor="tagInput">Tags</Label>
              {tagSlugs.length > 0 && (
                <div className="flex flex-wrap gap-1">
                  {tagSlugs.map((slug) => (
                    <span
                      key={slug}
                      className="inline-flex items-center gap-1 rounded-full bg-muted px-2 py-0.5 text-xs font-medium"
                    >
                      {slug}
                      <button
                        type="button"
                        aria-label={`Remover tag ${slug}`}
                        className="rounded-full p-0.5 hover:bg-background"
                        onClick={() => removeTag(slug)}
                      >
                        <X className="h-3 w-3" aria-hidden="true" />
                      </button>
                    </span>
                  ))}
                </div>
              )}
              <div className="flex gap-2">
                <Input
                  id="tagInput"
                  value={tagQuery}
                  onChange={(e) => setTagQuery(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') {
                      e.preventDefault();
                      addTag(tagQuery);
                    }
                  }}
                  placeholder="Digite e pressione Enter"
                  autoComplete="off"
                />
                <Button
                  type="button"
                  variant="secondary"
                  className="min-h-[44px]"
                  onClick={() => addTag(tagQuery)}
                >
                  Adicionar
                </Button>
              </div>
              {tagSuggestions.length > 0 && (
                <ul className="rounded-md border bg-popover">
                  {tagSuggestions.map((t) => (
                    <li key={t.id}>
                      <button
                        type="button"
                        className="flex min-h-[44px] w-full items-center px-3 py-2 text-left text-sm hover:bg-accent"
                        onClick={() => addTag(t.slug)}
                      >
                        {t.label}
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </div>

            {/* Horários */}
            <div className="space-y-2">
              <Label>Horário de funcionamento (opcional)</Label>
              <div className="space-y-2">
                {DAY_LABELS.map((label, day) => (
                  <div key={day} className="flex flex-wrap items-center gap-2">
                    <span className="w-20 text-sm font-medium">{label}</span>
                    <input
                      type="time"
                      aria-label={`Abre em ${label}`}
                      value={hours[day].opensAt}
                      onChange={(e) => setHourField(day, 'opensAt', e.target.value)}
                      className="h-10 rounded-md border border-input bg-background px-2 text-sm"
                    />
                    <span className="text-sm">–</span>
                    <input
                      type="time"
                      aria-label={`Fecha em ${label}`}
                      value={hours[day].closesAt}
                      onChange={(e) => setHourField(day, 'closesAt', e.target.value)}
                      className="h-10 rounded-md border border-input bg-background px-2 text-sm"
                    />
                    <input
                      type="text"
                      aria-label={`Observações ${label}`}
                      placeholder="Obs."
                      value={hours[day].notes}
                      onChange={(e) => setHourField(day, 'notes', e.target.value)}
                      className="h-10 min-w-[6rem] flex-1 rounded-md border border-input bg-background px-2 text-sm"
                    />
                  </div>
                ))}
              </div>
            </div>

            <Button type="submit" disabled={saving} className="min-h-[44px] w-full">
              {saving ? 'Salvando…' : isEdit ? 'Salvar alterações' : 'Criar indicação'}
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
                          alt="Foto da indicação"
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
