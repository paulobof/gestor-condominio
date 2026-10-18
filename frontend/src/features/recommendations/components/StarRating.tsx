import { Star } from 'lucide-react';

const STAR_LABELS = ['1 estrela', '2 estrelas', '3 estrelas', '4 estrelas', '5 estrelas'];

interface StarRatingProps {
  /** 0 = nenhuma nota escolhida ainda. */
  value: number;
  onChange: (value: number) => void;
  disabled?: boolean;
}

/**
 * Grupo de 5 botões, não um radiogroup nativo: como cada estrela é um `<button>`, Tab/Enter/Espaço
 * já funcionam sem gerenciar roving tabindex nem setas, e `aria-pressed` comunica a nota escolhida.
 */
export function StarRating({ value, onChange, disabled = false }: StarRatingProps) {
  return (
    <div role="group" aria-label="Nota" className="flex gap-1">
      {[1, 2, 3, 4, 5].map((n) => {
        const selected = n <= value;
        return (
          <button
            key={n}
            type="button"
            aria-label={STAR_LABELS[n - 1]}
            aria-pressed={selected}
            disabled={disabled}
            onClick={() => onChange(n)}
            className={`flex h-11 w-11 items-center justify-center rounded-md transition-colors disabled:cursor-not-allowed disabled:opacity-50 ${
              selected ? 'text-amber-500' : 'text-muted-foreground hover:text-amber-400'
            }`}
          >
            <Star
              className="h-6 w-6"
              fill={selected ? 'currentColor' : 'none'}
              aria-hidden="true"
            />
          </button>
        );
      })}
    </div>
  );
}
