import { useState } from 'react';
import { DAY_LABELS, type OpeningHoursDto } from './openingHours';

interface RowState {
  enabled: boolean;
  opensAt: string;
  closesAt: string;
}

function seedRows(value: OpeningHoursDto[]): RowState[] {
  return DAY_LABELS.map((_, dow) => {
    const entry = value.find((h) => h.dayOfWeek === dow);
    return {
      enabled: !!(entry?.opensAt && entry?.closesAt),
      opensAt: entry?.opensAt ? entry.opensAt.slice(0, 5) : '',
      closesAt: entry?.closesAt ? entry.closesAt.slice(0, 5) : '',
    };
  });
}

function buildHours(rows: RowState[]): OpeningHoursDto[] {
  return rows
    .map((row, dow) => ({ dow, row }))
    .filter(({ row }) => row.enabled && row.opensAt && row.closesAt)
    .map(({ dow, row }) => ({
      dayOfWeek: dow,
      opensAt: row.opensAt,
      closesAt: row.closesAt,
    }));
}

interface Props {
  value: OpeningHoursDto[];
  is24h: boolean;
  onChange: (hours: OpeningHoursDto[], is24h: boolean) => void;
}

export function OpeningHoursEditor({ value, is24h, onChange }: Props) {
  const [rows, setRows] = useState<RowState[]>(() => seedRows(value));

  function handleToggle24h(checked: boolean) {
    if (checked) {
      onChange([], true);
    } else {
      onChange(value, false);
    }
  }

  function updateRow(dow: number, patch: Partial<RowState>) {
    const next = rows.map((r, i) => (i === dow ? { ...r, ...patch } : r));
    setRows(next);
    onChange(buildHours(next), false);
  }

  return (
    <div className="space-y-4">
      {/* 24h toggle */}
      <label className="flex min-h-[44px] cursor-pointer items-center gap-3 text-sm font-medium">
        <input
          type="checkbox"
          className="h-4 w-4 accent-primary"
          checked={is24h}
          onChange={(e) => handleToggle24h(e.target.checked)}
          aria-label="Aberto 24 horas"
        />
        Aberto 24 horas
      </label>

      {/* 7-day grid */}
      {!is24h && (
        <div className="space-y-2">
          {DAY_LABELS.map((label, dow) => {
            const row = rows[dow];
            return (
              <div
                key={dow}
                className="flex flex-wrap items-center gap-3 rounded-lg border border-border px-4 py-3"
              >
                {/* Day enabled checkbox */}
                <label className="flex min-h-[44px] w-28 cursor-pointer items-center gap-2 text-sm">
                  <input
                    type="checkbox"
                    className="h-4 w-4 accent-primary"
                    checked={row.enabled}
                    onChange={(e) => updateRow(dow, { enabled: e.target.checked })}
                    aria-label={label}
                  />
                  <span>{label}</span>
                </label>

                {/* Opens input */}
                <div className="flex flex-col gap-1">
                  <label htmlFor={`opens-${dow}`} className="text-xs text-muted-foreground">
                    {`Abre (${label})`}
                  </label>
                  <input
                    id={`opens-${dow}`}
                    type="time"
                    value={row.opensAt}
                    disabled={!row.enabled}
                    onChange={(e) => updateRow(dow, { opensAt: e.target.value })}
                    className="min-h-[44px] rounded-md border border-input bg-background px-3 py-2 text-sm disabled:cursor-not-allowed disabled:opacity-50"
                  />
                </div>

                {/* Closes input */}
                <div className="flex flex-col gap-1">
                  <label htmlFor={`closes-${dow}`} className="text-xs text-muted-foreground">
                    {`Fecha (${label})`}
                  </label>
                  <input
                    id={`closes-${dow}`}
                    type="time"
                    value={row.closesAt}
                    disabled={!row.enabled}
                    onChange={(e) => updateRow(dow, { closesAt: e.target.value })}
                    className="min-h-[44px] rounded-md border border-input bg-background px-3 py-2 text-sm disabled:cursor-not-allowed disabled:opacity-50"
                  />
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
