import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('../api/recommendationsApi', () => ({ listRecommendations: vi.fn() }));
vi.mock('../api/tagsApi', () => ({ searchTags: vi.fn() }));
vi.mock('@/features/auth/useAuth', () => ({ useAuth: vi.fn() }));
vi.mock('sonner', () => ({ toast: { error: vi.fn(), success: vi.fn() } }));

import { RecommendationsListPage } from './RecommendationsListPage';
import { listRecommendations } from '../api/recommendationsApi';
import { searchTags } from '../api/tagsApi';
import { useAuth } from '@/features/auth/useAuth';

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
    reviewCount: 0,
    ratingSum: 0,
    recommendedByUserId: 'u2',
    status: 'ACTIVE',
    createdAt: '2026-06-06T00:00:00Z',
    tags: [],
    openingHours: [],
    photos: [],
    likeCount: 0,
    dislikeCount: 0,
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
  vi.mocked(useAuth).mockReturnValue({ user: { id: 'u1', authorities: [] } } as never);
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

  it('digitar busca refaz a query com o termo', async () => {
    listMock.mockResolvedValue(page([]));
    renderPage();
    await screen.findByText('Nenhuma indicação.');

    await userEvent.type(screen.getByLabelText('Buscar'), 'sofa');

    await waitFor(() =>
      expect(listMock).toHaveBeenLastCalledWith(expect.objectContaining({ search: 'sofa' }))
    );
  });

  it('mostra a média e o total de avaliações quando há avaliações', async () => {
    listMock.mockResolvedValue(page([reco({ reviewCount: 12, ratingSum: 52 })]));
    renderPage();
    expect(await screen.findByText('★ 4,3 · 12 avaliações')).toBeInTheDocument();
  });

  it('mostra "Sem avaliações" quando reviewCount é zero, nunca "★ 0,0"', async () => {
    listMock.mockResolvedValue(page([reco({ reviewCount: 0, ratingSum: 0 })]));
    renderPage();
    expect(await screen.findByText('Sem avaliações')).toBeInTheDocument();
    expect(screen.queryByText(/★/)).not.toBeInTheDocument();
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

  it('visitante não vê o botão "Nova indicação"', async () => {
    vi.mocked(useAuth).mockReturnValue({ user: null } as never);
    listMock.mockResolvedValue(page([]));
    renderPage();
    await screen.findByText('Nenhuma indicação.');
    expect(screen.queryByRole('link', { name: /nova indicação/i })).not.toBeInTheDocument();
  });

  it('logado vê o botão "Nova indicação"', async () => {
    listMock.mockResolvedValue(page([]));
    renderPage();
    await screen.findByText('Nenhuma indicação.');
    expect(screen.getByRole('link', { name: /nova indicação/i })).toBeInTheDocument();
  });

  it('Enter no campo de tag aplica a primeira sugestão e filtra', async () => {
    listMock.mockResolvedValue(page([]));
    searchTagsMock.mockResolvedValue([{ id: 't1', slug: 'vidro', label: 'Vidro', color: null }]);
    renderPage();
    await screen.findByText('Nenhuma indicação.');

    const input = screen.getByLabelText('Filtrar por tag');
    await userEvent.type(input, 'vid');
    await screen.findByRole('button', { name: 'Vidro' });
    await userEvent.type(input, '{Enter}');

    await waitFor(() =>
      expect(listMock).toHaveBeenLastCalledWith(expect.objectContaining({ tag: 'vidro' }))
    );
  });

  it('avisa quando nenhuma tag casa com o texto', async () => {
    listMock.mockResolvedValue(page([]));
    searchTagsMock.mockResolvedValue([]);
    renderPage();
    await screen.findByText('Nenhuma indicação.');

    await userEvent.type(screen.getByLabelText('Filtrar por tag'), 'xyz');
    expect(await screen.findByText('Nenhuma tag encontrada.')).toBeInTheDocument();
  });
});
