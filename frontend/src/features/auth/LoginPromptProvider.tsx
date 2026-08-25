import { createContext, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { X } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { LoginForm } from './LoginForm';

interface LoginPromptState {
  /**
   * Pede a entrada sem tirar a pessoa da página. `destination` é para onde ir depois de entrar —
   * use quando o clique era para abrir uma área com cadeado.
   */
  promptLogin: (options?: { reason?: string; destination?: string }) => void;
}

export const LoginPromptContext = createContext<LoginPromptState | null>(null);

/**
 * Popup de entrada. O visitante navega livre pelo que é aberto; quando esbarra numa área com
 * cadeado ou tenta participar, a conta é pedida ali mesmo, em cima do conteúdo — em vez de jogá-lo
 * numa tela de senha e fazer ele perder o lugar onde estava.
 */
export function LoginPromptProvider({ children }: { children: ReactNode }) {
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState<string | null>(null);
  const destinationRef = useRef<string | null>(null);
  const dialogRef = useRef<HTMLDivElement>(null);

  const promptLogin = useCallback((options?: { reason?: string; destination?: string }) => {
    setReason(options?.reason ?? null);
    destinationRef.current = options?.destination ?? null;
    setOpen(true);
  }, []);

  const close = useCallback(() => {
    setOpen(false);
    setReason(null);
    destinationRef.current = null;
  }, []);

  // Ao abrir, o foco entra no diálogo (primeiro campo) — é o que leitor de tela e teclado esperam
  // de um modal, e evita o autoFocus declarativo, que a regra de acessibilidade proíbe.
  useEffect(() => {
    if (!open) return;
    dialogRef.current?.querySelector<HTMLInputElement>('input')?.focus();
  }, [open]);

  // Esc fecha; enquanto aberto, o fundo não rola.
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && close();
    window.addEventListener('keydown', onKey);
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      window.removeEventListener('keydown', onKey);
      document.body.style.overflow = previousOverflow;
    };
  }, [open, close]);

  const onSuccess = useCallback(() => {
    const destination = destinationRef.current;
    close();
    if (destination) navigate(destination);
  }, [close, navigate]);

  const value = useMemo<LoginPromptState>(() => ({ promptLogin }), [promptLogin]);

  return (
    <LoginPromptContext.Provider value={value}>
      {children}
      {open && (
        <div className="fixed inset-0 z-[60] flex items-center justify-center p-4">
          <button
            type="button"
            aria-label="Fechar"
            tabIndex={-1}
            className="absolute inset-0 bg-black/50"
            onClick={close}
          />
          <div
            ref={dialogRef}
            role="dialog"
            aria-modal="true"
            aria-labelledby="login-prompt-title"
            className="relative z-10 w-full max-w-sm rounded-xl border border-border bg-card p-5 shadow-lg"
          >
            <Button
              type="button"
              variant="ghost"
              size="sm"
              aria-label="Fechar"
              className="absolute right-2 top-2 h-8 w-8 p-0"
              onClick={close}
            >
              <X className="h-4 w-4" />
            </Button>
            <h2 id="login-prompt-title" className="mb-1 font-heading text-lg font-semibold">
              Entrar
            </h2>
            <p className="mb-4 text-sm text-muted-foreground">
              {reason ?? 'Esta área é para quem tem conta.'}
            </p>
            <LoginForm onSuccess={onSuccess} />
          </div>
        </div>
      )}
    </LoginPromptContext.Provider>
  );
}
