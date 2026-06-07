import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect } from 'vitest';
import { BottomNav } from './BottomNav';

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <BottomNav />
    </MemoryRouter>
  );
}

describe('BottomNav', () => {
  it('mostra os 4 atalhos principais com seus destinos', () => {
    renderAt('/');
    expect(screen.getByRole('link', { name: /início/i })).toHaveAttribute('href', '/');
    expect(screen.getByRole('link', { name: /avisos/i })).toHaveAttribute('href', '/avisos');
    expect(screen.getByRole('link', { name: /indicações/i })).toHaveAttribute(
      'href',
      '/indicacoes'
    );
    expect(screen.getByRole('link', { name: /vendas/i })).toHaveAttribute('href', '/classificados');
  });

  it('marca o item ativo conforme a rota', () => {
    renderAt('/avisos');
    expect(screen.getByRole('link', { name: /avisos/i })).toHaveAttribute('aria-current', 'page');
    // "Início" só fica ativo na rota exata "/"
    expect(screen.getByRole('link', { name: /início/i })).not.toHaveAttribute('aria-current');
  });
});
