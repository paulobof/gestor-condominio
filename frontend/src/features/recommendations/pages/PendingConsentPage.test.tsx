import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('../api/recommendationsApi', () => ({
  listPendingConsent: vi.fn(),
  respondConsent: vi.fn(),
}));
vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

import { PendingConsentPage } from './PendingConsentPage';
import { listPendingConsent, respondConsent } from '../api/recommendationsApi';

const listMock = vi.mocked(listPendingConsent);
const respondMock = vi.mocked(respondConsent);

function renderPage() {
  return render(
    <MemoryRouter>
      <PendingConsentPage />
    </MemoryRouter>
  );
}

const pending = {
  id: 'r1',
  serviceName: 'Encanador Zé',
  professionalName: 'Zé',
  phone: null,
  isResident: true,
  residentUserId: 'u1',
  addressLine: null,
  priceRange: null,
  rating: null,
  comment: 'muito bom',
  recommendedByUserId: 'u2',
  status: 'PENDING_RESIDENT_CONSENT' as const,
  createdAt: '2026-06-06T00:00:00Z',
  tags: [],
  openingHours: [],
  photos: [],
};

beforeEach(() => {
  vi.clearAllMocks();
});

describe('PendingConsentPage', () => {
  it('lista as indicações pendentes do morador', async () => {
    listMock.mockResolvedValue([pending]);
    renderPage();
    expect(await screen.findByText('Encanador Zé')).toBeInTheDocument();
  });

  it('mostra estado vazio quando não há pendências', async () => {
    listMock.mockResolvedValue([]);
    renderPage();
    expect(await screen.findByText(/nenhuma indicação pendente/i)).toBeInTheDocument();
  });

  it('autorizar chama respondConsent(id, true) e recarrega a lista', async () => {
    listMock.mockResolvedValueOnce([pending]).mockResolvedValueOnce([]);
    respondMock.mockResolvedValue(undefined);
    renderPage();

    await userEvent.click(await screen.findByRole('button', { name: /autorizar/i }));

    expect(respondMock).toHaveBeenCalledWith('r1', true);
    await waitFor(() => expect(listMock).toHaveBeenCalledTimes(2));
  });

  it('recusar chama respondConsent(id, false)', async () => {
    listMock.mockResolvedValue([pending]);
    respondMock.mockResolvedValue(undefined);
    renderPage();

    await userEvent.click(await screen.findByRole('button', { name: /recusar/i }));

    expect(respondMock).toHaveBeenCalledWith('r1', false);
  });
});
