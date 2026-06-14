import { Globe, Mail, MessageCircle } from 'lucide-react';
import { cn } from '@/lib/utils';

/** Dados de contato da desenvolvedora — único lugar de manutenção. */
const WIZORTECH = {
  site: 'https://wizortech.com.br/',
  email: 'contato@wizortech.com.br',
  whatsapp: 'https://api.whatsapp.com/send/?phone=551145801261&text&type=phone_number&app_absent=0',
} as const;

const iconLinkClass =
  'inline-flex h-11 w-11 items-center justify-center rounded-md text-muted-foreground ' +
  'transition-colors hover:text-foreground focus-visible:outline-none focus-visible:ring-2 ' +
  'focus-visible:ring-ring';

/**
 * Crédito discreto da desenvolvedora (Wizortech) com atalhos de contato:
 * site, e-mail e WhatsApp. Usado na tela de login e no footer global.
 */
export function DeveloperCredit({ className }: { className?: string }) {
  return (
    <div
      className={cn(
        'flex flex-wrap items-center justify-center gap-x-1 gap-y-0 text-xs text-muted-foreground',
        className
      )}
    >
      <span>
        Desenvolvido por <span className="font-medium text-foreground">Wizortech</span>
      </span>
      <span className="flex items-center">
        <a
          href={WIZORTECH.site}
          target="_blank"
          rel="noopener noreferrer"
          aria-label="Site da Wizortech"
          title="Site"
          className={iconLinkClass}
        >
          <Globe className="h-4 w-4" aria-hidden="true" />
        </a>
        <a
          href={`mailto:${WIZORTECH.email}`}
          aria-label="E-mail da Wizortech"
          title={WIZORTECH.email}
          className={iconLinkClass}
        >
          <Mail className="h-4 w-4" aria-hidden="true" />
        </a>
        <a
          href={WIZORTECH.whatsapp}
          target="_blank"
          rel="noopener noreferrer"
          aria-label="WhatsApp da Wizortech"
          title="WhatsApp"
          className={iconLinkClass}
        >
          <MessageCircle className="h-4 w-4" aria-hidden="true" />
        </a>
      </span>
    </div>
  );
}
