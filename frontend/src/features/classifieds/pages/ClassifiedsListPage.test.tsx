import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('../api/classifiedsApi', () => ({ listClassifieds: vi.fn() }));
vi.mock('@/features/auth/useAuth', () => ({ useAuth: vi.fn() }));
vi.mock('sonner', () => ({ toast: { error: vi.fn(), success: vi.fn() } }));

import { ClassifiedsListPage } from './ClassifiedsListPage';
import { listClassifieds } from '../api/classifiedsApi';
import { useAuth } from '@/features/auth/useAuth';

const listMock = vi.mocked(listClassifieds);

function page(content: unknown[]) {
  return { content, totalElements: content.length, totalPages: 1, number: 0 } as never;
}

function renderPage() {
  return render(
    <MemoryRouter>
      <ClassifiedsListPage />
    </MemoryRouter>
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(useAuth).mockReturnValue({ user: { id: 'u1', authorities: [] } } as never);
});

describe('ClassifiedsListPage', () => {
  it('carrega anúncios ACTIVE por padrão', async () => {
    listMock.mockResolvedValue(page([{ id: 'c1', title: 'Sofá 3 lugares', price: 500 }]));
    renderPage();

    expect(await screen.findByText('Sofá 3 lugares')).toBeInTheDocument();
    expect(listMock).toHaveBeenCalledWith('ACTIVE');
  });

  it('mostra estado vazio', async () => {
    listMock.mockResolvedValue(page([]));
    renderPage();
    expect(await screen.findByText('Nenhum anúncio.')).toBeInTheDocument();
  });

  it('trocar o filtro para Vendidos refaz a busca com SOLD', async () => {
    listMock.mockResolvedValue(page([]));
    renderPage();
    await screen.findByText('Nenhum anúncio.');

    await userEvent.click(screen.getByRole('tab', { name: 'Vendidos' }));

    await waitFor(() => expect(listMock).toHaveBeenLastCalledWith('SOLD'));
  });

  it('visitante vê só os ativos, sem as abas de status', async () => {
    vi.mocked(useAuth).mockReturnValue({ user: null } as never);
    listMock.mockResolvedValue(page([{ id: 'c1', title: 'Sofá 3 lugares', price: 500 }]));
    renderPage();

    expect(await screen.findByText('Sofá 3 lugares')).toBeInTheDocument();
    expect(listMock).toHaveBeenCalledWith('ACTIVE');
    expect(screen.queryByRole('tablist')).not.toBeInTheDocument();
    expect(screen.queryByRole('tab', { name: 'Ativos' })).not.toBeInTheDocument();
  });
});
