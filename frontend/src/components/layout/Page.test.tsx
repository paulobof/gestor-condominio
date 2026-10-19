import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { DetailLayout, Page, PageHeader } from './Page';

describe('Page', () => {
  it('alinha à esquerda (sem centralizar) e não cria outro landmark main', () => {
    const { container } = render(
      <Page variant="list">
        <p>conteúdo</p>
      </Page>
    );
    const root = container.firstElementChild as HTMLElement;
    expect(root.tagName).toBe('DIV');
    expect(root.className).not.toContain('mx-auto');
    expect(root.className).toContain('max-w-6xl');
    expect(screen.queryByRole('main')).not.toBeInTheDocument();
  });

  it('formulário e leitura usam linha mais curta que listas', () => {
    const { container: form } = render(<Page variant="form">x</Page>);
    const { container: reading } = render(<Page variant="reading">x</Page>);
    expect((form.firstElementChild as HTMLElement).className).toContain('max-w-2xl');
    expect((reading.firstElementChild as HTMLElement).className).toContain('max-w-3xl');
  });

  it('cabeçalho traz título como h1 e as ações na mesma linha', () => {
    render(<PageHeader title="Classificados" accent="green" actions={<button>Novo</button>} />);
    expect(screen.getByRole('heading', { level: 1, name: 'Classificados' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Novo' })).toBeInTheDocument();
  });

  it('detalhe com lateral vira duas colunas; sem lateral, uma só', () => {
    const { container, rerender } = render(<DetailLayout main={<p>a</p>} aside={<p>b</p>} />);
    expect(container.querySelector('aside')).toBeInTheDocument();
    rerender(<DetailLayout main={<p>a</p>} />);
    expect(container.querySelector('aside')).toBeNull();
  });
});
