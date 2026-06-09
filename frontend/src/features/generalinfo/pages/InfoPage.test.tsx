import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('../api/generalInfoApi', () => ({ listSections: vi.fn() }));
vi.mock('@/features/auth/useAuth', () => ({ useAuth: vi.fn() }));

import { InfoPage } from './InfoPage';
import { listSections } from '../api/generalInfoApi';
import { useAuth } from '@/features/auth/useAuth';

const section = {
  id: 's1',
  title: 'Portaria',
  body: '<p>Aberta 24h</p>',
  position: 0,
  updatedAt: '2026-06-09T00:00:00Z',
};

function renderPage() {
  return render(
    <MemoryRouter>
      <InfoPage />
    </MemoryRouter>
  );
}

describe('InfoPage', () => {
  beforeEach(() => {
    vi.mocked(listSections).mockResolvedValue([section]);
    vi.mocked(useAuth).mockReturnValue({ user: { authorities: [] } } as never);
  });

  it('lista as seções com título e corpo', async () => {
    renderPage();
    expect(await screen.findByText('Portaria')).toBeInTheDocument();
    await waitFor(() => expect(screen.getByText('Aberta 24h')).toBeInTheDocument());
  });

  it('sem INFO_MANAGE não mostra "Gerenciar"', async () => {
    renderPage();
    await screen.findByText('Portaria');
    expect(screen.queryByRole('link', { name: 'Gerenciar' })).toBeNull();
  });

  it('com INFO_MANAGE mostra "Gerenciar" apontando para /informacoes/gerenciar', async () => {
    vi.mocked(useAuth).mockReturnValue({ user: { authorities: ['INFO_MANAGE'] } } as never);
    renderPage();
    const link = await screen.findByRole('link', { name: 'Gerenciar' });
    expect(link).toHaveAttribute('href', '/informacoes/gerenciar');
  });
});
