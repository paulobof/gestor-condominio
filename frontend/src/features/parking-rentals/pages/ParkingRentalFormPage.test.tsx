import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';

const navigateMock = vi.fn();

vi.mock('react-router-dom', async (orig) => {
  const actual = await orig<typeof import('react-router-dom')>();
  return { ...actual, useNavigate: () => navigateMock };
});
vi.mock('../api/parkingRentalsApi', () => ({
  createParkingRental: vi.fn(),
  getParkingRental: vi.fn(),
  updateParkingRental: vi.fn(),
}));
vi.mock('sonner', () => ({ toast: { error: vi.fn(), success: vi.fn() } }));

import { ParkingRentalFormPage } from './ParkingRentalFormPage';
import { createParkingRental } from '../api/parkingRentalsApi';

const createMock = vi.mocked(createParkingRental);

function renderNew() {
  return render(
    <MemoryRouter initialEntries={['/vagas/aluguel/novo']}>
      <Routes>
        <Route path="/vagas/aluguel/novo" element={<ParkingRentalFormPage />} />
      </Routes>
    </MemoryRouter>
  );
}

beforeEach(() => vi.clearAllMocks());

describe('ParkingRentalFormPage (novo)', () => {
  it('tem os 4 campos', () => {
    renderNew();
    expect(screen.getByLabelText('Torre')).toBeInTheDocument();
    expect(screen.getByLabelText('Andar')).toBeInTheDocument();
    expect(screen.getByLabelText('Numeração da vaga')).toBeInTheDocument();
    expect(screen.getByLabelText('Valor mensal em R$')).toBeInTheDocument();
  });

  it('cria a vaga e navega para o detalhe', async () => {
    createMock.mockResolvedValue({ id: 'r9' } as never);
    renderNew();

    await userEvent.type(screen.getByLabelText('Torre'), 'A');
    await userEvent.type(screen.getByLabelText('Andar'), '-1');
    await userEvent.type(screen.getByLabelText('Numeração da vaga'), '045');
    await userEvent.type(screen.getByLabelText('Valor mensal em R$'), '350,00');
    await userEvent.click(screen.getByRole('button', { name: /anunciar vaga/i }));

    await waitFor(() =>
      expect(createMock).toHaveBeenCalledWith({
        tower: 'A',
        floor: '-1',
        spotNumber: '045',
        monthlyPrice: 350,
      })
    );
    await waitFor(() =>
      expect(navigateMock).toHaveBeenCalledWith('/vagas/aluguel/r9', { replace: true })
    );
  });

  it('não envia com valor zero/ inválido', async () => {
    renderNew();
    await userEvent.type(screen.getByLabelText('Torre'), 'A');
    await userEvent.type(screen.getByLabelText('Andar'), '-1');
    await userEvent.type(screen.getByLabelText('Numeração da vaga'), '045');
    await userEvent.type(screen.getByLabelText('Valor mensal em R$'), '0');
    await userEvent.click(screen.getByRole('button', { name: /anunciar vaga/i }));
    expect(createMock).not.toHaveBeenCalled();
  });
});
