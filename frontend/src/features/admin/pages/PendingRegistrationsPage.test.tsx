import { render, screen } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('../api/adminApi', () => ({
  listPending: vi.fn(),
  approveRegistration: vi.fn(),
  rejectRegistration: vi.fn(),
}));

import { PendingRegistrationsPage } from './PendingRegistrationsPage';
import { listPending } from '../api/adminApi';

const listPendingMock = vi.mocked(listPending);

const item = {
  userId: 'u1',
  fullName: 'Fulano de Tal',
  email: 'f@x.com',
  phone: '11999999999',
  unitCode: '702C',
  gender: null,
  birthDate: null,
  createdAt: '2026-01-01T00:00:00Z',
};

beforeEach(() => {
  vi.clearAllMocks();
  listPendingMock.mockResolvedValue({ content: [item], totalElements: 1 } as never);
});

describe('PendingRegistrationsPage', () => {
  it('exibe a data de nascimento em dd/MM/yyyy (não no ISO invertido)', async () => {
    listPendingMock.mockResolvedValue({
      content: [{ ...item, birthDate: '1990-01-02' }],
      totalElements: 1,
    } as never);

    render(<PendingRegistrationsPage />);
    await screen.findByText(/Fulano de Tal/);

    expect(screen.getByText('02/01/1990')).toBeInTheDocument();
    expect(screen.queryByText('1990-01-02')).not.toBeInTheDocument();
  });
});
