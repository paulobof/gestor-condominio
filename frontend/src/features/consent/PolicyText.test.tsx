import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { PolicyText } from './PolicyText';

const BODY = [
  '# Política de Privacidade',
  '',
  '**Controladora:** WIZOR <b>x</b>',
  '',
  '## 2. Dados',
  '',
  '- **Cadastro:** nome e e-mail.',
  '- Comprovante.',
].join('\n');

describe('PolicyText', () => {
  it('formata títulos, listas e negrito em vez de mostrar as marcas do Markdown', () => {
    const { container } = render(<PolicyText body={BODY} />);

    expect(
      screen.getByRole('heading', { level: 2, name: 'Política de Privacidade' })
    ).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 3, name: '2. Dados' })).toBeInTheDocument();
    expect(screen.getAllByRole('listitem')).toHaveLength(2);
    expect(screen.getByText('Cadastro:').tagName).toBe('STRONG');
    expect(container.textContent).not.toMatch(/[#*]/);
  });

  it('não interpreta HTML vindo do texto', () => {
    const { container } = render(<PolicyText body={BODY} />);
    expect(container.querySelector('b')).toBeNull();
    expect(container.textContent).toContain('<b>x</b>');
  });
});
