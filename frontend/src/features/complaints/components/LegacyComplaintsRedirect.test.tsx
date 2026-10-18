import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { LegacyComplaintsRedirect } from './LegacyComplaintsRedirect';

function Where() {
  const loc = useLocation();
  return <p data-testid="where">{loc.pathname + loc.search + loc.hash}</p>;
}

function renderAt(path: string) {
  render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/chamados/*" element={<LegacyComplaintsRedirect />} />
        <Route path="/admin/chamados/*" element={<LegacyComplaintsRedirect />} />
        <Route path="*" element={<Where />} />
      </Routes>
    </MemoryRouter>
  );
}

describe('LegacyComplaintsRedirect', () => {
  it.each([
    ['/chamados', '/reclamacoes'],
    ['/chamados/novo', '/reclamacoes/nova'],
    ['/chamados/novo?x=1', '/reclamacoes/nova?x=1'],
    ['/chamados/c1', '/reclamacoes/c1'],
    ['/chamados/c1/editar', '/reclamacoes/c1/editar'],
    ['/admin/chamados', '/admin/reclamacoes'],
    ['/admin/chamados/c1', '/admin/reclamacoes/c1'],
    ['/admin/chamados/encaminhar', '/admin/reclamacoes/encaminhar'],
    ['/admin/chamados/catalogo', '/admin/reclamacoes/catalogo'],
    ['/chamados?x=1#y', '/reclamacoes?x=1#y'],
  ])('%s → %s', (from, to) => {
    renderAt(from);
    expect(screen.getByTestId('where')).toHaveTextContent(to);
  });
});
