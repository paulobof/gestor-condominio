import { Home } from 'lucide-react';

export default function App() {
  return (
    <main className="min-h-dvh bg-background text-foreground">
      <header className="border-b border-border">
        <div className="container flex items-center gap-3 py-6">
          <Home className="text-primary" aria-hidden="true" />
          <h1 className="text-2xl md:text-3xl font-heading font-semibold tracking-tight">
            HELBOR TRILOGY HOME
          </h1>
        </div>
      </header>
      <section className="container py-10 space-y-4">
        <p className="text-muted-foreground max-w-prose">
          Bem-vindo ao portal de gestão do condomínio. O sistema está em fase de implantação.
        </p>
        <p
          data-testid="app-status"
          className="inline-flex items-center gap-2 rounded-md bg-success/10 px-3 py-1.5 text-sm font-medium text-success"
        >
          Sistema disponível
        </p>
      </section>
    </main>
  );
}
