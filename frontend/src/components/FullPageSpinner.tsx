import { Loader2 } from 'lucide-react';

export function FullPageSpinner({ message = 'Carregando...' }: { message?: string }) {
  return (
    <div className="min-h-dvh flex flex-col items-center justify-center gap-3 bg-background text-foreground">
      <Loader2 className="w-10 h-10 animate-spin text-primary" aria-hidden="true" />
      <p className="text-muted-foreground text-sm">{message}</p>
    </div>
  );
}
