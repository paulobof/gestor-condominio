/**
 * Utilitários de data para exibição. O backend serializa `LocalDate` como ISO
 * `yyyy-MM-dd` (ex.: `1990-01-02`); aqui formatamos para o padrão brasileiro
 * `dd/MM/yyyy`. Importante: a conversão é puramente textual (split da string),
 * **sem** `new Date(...)`, que interpretaria a data como UTC e deslocaria o dia
 * em America/Sao_Paulo (UTC-3).
 */

import { formatInTimeZone } from 'date-fns-tz';

/** Formata uma data ISO (`yyyy-MM-dd`, com ou sem hora) para `dd/MM/yyyy`. */
export function formatDateBR(value: string | null | undefined): string {
  if (!value) return '—';
  const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(value);
  if (!match) return value;
  const [, year, month, day] = match;
  return `${day}/${month}/${year}`;
}

/**
 * Formata um instante (timestamp ISO com fuso, ex.: `2026-10-18T02:30:00Z`) como
 * `dd/MM/yyyy` no fuso America/Sao_Paulo. Use para `Instant`s do backend; para
 * `LocalDate` use `formatDateBR`.
 */
export function formatInstantBR(value: string | null | undefined): string {
  if (!value) return '—';
  return formatInTimeZone(value, 'America/Sao_Paulo', 'dd/MM/yyyy');
}
