import { useRef, useState } from 'react';
import { Label } from '@/components/ui/label';

interface Props {
  value: File | null;
  onChange: (f: File | null) => void;
}

const MAX_SIZE = 5 * 1024 * 1024;
const ACCEPTED = ['application/pdf', 'image/jpeg', 'image/png', 'image/webp'];

export function ProofUploader({ value, onChange }: Props) {
  const ref = useRef<HTMLInputElement>(null);
  const [error, setError] = useState<string | null>(null);

  const handleFile = (f: File | null) => {
    setError(null);
    if (!f) {
      onChange(null);
      return;
    }
    if (!ACCEPTED.includes(f.type)) {
      setError('Tipo inválido. Aceitamos PDF, JPG, PNG ou WEBP.');
      return;
    }
    if (f.size > MAX_SIZE) {
      setError('Arquivo maior que 5MB.');
      return;
    }
    onChange(f);
  };

  return (
    <div>
      <Label htmlFor="proof">Comprovante de residência</Label>
      <input
        id="proof"
        ref={ref}
        type="file"
        accept=".pdf,.jpg,.jpeg,.png,.webp"
        className="block w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
        onChange={(e) => handleFile(e.target.files?.[0] ?? null)}
      />
      {value && (
        <p className="text-sm text-muted-foreground mt-2">
          Selecionado: {value.name} ({(value.size / 1024).toFixed(0)} KB)
        </p>
      )}
      {error && (
        <p className="text-sm text-destructive mt-2" role="alert">
          {error}
        </p>
      )}
    </div>
  );
}
