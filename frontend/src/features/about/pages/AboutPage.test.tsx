import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect } from 'vitest';

import { AboutPage } from './AboutPage';

function renderAbout() {
  return render(
    <MemoryRouter>
      <AboutPage />
    </MemoryRouter>
  );
}

describe('AboutPage — apresentação pública', () => {
  it('deixa claro que o app é independente de quem administra o condomínio', () => {
    renderAbout();
    expect(screen.getByText(/aplicativo independente/i)).toBeInTheDocument();
    expect(screen.getByText(/feito por um morador/i)).toBeInTheDocument();
    expect(screen.getByText(/s[íi]ndico ou o conselho/i)).toBeInTheDocument();
  });

  it('explica a entrada por unidade e a aprovação de quem já está', () => {
    renderAbout();
    expect(screen.getByText(/entra na hora/i)).toBeInTheDocument();
    expect(screen.getByText(/recebe seu pedido e decide/i)).toBeInTheDocument();
  });

  it('leva para o cadastro, para o login e para a política de privacidade', () => {
    renderAbout();
    expect(screen.getByRole('link', { name: /criar minha conta/i })).toHaveAttribute(
      'href',
      '/register-master'
    );
    expect(screen.getByRole('link', { name: /já tenho conta/i })).toHaveAttribute('href', '/login');
    expect(screen.getByRole('link', { name: /política de privacidade/i })).toHaveAttribute(
      'href',
      '/privacidade'
    );
  });

  it('não expõe conteúdo do condomínio', () => {
    renderAbout();
    // nada de avisos, documentos ou lista de moradores em página pública
    expect(screen.queryByRole('link', { name: /^avisos$/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /^documentos$/i })).not.toBeInTheDocument();
  });
});
