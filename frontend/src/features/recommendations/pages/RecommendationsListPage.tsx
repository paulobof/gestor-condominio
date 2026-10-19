import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { toast } from 'sonner';
import { useAuth } from '@/features/auth/useAuth';
import { Page, PageHeader } from '@/components/layout/Page';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { listRecommendations, type Recommendation } from '../api/recommendationsApi';
import { searchTags, type Tag } from '../api/tagsApi';

/**
 * Média de avaliações formatada em pt-BR com uma casa decimal ("4,3"), ou `null` sem
 * avaliações — nunca "0,0", que sugeriria nota péssima em vez de ausência de avaliações.
 */
function formatAverageRating(r: Pick<Recommendation, 'reviewCount' | 'ratingSum'>): string | null {
  if (r.reviewCount === 0) return null;
  return (r.ratingSum / r.reviewCount).toLocaleString('pt-BR', {
    minimumFractionDigits: 1,
    maximumFractionDigits: 1,
  });
}

export function RecommendationsListPage() {
  const { user } = useAuth();
  const [search, setSearch] = useState('');
  const [tag, setTag] = useState(''); // slug aplicado ao filtro
  const [tagQuery, setTagQuery] = useState(''); // texto digitado no campo de autocomplete
  const [tagSuggestions, setTagSuggestions] = useState<Tag[]>([]);
  // Busca de sugestões concluída para o texto atual (distingue "carregando" de "nenhuma tag").
  const [tagSearched, setTagSearched] = useState(false);

  const [items, setItems] = useState<Recommendation[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    setLoading(true);
    listRecommendations({
      tag: tag || undefined,
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
  }, [tag, search]);

  // autocomplete de tags com debounce simples
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  useEffect(() => {
    const q = tagQuery.trim();
    if (debounceRef.current) clearTimeout(debounceRef.current);
    setTagSearched(false);
    if (!q) {
      setTagSuggestions([]);
      return;
    }
    debounceRef.current = setTimeout(() => {
      searchTags(q)
        .then(setTagSuggestions)
        .catch(() => setTagSuggestions([]))
        .finally(() => setTagSearched(true));
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
    <Page variant="list">
      <PageHeader
        title="Indicações"
        accent="orange"
        actions={
          /* Indicar exige conta: visitante só navega. */
          user && (
            <Button asChild className="min-h-[44px]">
              <Link to="/indicacoes/nova">Nova indicação</Link>
            </Button>
          )
        }
      />

      <div className="space-y-4 lg:grid lg:grid-cols-2 lg:items-start lg:gap-4 lg:space-y-0">
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
              onKeyDown={(e) => {
                // Enter aplica a primeira sugestão: digitar a tag e confirmar já filtra.
                if (e.key === 'Enter' && tagSuggestions.length > 0) {
                  e.preventDefault();
                  pickTag(tagSuggestions[0]);
                }
              }}
            />
            {tag && (
              <Button type="button" variant="secondary" className="min-h-[44px]" onClick={clearTag}>
                Limpar
              </Button>
            )}
          </div>
          {!tag && tagSearched && tagQuery.trim() && tagSuggestions.length === 0 && (
            <p className="text-sm text-muted-foreground">Nenhuma tag encontrada.</p>
          )}
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
      </div>

      {loading ? (
        <p className="text-muted-foreground">Carregando…</p>
      ) : items.length === 0 ? (
        <p className="text-muted-foreground">Nenhuma indicação.</p>
      ) : (
        <ul className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-2 xl:grid-cols-3">
          {items.map((r) => {
            const average = formatAverageRating(r);
            return (
              <li key={r.id}>
                <Link
                  to={`/indicacoes/${r.id}`}
                  className="block h-full rounded-lg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
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
                        {average != null ? (
                          <span className="text-sm font-medium">
                            {`★ ${average} · ${r.reviewCount} ${r.reviewCount === 1 ? 'avaliação' : 'avaliações'}`}
                          </span>
                        ) : (
                          <span className="text-sm text-muted-foreground">Sem avaliações</span>
                        )}
                        {r.isResident && (
                          <span className="rounded-full bg-primary/10 px-2 py-0.5 text-xs font-medium text-primary">
                            Mora aqui
                          </span>
                        )}
                        {r.likeCount > 0 && (
                          <span
                            className="text-xs text-muted-foreground"
                            aria-label={`${r.likeCount} curtidas`}
                          >
                            👍 {r.likeCount}
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
            );
          })}
        </ul>
      )}
    </Page>
  );
}
