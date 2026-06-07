import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('../api/recommendationsApi', () => ({ listRecommendations: vi.fn() }));
vi.mock('../api/tagsApi', () => ({ searchTags: vi.fn() }));
vi.mock('sonner', () => ({ toast: { error: vi.fn(), success: vi.fn() } }));

import { RecommendationsListPage } from './RecommendationsListPage';
import { listRecommendations } from '../api/recommendationsApi';
import { searchTags } from '../api/tagsApi';

const listMock = vi.mocked(listRecommendations);
const searchTagsMock = vi.mocked(searchTags);

function page(content: unknown[]) {
  return { content, totalElements: content.length, totalPages: 1, number: 0 } as never;
}

function reco(over: Record<string, unknown> = {}) {
  return {
    id: 'r1',
    serviceName: 'Encanador Zé',
    professionalName: 'Zé',
    phone: null,
    isResident: false,
    residentUserId: null,
    addressLine: null,
    priceRange: null,
    rating: 5,
    comment: null,
    recommendedByUserId: 'u2',
    status: 'ACTIVE',
    createdAt: '2026-06-06T00:00:00Z',
    tags: [],
    openingHours: [],
    photos: [],
    ...over,
  };
}

function renderPage() {
  return render(
    <MemoryRouter>
      <RecommendationsListPage />
    </MemoryRouter>
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  searchTagsMock.mockResolvedValue([]);
});

describe('RecommendationsListPage', () => {
  it('lista as indicações e marca "Mora aqui" para morador', async () => {
    listMock.mockResolvedValue(page([reco({ isResident: true })]));
    renderPage();
    expect(await screen.findByText('Encanador Zé')).toBeInTheDocument();
    expect(screen.getByText('Mora aqui')).toBeInTheDocument();
  });

  it('mostra estado vazio', async () => {
    listMock.mockResolvedValue(page([]));
    renderPage();
    expect(await screen.findByText('Nenhuma indicação.')).toBeInTheDocument();
  });

  it('marcar "Só moradores" refaz a busca com residentOnly', async () => {
    listMock.mockResolvedValue(page([]));
    renderPage();
    await screen.findByText('Nenhuma indicação.');

    await userEvent.click(screen.getByRole('checkbox', { name: /só moradores/i }));

    await waitFor(() =>
      expect(listMock).toHaveBeenLastCalledWith(expect.objectContaining({ residentOnly: true }))
    );
  });

  it('digitar busca refaz a query com o termo', async () => {
    listMock.mockResolvedValue(page([]));
    renderPage();
    await screen.findByText('Nenhuma indicação.');

    await userEvent.type(screen.getByLabelText('Buscar'), 'sofa');

    await waitFor(() =>
      expect(listMock).toHaveBeenLastCalledWith(expect.objectContaining({ search: 'sofa' }))
    );
  });

  it('autocomplete de tag chama searchTags com o texto digitado', async () => {
    listMock.mockResolvedValue(page([]));
    searchTagsMock.mockResolvedValue([
      { id: 't1', slug: 'encanador', label: 'Encanador', color: null },
    ]);
    renderPage();
    await screen.findByText('Nenhuma indicação.');

    await userEvent.type(screen.getByLabelText('Filtrar por tag'), 'enc');

    await waitFor(() => expect(searchTagsMock).toHaveBeenCalledWith('enc'));
    expect(await screen.findByRole('button', { name: 'Encanador' })).toBeInTheDocument();
  });
});
