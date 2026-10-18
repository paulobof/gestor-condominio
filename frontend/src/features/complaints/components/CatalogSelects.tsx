import { Label } from '@/components/ui/label';
import type { Catalog } from '../api/complaintsApi';

export interface CatalogSelection {
  typeId: string;
  subtypeId: string;
  locationId: string;
}

/** Itens já usados pelo chamado que podem ter sido desativados no catálogo. */
export interface CatalogFallback {
  typeId?: string;
  typeName?: string;
  subtypeId?: string;
  subtypeName?: string;
  locationId?: string;
  locationName?: string;
}

const SELECT =
  'flex h-11 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2';

interface Props {
  catalog: Catalog;
  value: CatalogSelection;
  onChange: (v: CatalogSelection) => void;
  fallback?: CatalogFallback;
}

export function CatalogSelects({ catalog, value, onChange, fallback }: Props) {
  const type = catalog.types.find((t) => t.id === value.typeId);
  const subtypes = type?.subtypes ?? [];
  const typeMissing = !!value.typeId && !type && fallback?.typeId === value.typeId;
  const subtypeMissing =
    !!value.subtypeId &&
    !subtypes.some((s) => s.id === value.subtypeId) &&
    fallback?.subtypeId === value.subtypeId;
  const locationMissing =
    !!value.locationId &&
    !catalog.locations.some((l) => l.id === value.locationId) &&
    fallback?.locationId === value.locationId;

  return (
    <div className="grid gap-4 sm:grid-cols-3">
      <div className="space-y-2">
        <Label htmlFor="complaint-type">Tipo</Label>
        <select
          id="complaint-type"
          className={SELECT}
          value={value.typeId}
          onChange={(e) => onChange({ ...value, typeId: e.target.value, subtypeId: '' })}
          required
        >
          <option value="">Selecione…</option>
          {typeMissing && <option value={value.typeId}>{fallback?.typeName} (inativo)</option>}
          {catalog.types.map((t) => (
            <option key={t.id} value={t.id}>
              {t.name}
            </option>
          ))}
        </select>
      </div>
      <div className="space-y-2">
        <Label htmlFor="complaint-subtype">Subtipo</Label>
        <select
          id="complaint-subtype"
          className={SELECT}
          value={value.subtypeId}
          onChange={(e) => onChange({ ...value, subtypeId: e.target.value })}
          disabled={!value.typeId}
          required
        >
          <option value="">Selecione…</option>
          {subtypeMissing && (
            <option value={value.subtypeId}>{fallback?.subtypeName} (inativo)</option>
          )}
          {subtypes.map((s) => (
            <option key={s.id} value={s.id}>
              {s.name}
            </option>
          ))}
        </select>
      </div>
      <div className="space-y-2">
        <Label htmlFor="complaint-location">Local</Label>
        <select
          id="complaint-location"
          className={SELECT}
          value={value.locationId}
          onChange={(e) => onChange({ ...value, locationId: e.target.value })}
          required
        >
          <option value="">Selecione…</option>
          {locationMissing && (
            <option value={value.locationId}>{fallback?.locationName} (inativo)</option>
          )}
          {catalog.locations.map((l) => (
            <option key={l.id} value={l.id}>
              {l.name}
            </option>
          ))}
        </select>
      </div>
    </div>
  );
}
