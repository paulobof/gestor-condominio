import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('../api/complaintsApi', async (orig) => {
  const actual = await orig<typeof import('../api/complaintsApi')>();
  return { ...actual, listBoard: vi.fn(), listMine: vi.fn(), getCatalog: vi.fn() };
});

import { ComplaintsPage } from './ComplaintsPage';
import { getCatalog, listBoard, listMine, type Complaint } from '../api/complaintsApi';

const base: Complaint = {
  id: 'c1',
  code: 123,
  typeId: 't1',
  typeName: 'Manutenção',
  subtypeId: 's1',
  subtypeName: 'Pintura',
  locationId: 'l1',
  locationName: 'Torre 2',
  locationDetail: 'hall',
  description: 'Parede descascando',
  status: 'FORWARDED',
  dueDate: '2026-11-30',
  rejectionReason: null,
  resolutionNote: null,
  syndicResponse: 'Pintor vem dia 30.',
  syndicResponseAt: null,
  createdAt: '2026-10-10T12:00:00Z',
  forwardedAt: '2026-10-17T12:00:00Z',
  resolvedAt: null,
  editable: false,
  photos: [],
};

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(getCatalog).mockResolvedValue({ types: [], locations: [] });
  vi.mocked(listBoard).mockResolvedValue({
    content: [base],
    totalElements: 1,
    totalPages: 1,
    number: 0,
  });
  vi.mocked(listMine).mockResolvedValue([
    { ...base, id: 'c2', code: 124, status: 'REJECTED', rejectionReason: 'Duplicado do #123' },
  ]);
});

function renderPage() {
  return render(
    <MemoryRouter>
      <ComplaintsPage />
    </MemoryRouter>
  );
}

describe('ComplaintsPage', () => {
  it('mural mostra encaminhados com prazo', async () => {
    renderPage();
    expect(await screen.findByText('#123')).toBeInTheDocument();
    expect(screen.getByText('Manutenção › Pintura')).toBeInTheDocument();
    expect(screen.getByText('Prazo: 30/11/2026')).toBeInTheDocument();
    // "Encaminhado" também é opção do filtro de status: procurar dentro do card
    const card = screen.getByRole('link', { name: /#123/ });
    expect(within(card).getByText('Encaminhado')).toBeInTheDocument();
    expect(within(card).getByText('Síndica: Pintor vem dia 30.')).toBeInTheDocument();
  });

  it('aba "Meus chamados" mostra o motivo da recusa', async () => {
    renderPage();
    await userEvent.click(screen.getByRole('tab', { name: 'Meus chamados' }));
    expect(await screen.findByText('Motivo: Duplicado do #123')).toBeInTheDocument();
  });

  it('filtro de status recarrega o mural', async () => {
    renderPage();
    await screen.findByText('#123');
    await userEvent.selectOptions(screen.getByLabelText('Status'), 'RESOLVED');
    expect(listBoard).toHaveBeenLastCalledWith({
      typeId: undefined,
      locationId: undefined,
      status: 'RESOLVED',
      page: 0,
    });
  });

  it('tem link para abrir chamado', async () => {
    renderPage();
    expect(screen.getByRole('link', { name: /abrir chamado/i })).toHaveAttribute(
      'href',
      '/chamados/novo'
    );
  });

  it('"Carregar mais" acrescenta a próxima página do mural', async () => {
    vi.mocked(listBoard)
      .mockResolvedValueOnce({ content: [base], totalElements: 2, totalPages: 2, number: 0 })
      .mockResolvedValueOnce({
        content: [{ ...base, id: 'c9', code: 200 }],
        totalElements: 2,
        totalPages: 2,
        number: 1,
      });
    renderPage();
    await screen.findByText('#123');
    await userEvent.click(screen.getByRole('button', { name: 'Carregar mais' }));
    expect(await screen.findByText('#200')).toBeInTheDocument();
    expect(screen.getByText('#123')).toBeInTheDocument();
    expect(listBoard).toHaveBeenLastCalledWith({
      typeId: undefined,
      locationId: undefined,
      status: undefined,
      page: 1,
    });
    expect(screen.queryByRole('button', { name: 'Carregar mais' })).not.toBeInTheDocument();
  });

  it('trocar filtro volta para a primeira página', async () => {
    vi.mocked(listBoard)
      .mockResolvedValueOnce({ content: [base], totalElements: 2, totalPages: 2, number: 0 })
      .mockResolvedValueOnce({
        content: [{ ...base, id: 'c9', code: 200 }],
        totalElements: 2,
        totalPages: 2,
        number: 1,
      });
    renderPage();
    await screen.findByText('#123');
    await userEvent.click(screen.getByRole('button', { name: 'Carregar mais' }));
    await screen.findByText('#200');
    await userEvent.selectOptions(screen.getByLabelText('Status'), 'RESOLVED');
    expect(listBoard).toHaveBeenLastCalledWith({
      typeId: undefined,
      locationId: undefined,
      status: 'RESOLVED',
      page: 0,
    });
  });
});
