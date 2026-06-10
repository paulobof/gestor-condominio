import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('../api/adminApi', () => ({
  listPending: vi.fn(),
  approveRegistration: vi.fn(),
  rejectRegistration: vi.fn(),
  getProofUrl: vi.fn(),
  getProofBlob: vi.fn(),
}));

import { PendingRegistrationsPage } from './PendingRegistrationsPage';
import { listPending, getProofBlob } from '../api/adminApi';

const listPendingMock = vi.mocked(listPending);
const getProofBlobMock = vi.mocked(getProofBlob);

const item = {
  userId: 'u1',
  fullName: 'Fulano de Tal',
  email: 'f@x.com',
  phone: '11999999999',
  unitCode: '702C',
  gender: null,
  birthDate: null,
  residenceProofFilename: 'comprovante.png',
  residenceProofUploadedAt: '2026-01-01T00:00:00Z',
  createdAt: '2026-01-01T00:00:00Z',
};

beforeEach(() => {
  vi.clearAllMocks();
  listPendingMock.mockResolvedValue({ content: [item], totalElements: 1 } as never);
});

describe('PendingRegistrationsPage', () => {
  it('abre o comprovante via blob autenticado (não URL pública)', async () => {
    const user = userEvent.setup();
    const blob = new Blob(['x'], { type: 'image/png' });
    getProofBlobMock.mockResolvedValue(blob as never);

    const createObjectURL = vi.fn(() => 'blob:fake-url');
    const revokeObjectURL = vi.fn();
    URL.createObjectURL = createObjectURL as never;
    URL.revokeObjectURL = revokeObjectURL as never;
    const open = vi.fn();
    window.open = open as never;

    render(<PendingRegistrationsPage />);
    await screen.findByText(/Fulano de Tal/);
    await user.click(screen.getByRole('button', { name: /ver comprovante/i }));

    await waitFor(() => expect(getProofBlobMock).toHaveBeenCalledWith('u1'));
    expect(createObjectURL).toHaveBeenCalledWith(blob);
    expect(open).toHaveBeenCalledWith('blob:fake-url', '_blank', expect.any(String));
  });
});
