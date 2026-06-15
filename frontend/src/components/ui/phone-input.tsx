import { useEffect, useState } from 'react';
import {
  DDI_OPTIONS,
  formatNational,
  maxNationalDigits,
  onlyDigits,
  parsePhone,
  toFullDigits,
} from '@/lib/phone';

interface PhoneInputProps {
  id?: string;
  /** Número cheio em dígitos (DDI + nacional), ex.: `5511988887777`. */
  value: string;
  /** Emite o número cheio em dígitos (vazio se não houver número). */
  onChange: (value: string) => void;
  disabled?: boolean;
  invalid?: boolean;
  placeholder?: string;
}

/**
 * Campo de celular padronizado para WhatsApp: seletor de DDI (Brasil padrão) +
 * número nacional mascarado. Emite sempre dígitos `DDI + nacional`. O DDI é
 * preservado em estado interno mesmo com o número vazio (não se perde no parse).
 */
export function PhoneInput({
  id,
  value,
  onChange,
  disabled,
  invalid,
  placeholder,
}: PhoneInputProps) {
  const initial = parsePhone(value);
  const [ddi, setDdi] = useState(initial.ddi);
  const [national, setNational] = useState(initial.national);

  // Ressincroniza quando o value externo muda (ex.: form de edição carregando).
  useEffect(() => {
    if (onlyDigits(value) !== toFullDigits(ddi, national)) {
      const p = parsePhone(value);
      setDdi(p.ddi);
      setNational(p.national);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [value]);

  const handleDdi = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const next = e.target.value;
    const trimmed = onlyDigits(national).slice(0, maxNationalDigits(next));
    setDdi(next);
    setNational(trimmed);
    onChange(toFullDigits(next, trimmed));
  };

  const handleNational = (e: React.ChangeEvent<HTMLInputElement>) => {
    const d = onlyDigits(e.target.value).slice(0, maxNationalDigits(ddi));
    setNational(d);
    onChange(toFullDigits(ddi, d));
  };

  return (
    <div className="flex gap-2">
      <select
        aria-label="Código do país (DDI)"
        value={ddi}
        onChange={handleDdi}
        disabled={disabled}
        className="min-h-[44px] shrink-0 rounded-lg border border-input bg-background px-2 text-sm"
      >
        {DDI_OPTIONS.map((o) => (
          <option key={o.ddi} value={o.ddi}>
            {o.flag} +{o.ddi}
          </option>
        ))}
      </select>
      <input
        id={id}
        type="tel"
        inputMode="numeric"
        autoComplete="tel-national"
        value={formatNational(ddi, national)}
        onChange={handleNational}
        disabled={disabled}
        placeholder={placeholder ?? (ddi === '55' ? '(11) 98888-7777' : 'número')}
        aria-invalid={invalid || undefined}
        className={[
          'min-h-[44px] w-full rounded-lg border bg-background px-3 text-sm',
          invalid ? 'border-destructive' : 'border-input',
        ].join(' ')}
      />
    </div>
  );
}
