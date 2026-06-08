import { Check, X } from 'lucide-react';
import { passwordRules } from '@/features/auth/passwordPolicy';

export function PasswordChecklist({ value }: { value: string }) {
  return (
    <ul aria-label="Requisitos de senha" aria-live="polite" className="space-y-1 text-xs">
      {passwordRules.map((r) => {
        const met = r.test(value);
        return (
          <li
            key={r.id}
            data-met={met}
            className={
              met
                ? 'flex items-center gap-1.5 text-green-600 dark:text-green-500'
                : 'flex items-center gap-1.5 text-muted-foreground'
            }
          >
            {met ? (
              <Check className="h-3.5 w-3.5" aria-hidden="true" />
            ) : (
              <X className="h-3.5 w-3.5" aria-hidden="true" />
            )}
            <span>{r.label}</span>
          </li>
        );
      })}
    </ul>
  );
}
