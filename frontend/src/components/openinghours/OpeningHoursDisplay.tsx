import { toZonedTime } from 'date-fns-tz';
import {
  DAY_LABELS,
  TIME_ZONE,
  isOpenNow,
  stripSeconds,
  type OpeningHoursDto,
} from './openingHours';

interface Props {
  openingHours: OpeningHoursDto[];
  is24h: boolean;
}

export function OpeningHoursDisplay({ openingHours, is24h }: Props) {
  // Real-clock read lives here; pure logic lives in isOpenNow (tested separately).
  const z = toZonedTime(new Date(), TIME_ZONE);
  const todayDow = z.getDay();
  const now = { dayOfWeek: todayDow, minutes: z.getHours() * 60 + z.getMinutes() };

  const open = isOpenNow(openingHours, is24h, now);

  return (
    <div className="space-y-2 text-sm">
      {/* Status badge */}
      {is24h ? (
        <span className="inline-flex items-center rounded-full border border-border bg-muted px-2.5 py-0.5 text-xs font-medium text-muted-foreground">
          24 horas
        </span>
      ) : open ? (
        <span className="inline-flex items-center rounded-full bg-green-100 px-2.5 py-0.5 text-xs font-medium text-green-800 dark:bg-green-900/30 dark:text-green-400">
          Aberto agora
        </span>
      ) : (
        <span className="inline-flex items-center rounded-full bg-muted px-2.5 py-0.5 text-xs font-medium text-muted-foreground">
          Fechado
        </span>
      )}

      {/* Collapsible weekly schedule */}
      <details className="group rounded-lg border border-border">
        <summary className="flex min-h-[44px] cursor-pointer list-none items-center justify-between gap-3 px-4 py-3 text-sm font-medium hover:bg-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-inset">
          <span>Ver horários</span>
          <svg
            aria-hidden="true"
            className="h-4 w-4 shrink-0 transition-transform group-open:rotate-180"
            fill="none"
            stroke="currentColor"
            strokeWidth={2}
            viewBox="0 0 24 24"
          >
            <path strokeLinecap="round" strokeLinejoin="round" d="M19 9l-7 7-7-7" />
          </svg>
        </summary>

        <div className="divide-y divide-border px-4 pb-3">
          {DAY_LABELS.map((label, dow) => {
            const entry = openingHours.find((h) => h.dayOfWeek === dow);
            const isToday = dow === todayDow;
            const hasTime = entry?.opensAt && entry?.closesAt;

            return (
              <div
                key={dow}
                className={`flex items-center justify-between py-2 text-xs${isToday ? ' font-semibold text-foreground' : ' text-muted-foreground'}`}
              >
                <span>
                  {label}
                  {isToday ? ' (hoje)' : ''}
                </span>
                <span>
                  {is24h
                    ? '24 horas'
                    : hasTime
                      ? `${stripSeconds(entry!.opensAt!)} – ${stripSeconds(entry!.closesAt!)}`
                      : 'Fechado'}
                </span>
              </div>
            );
          })}
        </div>
      </details>
    </div>
  );
}
