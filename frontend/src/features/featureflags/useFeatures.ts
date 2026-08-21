import { useContext } from 'react';
import { FeaturesContext } from './FeaturesProvider';

/**
 * Estado das feature flags do ambiente. Fora do provider (testes de unidade que não montam a
 * árvore inteira) devolve "tudo ligado" — o gate é de menu, não de segurança; quem barra de
 * verdade é o backend, que responde 404 no módulo desligado.
 */
export function useFeatures() {
  const ctx = useContext(FeaturesContext);
  return ctx ?? { enabled: () => true, loading: false };
}
