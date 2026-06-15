import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('../api/parkingRentalsApi', () => ({ listParkingRentals: vi.fn() }));
vi.mock('sonner', () => ({ toast: { error: vi.fn(), success: vi.fn() } }));

import { ParkingRentalsListPage } from './ParkingRentalsListPage';
import { listParkingRentals } from '../api/parkingRentalsApi';

const listMock = vi.mocked(listParkingRentals);

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
    authorName: null,
    authorPhone: null,
    authorWhatsapp: null,
    ...over,
  };
}

function page(content: ReturnType<typeof rental>[]) {
  return { content, totalElements: content.length, totalPages: 1, number: 0 } as never;
}

function renderPage() {
  return render(
    <MemoryRouter>
      <ParkingRentalsListPage />
    </MemoryRouter>
  );
}

beforeEach(() => vi.clearAllMocks());

describe('ParkingRentalsListPage', () => {
  it('renderiza os cards com torre/andar/vaga e valor/mês', async () => {
    listMock.mockResolvedValue(page([rental()]));
    renderPage();
    expect(await screen.findByText('Torre A · Andar -1 · Vaga 045')).toBeInTheDocument();
    expect(screen.getByText(/R\$\s?350,00\/mês/)).toBeInTheDocument();
  });

  it('mostra estado vazio', async () => {
    listMock.mockResolvedValue(page([]));
    renderPage();
    expect(await screen.findByText('Nenhuma vaga anunciada.')).toBeInTheDocument();
  });

  it('filtra por status ao clicar na aba', async () => {
    listMock.mockResolvedValue(page([]));
    renderPage();
    await screen.findByText('Nenhuma vaga anunciada.');
    await userEvent.click(screen.getByRole('tab', { name: 'Alugados' }));
    await waitFor(() => expect(listMock).toHaveBeenCalledWith('RENTED'));
  });

  it('tem botão de anunciar vaga', async () => {
    listMock.mockResolvedValue(page([]));
    renderPage();
    expect(await screen.findByRole('link', { name: /anunciar vaga/i })).toHaveAttribute(
      'href',
      '/vagas/aluguel/novo'
    );
  });
});
