import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('sonner', () => ({ toast: { error: vi.fn(), success: vi.fn() } }));
vi.mock('../api/complaintsApi', async (orig) => {
  const actual = await orig<typeof import('../api/complaintsApi')>();
  return {
    ...actual,
    adminDetail: vi.fn(),
    adminAction: vi.fn(),
    adminReject: vi.fn(),
    adminEdit: vi.fn(),
    adminSetDueDate: vi.fn(),
    adminResolve: vi.fn(),
    adminSetSyndicResponse: vi.fn(),
    getCatalog: vi.fn(),
    getComplaintPhotoUrl: vi.fn(),
  };
});

import { ComplaintAdminDetailPage } from './ComplaintAdminDetailPage';
import {
  adminAction,
  adminDetail,
  adminReject,
  adminSetDueDate,
  adminSetSyndicResponse,
  getCatalog,
  type AdminComplaintDetail,
  type ComplaintStatus,
} from '../api/complaintsApi';
import { toast } from 'sonner';

function detail(status: ComplaintStatus): AdminComplaintDetail {
  return {
    complaint: {
      id: 'c1',
      code: 7,
      typeId: 't',
      typeName: 'Manutenção',
      subtypeId: 's',
      subtypeName: 'Elétrica',
      locationId: 'l',
      locationName: 'Garagem',
      locationDetail: null,
      description: 'Lâmpada queimada',
      status,
      dueDate: null,
      rejectionReason: null,
      resolutionNote: null,
      syndicResponse: null,
      syndicResponseAt: null,
      createdAt: '2026-10-10T12:00:00Z',
      forwardedAt: null,
      resolvedAt: null,
      editable: false,
      photos: [],
    },
    authorName: 'Ana Souza',
    unitCode: 'T2-51',
    events: [
      {
        kind: 'CREATED',
        actorName: 'Ana Souza',
        fromValue: null,
        toValue: 'OPEN',
        createdAt: '2026-10-10T12:00:00Z',
      },
    ],
  };
}

function renderAt() {
  return render(
    <MemoryRouter initialEntries={['/admin/reclamacoes/c1']}>
      <Routes>
        <Route path="/admin/reclamacoes/:id" element={<ComplaintAdminDetailPage />} />
      </Routes>
    </MemoryRouter>
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(getCatalog).mockResolvedValue({ types: [], locations: [] });
});

describe('ComplaintAdminDetailPage', () => {
  it('reclamação aberta: iniciar análise', async () => {
    vi.mocked(adminDetail).mockResolvedValue(detail('OPEN'));
    vi.mocked(adminAction).mockResolvedValue(detail('IN_REVIEW'));
    renderAt();
    await userEvent.click(await screen.findByRole('button', { name: 'Iniciar análise' }));
    expect(adminAction).toHaveBeenCalledWith('c1', 'start-review');
    expect(await screen.findByRole('button', { name: 'Marcar como pronta' })).toBeInTheDocument();
  });

  it('recusar exige motivo', async () => {
    vi.mocked(adminDetail).mockResolvedValue(detail('IN_REVIEW'));
    renderAt();
    await userEvent.click(await screen.findByRole('button', { name: 'Recusar' }));
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar recusa' }));
    expect(toast.error).toHaveBeenCalledWith('Informe o motivo da recusa.');
    expect(adminReject).not.toHaveBeenCalled();

    vi.mocked(adminReject).mockResolvedValue(detail('REJECTED'));
    await userEvent.type(screen.getByLabelText('Motivo da recusa'), 'Duplicado');
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar recusa' }));
    await waitFor(() => expect(adminReject).toHaveBeenCalledWith('c1', 'Duplicado'));
  });

  it('encaminhado: define prazo', async () => {
    vi.mocked(adminDetail).mockResolvedValue(detail('FORWARDED'));
    vi.mocked(adminSetDueDate).mockResolvedValue(detail('FORWARDED'));
    renderAt();
    const input = await screen.findByLabelText('Prazo informado pela síndica');
    await userEvent.type(input, '2026-11-30');
    await userEvent.click(screen.getByRole('button', { name: 'Salvar prazo' }));
    await waitFor(() => expect(adminSetDueDate).toHaveBeenCalledWith('c1', '2026-11-30'));
  });

  it('mostra autor, unidade e histórico', async () => {
    vi.mocked(adminDetail).mockResolvedValue(detail('OPEN'));
    renderAt();
    expect(await screen.findByText('Ana Souza · T2-51')).toBeInTheDocument();
    expect(screen.getByText(/Aberta por Ana Souza/)).toBeInTheDocument();
  });

  it('encaminhado: salva a resposta da síndica', async () => {
    vi.mocked(adminDetail).mockResolvedValue(detail('FORWARDED'));
    vi.mocked(adminSetSyndicResponse).mockResolvedValue(detail('FORWARDED'));
    renderAt();
    await userEvent.type(
      await screen.findByLabelText('Resposta da síndica (visível aos moradores)'),
      'Pintor vem dia 30.'
    );
    await userEvent.click(screen.getByRole('button', { name: 'Salvar resposta' }));
    await waitFor(() =>
      expect(adminSetSyndicResponse).toHaveBeenCalledWith('c1', 'Pintor vem dia 30.')
    );
  });
});
