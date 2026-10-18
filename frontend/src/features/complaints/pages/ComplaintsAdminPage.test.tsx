import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('../api/complaintsApi', async (orig) => {
  const actual = await orig<typeof import('../api/complaintsApi')>();
  return { ...actual, adminQueue: vi.fn() };
});

import { ComplaintsAdminPage } from './ComplaintsAdminPage';
import { adminQueue, type Complaint } from '../api/complaintsApi';

const c: Complaint = {
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
  status: 'OPEN',
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
};

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(adminQueue).mockResolvedValue({
    content: [{ complaint: c, authorName: 'Ana Souza', unitCode: 'T2-51' }],
    totalElements: 1,
    totalPages: 1,
    number: 0,
  });
});

describe('ComplaintsAdminPage', () => {
  it('lista pendentes com autor e unidade', async () => {
    render(
      <MemoryRouter>
        <ComplaintsAdminPage />
      </MemoryRouter>
    );
    expect(await screen.findByText(/Ana Souza · T2-51/)).toBeInTheDocument();
    expect(adminQueue).toHaveBeenCalledWith(['OPEN', 'IN_REVIEW', 'READY'], 0);
  });

  it('troca de aba consulta o status correspondente', async () => {
    render(
      <MemoryRouter>
        <ComplaintsAdminPage />
      </MemoryRouter>
    );
    await screen.findByText(/Ana Souza/);
    await userEvent.click(screen.getByRole('tab', { name: 'Encaminhados' }));
    expect(adminQueue).toHaveBeenLastCalledWith(['FORWARDED'], 0);
  });

  it('tem atalho para encaminhar à síndica', async () => {
    render(
      <MemoryRouter>
        <ComplaintsAdminPage />
      </MemoryRouter>
    );
    expect(screen.getByRole('link', { name: /encaminhar à síndica/i })).toHaveAttribute(
      'href',
      '/admin/chamados/encaminhar'
    );
  });
});
