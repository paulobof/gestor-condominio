/**
 * Utilitários de telefone para WhatsApp. O Evolution API espera apenas dígitos
 * com DDI (ex.: `5511988887777`). O foco é Brasil (+55 padrão); a lista de DDIs
 * é curada e o número nacional é mascarado para BR. Mantém o backend intacto: o
 * `PhoneNumberNormalizer` já aceita números de 12/13 dígitos (DDI + DDD + número).
 */

export interface ParsedPhone {
  ddi: string;
  national: string;
}

export interface DdiOption {
  ddi: string;
  flag: string;
  label: string;
}

/** DDIs oferecidos no seletor; Brasil é o padrão (escopo BR-first). */
export const DDI_OPTIONS: DdiOption[] = [
  { ddi: '55', flag: '🇧🇷', label: 'Brasil' },
  { ddi: '351', flag: '🇵🇹', label: 'Portugal' },
  { ddi: '1', flag: '🇺🇸', label: 'EUA/Canadá' },
  { ddi: '54', flag: '🇦🇷', label: 'Argentina' },
  { ddi: '598', flag: '🇺🇾', label: 'Uruguai' },
  { ddi: '595', flag: '🇵🇾', label: 'Paraguai' },
];

export const DEFAULT_DDI = '55';

/** Remove tudo que não for dígito. */
export function onlyDigits(value: string): string {
  return (value ?? '').replace(/\D/g, '');
}

/** Máximo de dígitos do número nacional (sem DDI), por DDI. */
export function maxNationalDigits(ddi: string): number {
  return ddi === '55' ? 11 : 14;
}

/**
 * Quebra um número cheio (com ou sem DDI) em `{ ddi, national }`. BR-first: um
 * número de 10/11 dígitos é tratado como nacional brasileiro (sem DDI), e
 * `55` + 10/11 como brasileiro com DDI. Só então tenta os outros DDIs conhecidos.
 */
export function parsePhone(full: string): ParsedPhone {
  const digits = onlyDigits(full);
  if (!digits) return { ddi: DEFAULT_DDI, national: '' };

  if (digits.startsWith('55') && (digits.length === 12 || digits.length === 13)) {
    return { ddi: '55', national: digits.slice(2) };
  }
  if (digits.length === 10 || digits.length === 11) {
    return { ddi: '55', national: digits };
  }
  const others = DDI_OPTIONS.map((o) => o.ddi)
    .filter((d) => d !== '55')
    .sort((a, b) => b.length - a.length);
  for (const ddi of others) {
    if (digits.startsWith(ddi) && digits.length > ddi.length) {
      return { ddi, national: digits.slice(ddi.length) };
    }
  }
  return { ddi: DEFAULT_DDI, national: digits };
}

/** Formata o número nacional para exibição. Máscara BR só quando DDI = 55. */
export function formatNational(ddi: string, national: string): string {
  const d = onlyDigits(national).slice(0, maxNationalDigits(ddi));
  if (ddi !== '55') return d;
  if (d.length <= 2) return d;
  const ddd = d.slice(0, 2);
  const rest = d.slice(2);
  // celular (11 díg.) usa bloco do meio com 5; fixo (10 díg.) com 4.
  const split = d.length > 10 ? 5 : 4;
  if (rest.length <= split) return `(${ddd}) ${rest}`;
  return `(${ddd}) ${rest.slice(0, split)}-${rest.slice(split)}`;
}

/** Valida o número. BR exige 10 (fixo) ou 11 (celular) dígitos; demais, 6–14. */
export function isValidPhone(ddi: string, national: string): boolean {
  const d = onlyDigits(national);
  if (ddi === '55') return d.length === 10 || d.length === 11;
  return d.length >= 6 && d.length <= 14;
}

/** Concatena DDI + número em dígitos puros (o que vai para o backend). Vazio se sem número. */
export function toFullDigits(ddi: string, national: string): string {
  const d = onlyDigits(national);
  return d ? onlyDigits(ddi) + d : '';
}
