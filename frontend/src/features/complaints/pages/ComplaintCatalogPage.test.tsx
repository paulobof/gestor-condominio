import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('sonner', () => ({ toast: { error: vi.fn(), success: vi.fn() } }));
vi.mock('../api/complaintsApi', async (orig) => {
  const actual = await orig<typeof import('../api/complaintsApi')>();
  return {
    ...actual,
    adminCatalog: vi.fn(),
    createType: vi.fn(),
    updateType: vi.fn(),
    createSubtype: vi.fn(),
    updateSubtype: vi.fn(),
    createLocation: vi.fn(),
    updateLocation: vi.fn(),
    moveCatalogItem: vi.fn(),
  };
});

import { ComplaintCatalogPage } from './ComplaintCatalogPage';
import {
  adminCatalog,
  createSubtype,
  createType,
  moveCatalogItem,
  updateLocation,
} from '../api/complaintsApi';
import { toast } from 'sonner';

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(adminCatalog).mockResolvedValue({
    types: [
      {
        id: 't1',
        name: 'Manutenção',
        active: true,
        sortOrder: 1,
        subtypes: [{ id: 's1', name: 'Pintura', active: true, sortOrder: 1 }],
      },
    ],
    locations: [
      { id: 'l1', name: 'Torre 1', active: true, sortOrder: 1 },
      { id: 'l2', name: 'Garagem', active: true, sortOrder: 2 },
    ],
  });
});

function renderPage() {
  return render(
    <MemoryRouter>
      <ComplaintCatalogPage />
    </MemoryRouter>
  );
}

describe('ComplaintCatalogPage', () => {
  it('cria tipo novo com a próxima ordem', async () => {
    vi.mocked(createType).mockResolvedValue({} as never);
    renderPage();
    await screen.findByText('Manutenção');
    await userEvent.type(screen.getByLabelText('Novo tipo'), 'Jardinagem');
    await userEvent.click(screen.getByRole('button', { name: 'Adicionar tipo' }));
    await waitFor(() =>
      expect(createType).toHaveBeenCalledWith({ name: 'Jardinagem', sortOrder: 2, active: true })
    );
    expect(adminCatalog).toHaveBeenCalledTimes(2); // recarrega
  });

  it('cria subtipo dentro do tipo', async () => {
    vi.mocked(createSubtype).mockResolvedValue({} as never);
    renderPage();
    const group = await screen.findByRole('group', { name: 'Manutenção' });
    await userEvent.type(within(group).getByLabelText('Novo subtipo'), 'Elevador');
    await userEvent.click(within(group).getByRole('button', { name: 'Adicionar subtipo' }));
    await waitFor(() =>
      expect(createSubtype).toHaveBeenCalledWith('t1', {
        name: 'Elevador',
        sortOrder: 2,
        active: true,
      })
    );
  });

  it('desativa local', async () => {
    vi.mocked(updateLocation).mockResolvedValue({} as never);
    renderPage();
    await screen.findByText('Torre 1');
    await userEvent.click(screen.getByRole('button', { name: 'Desativar Torre 1' }));
    await waitFor(() =>
      expect(updateLocation).toHaveBeenCalledWith('l1', {
        name: 'Torre 1',
        sortOrder: 1,
        active: false,
      })
    );
  });

  it('nome duplicado mostra erro do backend', async () => {
    vi.mocked(createType).mockRejectedValue({
      response: { data: { message: 'Já existe um item com esse nome.' } },
    });
    renderPage();
    await screen.findByText('Manutenção');
    await userEvent.type(screen.getByLabelText('Novo tipo'), 'manutenção');
    await userEvent.click(screen.getByRole('button', { name: 'Adicionar tipo' }));
    await waitFor(() =>
      expect(toast.error).toHaveBeenCalledWith('Já existe um item com esse nome.')
    );
  });

  it('move local para baixo e recarrega; pontas ficam desabilitadas', async () => {
    vi.mocked(moveCatalogItem).mockResolvedValue(undefined);
    renderPage();
    await screen.findByText('Torre 1');
    expect(screen.getByRole('button', { name: 'Mover Torre 1 para cima' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Mover Garagem para baixo' })).toBeDisabled();

    await userEvent.click(screen.getByRole('button', { name: 'Mover Torre 1 para baixo' }));
    await waitFor(() => expect(moveCatalogItem).toHaveBeenCalledWith('locations', 'l1', 'DOWN'));
    expect(adminCatalog).toHaveBeenCalledTimes(2);
  });

  it('move tipo e subtipo com o tipo certo de item', async () => {
    vi.mocked(adminCatalog).mockResolvedValue({
      types: [
        {
          id: 't1',
          name: 'Manutenção',
          active: true,
          sortOrder: 1,
          subtypes: [
            { id: 's1', name: 'Pintura', active: true, sortOrder: 1 },
            { id: 's2', name: 'Elétrica', active: true, sortOrder: 2 },
          ],
        },
        { id: 't2', name: 'Limpeza', active: true, sortOrder: 2, subtypes: [] },
      ],
      locations: [],
    });
    vi.mocked(moveCatalogItem).mockResolvedValue(undefined);
    renderPage();
    await screen.findByText('Limpeza');

    await userEvent.click(screen.getByRole('button', { name: 'Mover Limpeza para cima' }));
    await waitFor(() => expect(moveCatalogItem).toHaveBeenCalledWith('types', 't2', 'UP'));

    await userEvent.click(screen.getByRole('button', { name: 'Mover Elétrica para cima' }));
    await waitFor(() => expect(moveCatalogItem).toHaveBeenCalledWith('subtypes', 's2', 'UP'));
  });
});
