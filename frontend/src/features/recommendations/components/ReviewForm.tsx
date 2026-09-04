import { useState, type FormEvent } from 'react';
import { ThumbsDown, ThumbsUp } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';
import type { ReviewSentiment } from '../api/recommendationsApi';
import { StarRating } from './StarRating';

const MIN_TEXT_LENGTH = 10;
const MAX_TEXT_LENGTH = 2000;

export interface ReviewFormValues {
  sentiment: ReviewSentiment;
  rating: number;
  text: string;
}

interface ReviewFormProps {
  /** Valores iniciais — usado na edição de uma avaliação já existente. */
  initialValues?: Partial<ReviewFormValues>;
  onSubmit: (values: ReviewFormValues) => void | Promise<void>;
  /** Some quando ausente (fluxo de criação, sem opção de cancelar embutida). */
  onCancel?: () => void;
  submitting?: boolean;
  submitLabel?: string;
}

/**
 * Formulário embutível de avaliação: sentimento (recomendo/não recomendo), nota de 1 a 5 estrelas
 * e comentário obrigatório de no mínimo 10 caracteres (após trim). Não faz a chamada de API —
 * quem usa decide o que fazer com os valores em `onSubmit` (criar, editar…).
 */
export function ReviewForm({
  initialValues,
  onSubmit,
  onCancel,
  submitting = false,
  submitLabel = 'Publicar avaliação',
}: ReviewFormProps) {
  const [sentiment, setSentiment] = useState<ReviewSentiment>(initialValues?.sentiment ?? 'LIKE');
  const [rating, setRating] = useState(initialValues?.rating ?? 0);
  const [text, setText] = useState(initialValues?.text ?? '');

  const trimmedLength = text.trim().length;
  const canSubmit = rating > 0 && trimmedLength >= MIN_TEXT_LENGTH && !submitting;

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!canSubmit) return;
    await onSubmit({ sentiment, rating, text: text.trim() });
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-4" noValidate>
      <div className="space-y-2">
        <Label>Você recomenda?</Label>
        <div className="flex gap-2">
          <Button
            type="button"
            variant={sentiment === 'LIKE' ? 'default' : 'outline'}
            className="min-h-[44px]"
            aria-pressed={sentiment === 'LIKE'}
            disabled={submitting}
            onClick={() => setSentiment('LIKE')}
          >
            <ThumbsUp className="mr-1 h-4 w-4" aria-hidden="true" /> Recomendo
          </Button>
          <Button
            type="button"
            variant={sentiment === 'DISLIKE' ? 'default' : 'outline'}
            className="min-h-[44px]"
            aria-pressed={sentiment === 'DISLIKE'}
            disabled={submitting}
            onClick={() => setSentiment('DISLIKE')}
          >
            <ThumbsDown className="mr-1 h-4 w-4" aria-hidden="true" /> Não recomendo
          </Button>
        </div>
      </div>

      <div className="space-y-2">
        <Label>Nota</Label>
        <StarRating value={rating} onChange={setRating} disabled={submitting} />
      </div>

      <div className="space-y-2">
        <Label htmlFor="reviewText">Comentário</Label>
        <textarea
          id="reviewText"
          value={text}
          onChange={(e) => setText(e.target.value)}
          rows={4}
          maxLength={MAX_TEXT_LENGTH}
          placeholder="Conte como foi sua experiência (mínimo 10 caracteres)…"
          className="flex w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
          disabled={submitting}
        />
        <p className="text-xs text-muted-foreground">
          {trimmedLength}/{MAX_TEXT_LENGTH} caracteres (mínimo {MIN_TEXT_LENGTH})
        </p>
      </div>

      <div className="flex gap-2">
        <Button type="submit" className="min-h-[44px]" disabled={!canSubmit}>
          {submitting ? 'Enviando…' : submitLabel}
        </Button>
        {onCancel && (
          <Button
            type="button"
            variant="ghost"
            className="min-h-[44px]"
            disabled={submitting}
            onClick={onCancel}
          >
            Cancelar
          </Button>
        )}
      </div>
    </form>
  );
}
