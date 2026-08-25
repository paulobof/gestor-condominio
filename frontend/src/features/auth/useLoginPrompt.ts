import { useContext } from 'react';
import { LoginPromptContext } from './LoginPromptProvider';

/**
 * Pede a entrada em popup. Fora do provider (testes de unidade) vira no-op, para nenhum componente
 * quebrar só por não montar a árvore inteira.
 */
export function useLoginPrompt() {
  return useContext(LoginPromptContext) ?? { promptLogin: () => {} };
}
