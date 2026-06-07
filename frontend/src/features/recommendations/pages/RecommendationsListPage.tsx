import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { listRecommendations, type Recommendation } from '../api/recommendationsApi';
import { searchTags, type Tag } from '../api/tagsApi';

export function RecommendationsListPage() {
  const [search, setSearch] = useState('');
  const [residentOnly, setResidentOnly] = useState(false);
  const [tag, setTag] = useState(''); // slug aplicado ao filtro
  const [tagQuery, setTagQuery] = useState(''); // texto digitado no campo de autocomplete
  const [tagSuggestions, setTagSuggestions] = useState<Tag[]>([]);

  const [items, setItems] = useState<Recommendation[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    setLoading(true);
    listRecommendations({
      tag: tag || undefined,
      residentOnly: residentOnly || undefined,
      search: search.trim() || undefined,
    })
      .then((p) => {
        if (active) setItems(p.content);
      })
      .catch(() => {
        if (active) toast.error('Erro ao carregar indicações.');
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [tag, residentOnly, search]);

  // autocomplete de tags com debounce simples
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

  const pickTag = (t: Tag) => {
    setTag(t.slug);
    setTagQuery(t.label);
    setTagSuggestions([]);
  };

  const clearTag = () => {
    setTag('');
    setTagQuery('');
    setTagSuggestions([]);
  };

  return (
    <main className="mx-auto max-w-3xl p-4">
      <div className="mb-4 flex items-center justify-between">
        <h1 className="flex items-center gap-2 text-2xl font-heading font-semibold">
          <span
            aria-hidden="true"
            className="inline-block h-6 w-1.5 rounded-full"
            style={{ backgroundColor: 'hsl(var(--brand-orange))' }}
          />
          Indicações
        </h1>
        <Button asChild className="min-h-[44px]">
          <Link to="/indicacoes/nova">Nova indicação</Link>
        </Button>
      </div>

      <div className="mb-6 space-y-4">
        <div className="space-y-2">
          <Label htmlFor="search">Buscar</Label>
          <Input
            id="search"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Serviço, profissional…"
          />
        </div>

        <div className="space-y-2">
          <Label htmlFor="tag">Filtrar por tag</Label>
          <div className="flex gap-2">
            <Input
              id="tag"
              value={tagQuery}
              onChange={(e) => {
                setTagQuery(e.target.value);
                if (tag) setTag(''); // editar o texto limpa o slug aplicado
              }}
              placeholder="Ex.: encanador"
              autoComplete="off"
            />
            {tag && (
              <Button type="button" variant="secondary" className="min-h-[44px]" onClick={clearTag}>
                Limpar
              </Button>
            )}
          </div>
          {tagSuggestions.length > 0 && (
            <ul className="rounded-md border bg-popover">
              {tagSuggestions.map((t) => (
                <li key={t.id}>
                  <button
                    type="button"
                    className="flex min-h-[44px] w-full items-center px-3 py-2 text-left text-sm hover:bg-accent"
                    onClick={() => pickTag(t)}
                  >
                    {t.label}
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>

        <label className="flex min-h-[44px] cursor-pointer items-center gap-2">
          <input
            type="checkbox"
            className="h-5 w-5"
            checked={residentOnly}
            onChange={(e) => setResidentOnly(e.target.checked)}
          />
          <span className="text-sm font-medium">Só moradores</span>
        </label>
      </div>

      {loading ? (
        <p className="text-muted-foreground">Carregando…</p>
      ) : items.length === 0 ? (
        <p className="text-muted-foreground">Nenhuma indicação.</p>
      ) : (
        <ul className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          {items.map((r) => (
            <li key={r.id}>
              <Link
                to={`/indicacoes/${r.id}`}
                className="block rounded-lg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
              >
                <Card className="h-full transition-colors hover:bg-accent">
                  <CardHeader>
                    <CardTitle className="text-base">{r.serviceName}</CardTitle>
                  </CardHeader>
                  <CardContent className="space-y-2">
                    {r.professionalName && (
                      <p className="text-sm text-muted-foreground">{r.professionalName}</p>
                    )}
                    <div className="flex flex-wrap items-center gap-2">
                      {r.rating != null && (
                        <span className="text-sm font-medium" aria-label={`Nota ${r.rating} de 5`}>
                          {'★'.repeat(r.rating)}
                          <span className="text-muted-foreground">{'★'.repeat(5 - r.rating)}</span>
                        </span>
                      )}
                      {r.isResident && (
                        <span className="rounded-full bg-primary/10 px-2 py-0.5 text-xs font-medium text-primary">
                          Mora aqui
                        </span>
                      )}
                    </div>
                    {r.tags.length > 0 && (
                      <div className="flex flex-wrap gap-1">
                        {r.tags.map((t) => (
                          <span
                            key={t.id}
                            className="rounded-full bg-muted px-2 py-0.5 text-xs font-medium"
                          >
                            {t.label}
                          </span>
                        ))}
                      </div>
                    )}
                  </CardContent>
                </Card>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </main>
  );
}
