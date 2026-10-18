import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';

const navigateMock = vi.fn();
vi.mock('react-router-dom', async (orig) => {
  const actual = await orig<typeof import('react-router-dom')>();
  return { ...actual, useNavigate: () => navigateMock };
});
vi.mock('sonner', () => ({ toast: { error: vi.fn(), success: vi.fn() } }));
vi.mock('../api/complaintsApi', async (orig) => {
  const actual = await orig<typeof import('../api/complaintsApi')>();
  return {
    ...actual,
    getComplaint: vi.fn(),
    cancelComplaint: vi.fn(),
    getComplaintPhotoUrl: vi.fn(),
  };
});

import { ComplaintDetailPage } from './ComplaintDetailPage';
import { cancelComplaint, getComplaint, type Complaint } from '../api/complaintsApi';

const open: Complaint = {
  id: 'c1',
  code: 5,
  typeId: 't',
  typeName: 'Limpeza',
  subtypeId: 's',
  subtypeName: 'Garagem',
  locationId: 'l',
  locationName: 'Garagem',
  locationDetail: null,
  description: 'Óleo no piso',
  status: 'OPEN',
  dueDate: null,
  rejectionReason: null,
  resolutionNote: null,
  createdAt: '2026-10-10T12:00:00Z',
  forwardedAt: null,
  resolvedAt: null,
  editable: true,
  photos: [],
};

function renderAt(id: string) {
  return render(
    <MemoryRouter initialEntries={[`/chamados/${id}`]}>
      <Routes>
        <Route path="/chamados/:id" element={<ComplaintDetailPage />} />
      </Routes>
    </MemoryRouter>
  );
}

beforeEach(() => vi.clearAllMocks());

describe('ComplaintDetailPage', () => {
  it('autor de chamado aberto pode editar e cancelar (com confirmação)', async () => {
    vi.mocked(getComplaint).mockResolvedValue(open);
    renderAt('c1');
    expect(await screen.findByRole('link', { name: /editar/i })).toHaveAttribute(
      'href',
      '/chamados/c1/editar'
    );
    await userEvent.click(screen.getByRole('button', { name: /cancelar chamado/i }));
    await userEvent.click(screen.getByRole('button', { name: /sim, cancelar/i }));
    await waitFor(() => expect(cancelComplaint).toHaveBeenCalledWith('c1'));
    expect(navigateMock).toHaveBeenCalledWith('/chamados', { replace: true });
  });

  it('chamado não editável não mostra ações', async () => {
    vi.mocked(getComplaint).mockResolvedValue({ ...open, status: 'FORWARDED', editable: false });
    renderAt('c1');
    await screen.findByText('Óleo no piso');
    expect(screen.queryByRole('button', { name: /cancelar chamado/i })).not.toBeInTheDocument();
  });

  it('mostra nota de resolução', async () => {
    vi.mocked(getComplaint).mockResolvedValue({
      ...open,
      status: 'RESOLVED',
      editable: false,
      resolutionNote: 'Limpo pela equipe',
    });
    renderAt('c1');
    expect(await screen.findByText('Resolução: Limpo pela equipe')).toBeInTheDocument();
  });

  it('mostra datas de instante no fuso de São Paulo', async () => {
    vi.mocked(getComplaint).mockResolvedValue({ ...open, createdAt: '2026-10-18T02:30:00Z' });
    renderAt('c1');
    expect(await screen.findByText('Aberto em 17/10/2026')).toBeInTheDocument();
  });
});
