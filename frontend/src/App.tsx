import { type CSSProperties } from 'react';
import { Link } from 'react-router-dom';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { useAuth } from '@/features/auth/useAuth';
import { brandColor as hsl, visibleSections } from '@/components/layout/navigation';

export default function App() {
  const { user } = useAuth();
  // Mesma lista e mesma regra do menu lateral: a home mostra tudo o que o menu mostra.
  const sections = visibleSections(user, (item) => !item.sidebarOnly);

  return (
    <section className="container space-y-6 py-6">
      <div>
        <h1 className="font-heading text-2xl font-semibold">
          {user ? `Olá, ${user.greetingName || user.fullName} 👋` : 'HELBOR TRILOGY HOME'}
        </h1>
        <p className="text-muted-foreground">
          {user ? 'Escolha uma área do portal.' : 'Veja o que está acontecendo no condomínio.'}
        </p>
      </div>

      {sections.map(({ section, title, items }) => (
        <nav key={section} aria-label={title ?? 'Áreas do portal'} className="space-y-3">
          {title && <h2 className="font-heading text-lg font-semibold">{title}</h2>}
          <ul className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {items.map((item) => {
              const Icon = item.icon;
              return (
                <li key={item.to}>
                  <Link
                    to={item.to}
                    className="block rounded-xl focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
                  >
                    <Card
                      className="h-full border-l-4 transition-colors hover:bg-[var(--card-hover)] active:bg-[var(--card-hover)]"
                      style={
                        {
                          borderLeftColor: hsl(item.brand),
                          '--card-hover': hsl(item.brand, 0.1),
                        } as CSSProperties
                      }
                    >
                      <CardHeader className="flex flex-row items-center gap-3 space-y-0">
                        <span
                          className="flex h-10 w-10 items-center justify-center rounded-xl"
                          style={{ backgroundColor: hsl(item.brand, 0.12), color: hsl(item.brand) }}
                        >
                          <Icon className="h-5 w-5" aria-hidden="true" />
                        </span>
                        <CardTitle className="flex flex-1 items-center gap-2 text-base">
                          {item.homeTitle ?? item.label}
                        </CardTitle>
                      </CardHeader>
                      <CardContent>
                        <p className="text-sm text-muted-foreground">{item.desc}</p>
                      </CardContent>
                    </Card>
                  </Link>
                </li>
              );
            })}
          </ul>
        </nav>
      ))}
    </section>
  );
}
