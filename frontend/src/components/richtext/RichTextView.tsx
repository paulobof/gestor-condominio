import DOMPurify from 'dompurify';

const ALLOWED_TAGS = ['p', 'br', 'b', 'strong', 'i', 'em', 'u', 'ul', 'ol', 'li', 'a'];
const ALLOWED_ATTR = ['href', 'target', 'rel'];

/** Renderiza HTML de rich text sanitizado (defesa em profundidade; o backend já sanitiza). */
export function RichTextView({ html, className }: { html: string; className?: string }) {
  const clean = DOMPurify.sanitize(html, { ALLOWED_TAGS, ALLOWED_ATTR });
  return (
    <div
      className={className ?? 'prose prose-sm max-w-none text-sm text-foreground'}
      dangerouslySetInnerHTML={{ __html: clean }}
    />
  );
}
