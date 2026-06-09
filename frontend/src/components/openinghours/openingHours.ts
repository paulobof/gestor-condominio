export interface OpeningHoursDto {
  dayOfWeek: number; // 0=Sunday .. 6=Saturday
  opensAt: string | null; // "HH:mm" or "HH:mm:ss"
  closesAt: string | null;
  notes?: string | null;
}

export const DAY_LABELS = ['Domingo', 'Segunda', 'Terça', 'Quarta', 'Quinta', 'Sexta', 'Sábado'];

export const TIME_ZONE = 'America/Sao_Paulo';

function toMinutes(t: string): number {
  const [h, m] = t.split(':').map(Number);
  return h * 60 + m;
}

/**
 * Pure function — `now` is already expressed in São Paulo local time.
 * Returns true if the venue is open at the given moment.
 */
export function isOpenNow(
  hours: OpeningHoursDto[],
  is24h: boolean,
  now: { dayOfWeek: number; minutes: number }
): boolean {
  if (is24h) return true;
  const today = hours.find((h) => h.dayOfWeek === now.dayOfWeek && h.opensAt && h.closesAt);
  if (!today) return false;
  const open = toMinutes(today.opensAt!);
  const close = toMinutes(today.closesAt!);
  return open <= now.minutes && now.minutes < close; // same-day ranges only (no overnight) for MVP
}

/** Strips trailing ":ss" from "HH:mm:ss" → "HH:mm". Leaves "HH:mm" unchanged. */
export function stripSeconds(t: string): string {
  const parts = t.split(':');
  return `${parts[0]}:${parts[1]}`;
}
