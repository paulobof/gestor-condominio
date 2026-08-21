import { createContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import axios from 'axios';

export type FeatureMap = Record<string, boolean>;

interface FeaturesState {
  /** Módulo ligado neste ambiente. Enquanto carrega, responde `false` — o menu só cresce. */
  enabled: (name: string) => boolean;
  loading: boolean;
}

export const FeaturesContext = createContext<FeaturesState | null>(null);

const baseUrl = () => (import.meta.env.VITE_API_BASE_URL ?? '/api') as string;

/**
 * Carrega uma vez o que está ligado no ambiente (`GET /api/features`, público) para que o menu e a
 * tela de login não ofereçam caminho que o backend responde 404.
 *
 * Falha de rede degrada para "nada ligado": é melhor um menu curto do que um link quebrado.
 */
export function FeaturesProvider({ children }: { children: ReactNode }) {
  const [features, setFeatures] = useState<FeatureMap>({});
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    axios
      .get<FeatureMap>(`${baseUrl()}/features`)
      .then((r) => {
        if (!cancelled) setFeatures(r.data ?? {});
      })
      .catch(() => {
        if (!cancelled) setFeatures({});
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const value = useMemo<FeaturesState>(
    () => ({ enabled: (name: string) => features[name] === true, loading }),
    [features, loading]
  );

  return <FeaturesContext.Provider value={value}>{children}</FeaturesContext.Provider>;
}
