import type { ReactNode } from 'react';

/** `**negrito**` vira <strong>; o resto é texto puro (nunca HTML). */
function inline(text: string): ReactNode[] {
  return text
    .split(/(\*\*[^*]+\*\*)/g)
    .filter(Boolean)
    .map((part, i) =>
      part.startsWith('**') && part.endsWith('**') ? (
        <strong key={i}>{part.slice(2, -2)}</strong>
      ) : (
        part
      )
    );
}

/**
 * Exibe o termo de privacidade, que é guardado em Markdown simples: `#`/`##` (títulos), `- ` (lista),
 * `**negrito**` e parágrafos separados por linha em branco. Renderiza só esse subconjunto, montando
 * elementos React — sem `dangerouslySetInnerHTML`, então nada no texto vira HTML.
 */
export function PolicyText({ body }: { body: string }) {
  const blocks = body.replace(/\r\n/g, '\n').split(/\n{2,}/);

  return (
    <div className="space-y-3">
      {blocks.map((block, i) => {
        const lines = block.split('\n').filter((l) => l.trim());
        if (lines.length === 0) return null;
        const first = lines[0];
        if (first.startsWith('## ')) {
          return (
            <h3 key={i} className="pt-1 font-heading text-sm font-semibold">
              {inline(first.slice(3))}
            </h3>
          );
        }
        if (first.startsWith('# ')) {
          return (
            <h2 key={i} className="font-heading text-base font-semibold">
              {inline(first.slice(2))}
            </h2>
          );
        }
        if (lines.every((l) => l.startsWith('- '))) {
          return (
            <ul key={i} className="list-disc space-y-1 pl-5">
              {lines.map((l, j) => (
                <li key={j}>{inline(l.slice(2))}</li>
              ))}
            </ul>
          );
        }
        return <p key={i}>{inline(lines.join(' '))}</p>;
      })}
    </div>
  );
}
