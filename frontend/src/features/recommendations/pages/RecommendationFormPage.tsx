import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { toast } from 'sonner';
import imageCompression from 'browser-image-compression';
import { ArrowLeft, Trash2, X } from 'lucide-react';
import { Page } from '@/components/layout/Page';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { useAuth } from '@/features/auth/useAuth';
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
import { ReviewForm, type ReviewFormValues } from '../components/ReviewForm';

const MAX_PHOTOS = 3;
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

/**
 * Normaliza handle do Instagram para URL completa.
 * "@joao" → "https://instagram.com/joao"
 * "https://instagram.com/joao" → inalterado
 */
function normalizeInstagramUrl(value: string): string {
  const trimmed = value.trim();
  if (!trimmed) return '';
  if (trimmed.startsWith('https://') || trimmed.startsWith('http://')) return trimmed;
  const handle = trimmed.startsWith('@') ? trimmed.slice(1) : trimmed;
  return `https://instagram.com/${handle}`;
}

/**
 * Normaliza número de telefone para URL wa.me.
 * "+55 11 99999-0000" → "https://wa.me/5511999990000"
 * "https://wa.me/..." → inalterado
 */
function normalizeWhatsappUrl(value: string): string {
  const trimmed = value.trim();
  if (!trimmed) return '';
  if (trimmed.startsWith('https://') || trimmed.startsWith('http://')) return trimmed;
  // Remove tudo exceto dígitos e +
  const digits = trimmed.replace(/[^\d+]/g, '');
  const number = digits.startsWith('+') ? digits.slice(1) : digits;
  return `https://wa.me/${number}`;
}

export function RecommendationFormPage() {
  const { id } = useParams<{ id: string }>();
  const isEdit = !!id;
  const navigate = useNavigate();
  const fileRef = useRef<HTMLInputElement>(null);
  const { user } = useAuth();

  const [serviceName, setServiceName] = useState('');
  const [professionalName, setProfessionalName] = useState('');
  const [phone, setPhone] = useState('');
  const [addressLine, setAddressLine] = useState('');
  const [priceRange, setPriceRange] = useState('');

  // links sociais
  const [instagramInput, setInstagramInput] = useState('');
  const [facebookUrl, setFacebookUrl] = useState('');
  const [whatsappInput, setWhatsappInput] = useState('');
  const [catalogUrl, setCatalogUrl] = useState('');

  // morador (apenas na criação)
  const [isResident, setIsResident] = useState(false);
  // "sou eu" = checkbox marcado sem residentUserId (null = autor)
  // "outro morador" = checkbox marcado com residentUserId preenchido (admin)
  const [residentMode, setResidentMode] = useState<'self' | 'other'>('self');
  const [residentUserId, setResidentUserId] = useState('');

  // tags
  const [tagSlugs, setTagSlugs] = useState<string[]>([]);
  const [tagQuery, setTagQuery] = useState('');
  const [tagSuggestions, setTagSuggestions] = useState<Tag[]>([]);

  // horários
  const [hours, setHours] = useState<HoursRow[]>(emptyHoursRows);

  // fotos
  const [photos, setPhotos] = useState<RecommendationPhoto[]>([]);
  const [photoUrls, setPhotoUrls] = useState<Record<string, string>>({});
  // Fotos selecionadas na criação (sem id ainda): enviadas após criar a indicação.
  const [pending, setPending] = useState<{ file: File; url: string }[]>([]);

  const [loading, setLoading] = useState(isEdit);
  const [saving, setSaving] = useState(false);
  const [uploading, setUploading] = useState(false);

  // O utilizador logado é morador (tem unitId)?
  const userIsResident = !!(user as { unitId?: string | null } | null)?.unitId;

  // Precisa informar o UUID do morador indicado quando: marcou "é morador" e
  // (não é morador logado — ex.: admin — ou escolheu "outro morador").
  const needsResidentUuid = isResident && (!userIsResident || residentMode === 'other');

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
    setIsResident(rec.isResident);
    setResidentUserId(rec.residentUserId ?? '');
    setInstagramInput(rec.instagramUrl ?? '');
    setFacebookUrl(rec.facebookUrl ?? '');
    setWhatsappInput(rec.whatsappUrl ?? '');
    setCatalogUrl(rec.catalogUrl ?? '');
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

  // Mostra o formulário de avaliação embutido só na criação e só para quem tem unidade — é regra
  // de domínio do backend: autor com unidade manda sentiment/rating/text, sem unidade não manda
  // nada (a indicação nasce sem avaliação). Edição nunca envia avaliação (o PUT nem aceita).
  const showReviewSection = !isEdit && userIsResident;

  const validateCommonFields = (): boolean => {
    if (!serviceName.trim()) {
      toast.error('Informe o nome do serviço.');
      return false;
    }
    // Validação de resident: se precisa do UUID do morador e está em branco
    if (!isEdit && needsResidentUuid && !residentUserId.trim()) {
      toast.error('Informe o UUID do morador indicado.');
      return false;
    }
    return true;
  };

  const buildCommonBody = () => {
    const instagramUrlFinal = instagramInput.trim() ? normalizeInstagramUrl(instagramInput) : null;
    const whatsappUrlFinal = whatsappInput.trim() ? normalizeWhatsappUrl(whatsappInput) : null;
    return {
      serviceName: serviceName.trim(),
      professionalName: professionalName.trim() || undefined,
      phone: phone.trim() || undefined,
      addressLine: addressLine.trim() || undefined,
      priceRange: priceRange.trim() || undefined,
      tagSlugs,
      openingHours: buildOpeningHours(),
      instagramUrl: instagramUrlFinal,
      facebookUrl: facebookUrl.trim() || null,
      whatsappUrl: whatsappUrlFinal,
      catalogUrl: catalogUrl.trim() || null,
    };
  };

  const submitCreate = async (review: ReviewFormValues | null) => {
    // Para criação: resolver residentUserId baseado no modo.
    // "sou eu" (morador logado, sem UUID) → null: backend resolve pelo JWT.
    const resolvedResidentUserId = needsResidentUuid ? residentUserId.trim() || null : null;

    const created = await createRecommendation({
      ...buildCommonBody(),
      isResident,
      residentUserId: resolvedResidentUserId,
      ...(review ?? {}),
    });
    await uploadPending(created.id);
    navigate(`/indicacoes/${created.id}`, { replace: true });
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!validateCommonFields()) return;
    setSaving(true);
    try {
      if (isEdit && id) {
        await updateRecommendation(id, buildCommonBody());
        toast.success('Indicação atualizada.');
        navigate(`/indicacoes/${id}`);
      } else {
        await submitCreate(null);
      }
    } catch (err) {
      toast.error(backendMessage(err, 'Erro ao salvar a indicação.'));
    } finally {
      setSaving(false);
    }
  };

  // Chamado pelo ReviewForm embutido (só na criação, com unidade): junta a avaliação aos demais
  // campos e cria tudo numa única chamada — não há duas submissões.
  const handleReviewSubmit = async (values: ReviewFormValues) => {
    if (!validateCommonFields()) return;
    setSaving(true);
    try {
      await submitCreate(values);
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

  const compress = async (file: File): Promise<File> => {
    try {
      return await imageCompression(file, { maxSizeMB: 1, maxWidthOrHeight: 1920 });
    } catch {
      return file; // backend valida tamanho/tipo
    }
  };

  // Modo criação: apenas guarda o arquivo (com preview) para enviar após criar.
  const stagePhoto = (file: File | null) => {
    if (!file) return;
    if (pending.length >= MAX_PHOTOS) {
      toast.error(`Máximo de ${MAX_PHOTOS} fotos por indicação.`);
      return;
    }
    setPending((prev) => [...prev, { file, url: URL.createObjectURL(file) }]);
    if (fileRef.current) fileRef.current.value = '';
  };

  const removePending = (index: number) => {
    setPending((prev) => {
      const next = [...prev];
      const [removed] = next.splice(index, 1);
      if (removed) URL.revokeObjectURL(removed.url);
      return next;
    });
  };

  const uploadPending = async (recId: string) => {
    if (pending.length === 0) {
      toast.success('Indicação criada.');
      return;
    }
    let failed = 0;
    for (const p of pending) {
      try {
        await uploadRecommendationPhoto(recId, await compress(p.file));
      } catch {
        failed += 1;
      }
      URL.revokeObjectURL(p.url);
    }
    if (failed > 0) {
      toast.error(
        `Indicação criada, mas ${failed} foto(s) não puderam ser enviadas. Adicione na edição.`
      );
    } else {
      toast.success('Indicação criada.');
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

  if (loading) return <Page variant="form">Carregando…</Page>;

  return (
    <Page variant="form">
      <Button asChild variant="ghost" className="-ml-2 min-h-[44px] px-2">
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
              <Label htmlFor="whatsappInput">WhatsApp (número, opcional)</Label>
              <Input
                id="whatsappInput"
                value={whatsappInput}
                onChange={(e) => setWhatsappInput(e.target.value)}
                placeholder="+55 11 99999-0000"
              />
              <p className="text-xs text-muted-foreground">
                Vira um link wa.me. Você também pode informar a URL completa.
              </p>
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
            {/* Links sociais */}
            <div className="space-y-3 rounded-md border p-3">
              <p className="text-sm font-medium">Links (opcionais)</p>
              <div className="space-y-2">
                <Label htmlFor="instagramInput">Instagram</Label>
                <Input
                  id="instagramInput"
                  value={instagramInput}
                  onChange={(e) => setInstagramInput(e.target.value)}
                  placeholder="@usuario ou https://instagram.com/usuario"
                />
                <p className="text-xs text-muted-foreground">Informe @handle ou URL completa.</p>
              </div>
              <div className="space-y-2">
                <Label htmlFor="facebookUrl">Facebook</Label>
                <Input
                  id="facebookUrl"
                  value={facebookUrl}
                  onChange={(e) => setFacebookUrl(e.target.value)}
                  placeholder="https://facebook.com/pagina"
                  type="url"
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="catalogUrl">Cardápio / Catálogo</Label>
                <Input
                  id="catalogUrl"
                  value={catalogUrl}
                  onChange={(e) => setCatalogUrl(e.target.value)}
                  placeholder="https://ifood.com.br/... ou link do cardápio"
                  type="url"
                />
              </div>
            </div>

            {!isEdit && (
              <div className="space-y-2 rounded-md border p-3">
                <label className="flex min-h-[44px] cursor-pointer items-center gap-2">
                  <input
                    type="checkbox"
                    className="h-5 w-5"
                    checked={isResident}
                    onChange={(e) => {
                      setIsResident(e.target.checked);
                      setResidentMode('self');
                      setResidentUserId('');
                    }}
                  />
                  <span className="text-sm font-medium">É morador do condomínio?</span>
                </label>
                {isResident && (
                  <div className="space-y-3 pl-7">
                    {userIsResident && (
                      <label className="flex min-h-[44px] cursor-pointer items-center gap-2">
                        <input
                          type="checkbox"
                          className="h-5 w-5"
                          checked={residentMode === 'self'}
                          onChange={(e) => setResidentMode(e.target.checked ? 'self' : 'other')}
                        />
                        <span className="text-sm">
                          Esta indicação é minha (morador da minha unidade)
                        </span>
                      </label>
                    )}
                    {(!userIsResident || residentMode === 'other') && (
                      <div className="space-y-2">
                        <Label htmlFor="residentUserId">UUID do morador indicado</Label>
                        <Input
                          id="residentUserId"
                          value={residentUserId}
                          onChange={(e) => setResidentUserId(e.target.value)}
                          aria-invalid={needsResidentUuid && !residentUserId.trim()}
                          placeholder="00000000-0000-0000-0000-000000000000"
                        />
                        <p className="text-xs text-muted-foreground">
                          Identifica que o profissional indicado é um morador do prédio.
                        </p>
                      </div>
                    )}
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

            {!showReviewSection && (
              <Button type="submit" disabled={saving} className="min-h-[44px] w-full">
                {saving ? 'Salvando…' : isEdit ? 'Salvar alterações' : 'Criar indicação'}
              </Button>
            )}
          </form>

          {showReviewSection && (
            <div className="mt-4 space-y-2 border-t pt-4">
              <p className="text-sm font-medium">Sua avaliação</p>
              <p className="text-xs text-muted-foreground">
                Como morador, sua indicação já nasce com a sua avaliação.
              </p>
              <ReviewForm
                onSubmit={handleReviewSubmit}
                submitting={saving}
                submitLabel="Criar indicação"
              />
            </div>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-lg">
            Fotos ({isEdit ? photos.length : pending.length}/{MAX_PHOTOS})
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
              disabled={uploading || (isEdit ? photos.length : pending.length) >= MAX_PHOTOS}
              className="block w-full rounded-md border border-input bg-background px-3 py-2 text-sm disabled:cursor-not-allowed disabled:opacity-50"
              onChange={(e) => (isEdit ? handleUpload : stagePhoto)(e.target.files?.[0] ?? null)}
            />
            {(isEdit ? photos.length : pending.length) >= MAX_PHOTOS && (
              <p className="text-sm text-muted-foreground">
                Limite de {MAX_PHOTOS} fotos atingido.
              </p>
            )}
            {uploading && <p className="text-sm text-muted-foreground">Enviando…</p>}
            {!isEdit && (
              <p className="text-sm text-muted-foreground">
                As fotos serão enviadas ao criar a indicação.
              </p>
            )}
          </div>

          {isEdit && photos.length > 0 && (
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

          {!isEdit && pending.length > 0 && (
            <ul className="grid grid-cols-2 gap-3 sm:grid-cols-3">
              {pending.map((p, idx) => (
                <li key={p.url} className="space-y-2">
                  <div className="overflow-hidden rounded-lg border bg-muted">
                    <img
                      src={p.url}
                      alt="Pré-visualização da foto"
                      className="aspect-square w-full object-cover"
                    />
                  </div>
                  <Button
                    type="button"
                    variant="destructive"
                    size="sm"
                    className="min-h-[44px] w-full"
                    onClick={() => removePending(idx)}
                  >
                    <Trash2 aria-hidden="true" /> Remover
                  </Button>
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>
    </Page>
  );
}
