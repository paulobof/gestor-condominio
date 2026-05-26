import { useEffect, useState } from 'react';
import { Label } from '@/components/ui/label';
import { lookupUnit } from '@/features/consent/api/consentApi';

interface Props {
  value: string | null;
  onChange: (code: string | null, hasActiveMaster: boolean | null) => void;
}

const TOWERS = ['A', 'B', 'C'];
const FLOORS = Array.from({ length: 32 - 4 + 1 }, (_, i) => i + 4);
const POSITIONS = [1, 2, 3, 4, 5, 6];

export function UnitSelector({ value, onChange }: Props) {
  const [tower, setTower] = useState<string>('');
  const [floor, setFloor] = useState<number | ''>('');
  const [position, setPosition] = useState<number | ''>('');
  const [hasActiveMaster, setHasActiveMaster] = useState<boolean | null>(null);

  useEffect(() => {
    if (tower && floor && position) {
      const code = `${floor}${String(position).padStart(2, '0')}${tower}`;
      lookupUnit(code)
        .then((r) => {
          setHasActiveMaster(r.hasActiveMaster);
          onChange(code, r.hasActiveMaster);
        })
        .catch(() => onChange(null, null));
    } else {
      onChange(null, null);
      setHasActiveMaster(null);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tower, floor, position]);

  return (
    <div className="grid grid-cols-3 gap-3">
      <div>
        <Label>Torre</Label>
        <select
          className="w-full rounded-md border border-input bg-background px-3 py-2"
          value={tower}
          onChange={(e) => setTower(e.target.value)}
        >
          <option value="">—</option>
          {TOWERS.map((t) => (
            <option key={t} value={t}>
              {t}
            </option>
          ))}
        </select>
      </div>
      <div>
        <Label>Andar</Label>
        <select
          className="w-full rounded-md border border-input bg-background px-3 py-2"
          value={floor}
          onChange={(e) => setFloor(e.target.value ? parseInt(e.target.value, 10) : '')}
        >
          <option value="">—</option>
          {FLOORS.map((f) => (
            <option key={f} value={f}>
              {f}
            </option>
          ))}
        </select>
      </div>
      <div>
        <Label>Apto</Label>
        <select
          className="w-full rounded-md border border-input bg-background px-3 py-2"
          value={position}
          onChange={(e) => setPosition(e.target.value ? parseInt(e.target.value, 10) : '')}
        >
          <option value="">—</option>
          {POSITIONS.map((p) => (
            <option key={p} value={p}>
              {String(p).padStart(2, '0')}
            </option>
          ))}
        </select>
      </div>
      {value && hasActiveMaster && (
        <p className="col-span-3 text-sm text-destructive" role="alert">
          Esta unidade já possui um master. Procure o síndico se você é o morador.
        </p>
      )}
      {value && hasActiveMaster === false && (
        <p className="col-span-3 text-sm text-success">
          Unidade disponível: <strong>{value}</strong>
        </p>
      )}
    </div>
  );
}
