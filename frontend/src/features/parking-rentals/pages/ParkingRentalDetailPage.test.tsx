import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';

const navigateMock = vi.fn();

vi.mock('react-router-dom', async (orig) => {
  const actual = await orig<typeof import('react-router-dom')>();
  return { ...actual, useNavigate: () => navigateMock };
});
vi.mock('@/features/auth/useAuth', () => ({ useAuth: vi.fn() }));
vi.mock('../api/parkingRentalsApi', () => ({
  getParkingRental: vi.fn(),
  deleteParkingRental: vi.fn(),
  updateParkingRental: vi.fn(),
}));
vi.mock('sonner', () => ({ toast: { error: vi.fn(), success: vi.fn() } }));

import { ParkingRentalDetailPage } from './ParkingRentalDetailPage';
import { useAuth } from '@/features/auth/useAuth';
import {
  getParkingRental,
  deleteParkingRental,
  updateParkingRental,
} from '../api/parkingRentalsApi';

const useAuthMock = vi.mocked(useAuth);
const getMock = vi.mocked(getParkingRental);
const deleteMock = vi.mocked(deleteParkingRental);
const updateMock = vi.mocked(updateParkingRental);

function rental(over: Record<string, unknown> = {}) {
  return {
    id: 'r1',
    tower: 'A',
    floor: '-1',
    spotNumber: '045',
    monthlyPrice: 350,
    status: 'ACTIVE',
    authorUserId: 'u1',
    createdAt: '2026-06-14T00:00:00Z',
    authorName: 'Ana Costa',
    authorPhone: '11999990000',
    authorWhatsapp: '5511999990000',
    ...over,
  } as never;
}

function setUser(id: string, authorities: string[] = []) {
  useAuthMock.mockReturnValue({ user: { id, authorities } } as never);
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/vagas/aluguel/r1']}>
      <Routes>
        <Route path="/vagas/aluguel/:id" element={<ParkingRentalDetailPage />} />
      </Routes>
    </MemoryRouter>
  );
}

beforeEach(() => vi.clearAllMocks());

describe('ParkingRentalDetailPage', () => {
  it('renderiza a vaga e o anunciante', async () => {
    setUser('u9');
    getMock.mockResolvedValue(rental());
    renderPage();
    expect(
      await screen.findByRole('heading', { name: 'Torre A · Andar -1 · Vaga 045' })
    ).toBeInTheDocument();
    expect(screen.getByText('Ana Costa')).toBeInTheDocument();
  });

  it('mostra botão WhatsApp quando há authorWhatsapp', async () => {
    setUser('u9');
    getMock.mockResolvedValue(rental());
    renderPage();
    const link = await screen.findByRole('link', { name: /falar no whatsapp/i });
    expect(link).toHaveAttribute('href', 'https://wa.me/5511999990000');
  });

  it('não mostra botão WhatsApp quando authorWhatsapp é null', async () => {
    setUser('u9');
    getMock.mockResolvedValue(rental({ authorWhatsapp: null }));
    renderPage();
    await screen.findByText('Ana Costa');
    expect(screen.queryByRole('link', { name: /falar no whatsapp/i })).not.toBeInTheDocument();
  });

  it('terceiro sem permissão não vê ações', async () => {
    setUser('u9');
    getMock.mockResolvedValue(rental());
    renderPage();
    await screen.findByText('Ana Costa');
    expect(screen.queryByRole('button', { name: /excluir/i })).not.toBeInTheDocument();
  });

  it('autor vê ações e marca como alugada', async () => {
    setUser('u1');
    getMock.mockResolvedValue(rental());
    updateMock.mockResolvedValue(rental({ status: 'RENTED' }));
    renderPage();
    await screen.findByText('Ana Costa');

    await userEvent.click(screen.getByRole('button', { name: /marcar como alugada/i }));
    await waitFor(() =>
      expect(updateMock).toHaveBeenCalledWith('r1', {
        tower: 'A',
        floor: '-1',
        spotNumber: '045',
        monthlyPrice: 350,
        status: 'RENTED',
      })
    );
  });

  it('autor de vaga alugada disponibiliza novamente (RENTED → ACTIVE)', async () => {
    setUser('u1');
    getMock.mockResolvedValue(rental({ status: 'RENTED' }));
    updateMock.mockResolvedValue(rental({ status: 'ACTIVE' }));
    renderPage();
    await screen.findByText('Ana Costa');

    // numa vaga alugada não cabe "marcar como alugada"
    expect(screen.queryByRole('button', { name: /marcar como alugada/i })).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: /disponibilizar novamente/i }));
    await waitFor(() =>
      expect(updateMock).toHaveBeenCalledWith('r1', {
        tower: 'A',
        floor: '-1',
        spotNumber: '045',
        monthlyPrice: 350,
        status: 'ACTIVE',
      })
    );
  });

  it('moderador exclui após confirmar e volta para a lista', async () => {
    setUser('u9', ['PARKING_RENTAL_MODERATE']);
    getMock.mockResolvedValue(rental());
    deleteMock.mockResolvedValue(undefined);
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    renderPage();
    await screen.findByText('Ana Costa');

    await userEvent.click(screen.getByRole('button', { name: /excluir/i }));
    expect(deleteMock).toHaveBeenCalledWith('r1');
    await waitFor(() =>
      expect(navigateMock).toHaveBeenCalledWith('/vagas/aluguel', { replace: true })
    );
  });
});
