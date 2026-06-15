import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('../api/ownershipClaimsApi', () => ({
  listClaims: vi.fn(),
  approveClaim: vi.fn(),
  rejectClaim: vi.fn(),
  getClaimProofBlob: vi.fn(),
}));
vi.mock('sonner', () => ({ toast: { error: vi.fn(), success: vi.fn() } }));

import { OwnershipClaimsPage } from './OwnershipClaimsPage';
import { listClaims, approveClaim, rejectClaim } from '../api/ownershipClaimsApi';

const listMock = vi.mocked(listClaims);
const approveMock = vi.mocked(approveClaim);
const rejectMock = vi.mocked(rejectClaim);

const CLAIM = {
  id: 'c1',
  userId: 'u1',
  userName: 'Ana Costa',
  unitId: 'uA',
  unitCode: '702A',
  proofFilename: 'comprovante.pdf',
  proofUploadedAt: '2026-06-15T00:00:00Z',
  createdAt: '2026-06-15T00:00:00Z',
};

beforeEach(() => {
  vi.clearAllMocks();
  listMock.mockResolvedValue({ content: [CLAIM], totalElements: 1 });
  approveMock.mockResolvedValue(undefined);
  rejectMock.mockResolvedValue(undefined);
});

describe('OwnershipClaimsPage', () => {
  it('lista os pedidos pendentes com solicitante e unidade', async () => {
    render(<OwnershipClaimsPage />);
    expect(await screen.findByText(/Ana Costa — Unidade 702A/)).toBeInTheDocument();
    expect(screen.getByText('comprovante.pdf')).toBeInTheDocument();
  });

  it('aprovar chama approveClaim e recarrega', async () => {
    const user = userEvent.setup();
    render(<OwnershipClaimsPage />);
    await screen.findByText(/Ana Costa/);
    await user.click(screen.getByRole('button', { name: /aprovar/i }));
    await waitFor(() => expect(approveMock).toHaveBeenCalledWith('c1'));
  });

  it('rejeitar exige motivo e chama rejectClaim', async () => {
    const user = userEvent.setup();
    render(<OwnershipClaimsPage />);
    await screen.findByText(/Ana Costa/);
    await user.click(screen.getByRole('button', { name: /^rejeitar$/i }));
    await user.type(screen.getByLabelText(/motivo da rejeição/i), 'ilegível');
    await user.click(screen.getByRole('button', { name: /confirmar rejeição/i }));
    await waitFor(() => expect(rejectMock).toHaveBeenCalledWith('c1', 'ilegível'));
  });
});
