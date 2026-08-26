import { createContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import axios from 'axios';

export type FeatureMap = Record<string, boolean>;

interface FeaturesState {
  /** Módulo ligado neste ambiente. Sem resposta ainda, assume ligado — o padrão é rodar. */
  enabled: (name: string) => boolean;
  loading: boolean;
}

export const FeaturesContext = createContext<FeaturesState | null>(null);

const baseUrl = () => (import.meta.env.VITE_API_BASE_URL ?? '/api') as string;

/**
 * Carrega uma vez o que está ligado no ambiente (`GET /api/features`, público) para que o menu e a
 * tela de login não ofereçam caminho que o backend responde 404.
 *
 * O padrão é rodar: módulo desligado é a exceção, declarada no ambiente. Por isso o que ainda não
 * chegou — ou não chegou por falha de rede — conta como ligado, igual ao backend. Esconder o app
 * inteiro porque uma chamada falhou seria pior do que um link que eventualmente dá 404.
 */
export function FeaturesProvider({ children }: { children: ReactNode }) {
  const [features, setFeatures] = useState<FeatureMap | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    axios
      .get<FeatureMap>(`${baseUrl()}/features`)
      .then((r) => {
        if (!cancelled) setFeatures(r.data ?? null);
      })
      .catch(() => {
        // Sem resposta: mantém null, que significa "assume tudo ligado".
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const value = useMemo<FeaturesState>(
    () => ({ enabled: (name: string) => features === null || features[name] === true, loading }),
    [features, loading]
  );

  return <FeaturesContext.Provider value={value}>{children}</FeaturesContext.Provider>;
}
