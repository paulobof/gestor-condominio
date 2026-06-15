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
vi.mock('../api/recommendationsApi', () => ({
  getRecommendation: vi.fn(),
  getRecommendationPhotoUrl: vi.fn(),
  deleteRecommendation: vi.fn(),
  hideRecommendation: vi.fn(),
  voteRecommendation: vi.fn(),
  listRecommendationComments: vi.fn(),
  addRecommendationComment: vi.fn(),
  deleteRecommendationComment: vi.fn(),
}));
vi.mock('sonner', () => ({ toast: { error: vi.fn(), success: vi.fn() } }));

import { RecommendationDetailPage } from './RecommendationDetailPage';
import { useAuth } from '@/features/auth/useAuth';
import {
  getRecommendation,
  deleteRecommendation,
  hideRecommendation,
  voteRecommendation,
  listRecommendationComments,
  addRecommendationComment,
} from '../api/recommendationsApi';

const useAuthMock = vi.mocked(useAuth);
const getMock = vi.mocked(getRecommendation);
const deleteMock = vi.mocked(deleteRecommendation);
const hideMock = vi.mocked(hideRecommendation);
const voteMock = vi.mocked(voteRecommendation);
const listCommentsMock = vi.mocked(listRecommendationComments);
const addCommentMock = vi.mocked(addRecommendationComment);

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
    comment: 'muito bom',
    recommendedByUserId: 'u1',
    status: 'ACTIVE',
    createdAt: '2026-06-06T00:00:00Z',
    tags: [{ id: 't1', slug: 'encanador', label: 'Encanador', color: null }],
    openingHours: [],
    photos: [],
    likeCount: 0,
    dislikeCount: 0,
    myVote: null,
    commentCount: 0,
    ...over,
  } as never;
}

function setUser(id: string, authorities: string[] = []) {
  useAuthMock.mockReturnValue({ user: { id, authorities } } as never);
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/indicacoes/r1']}>
      <Routes>
        <Route path="/indicacoes/:id" element={<RecommendationDetailPage />} />
      </Routes>
    </MemoryRouter>
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  listCommentsMock.mockResolvedValue([]);
});

describe('RecommendationDetailPage', () => {
  it('renderiza os detalhes da indicação', async () => {
    setUser('u9');
    getMock.mockResolvedValue(reco());
    renderPage();

    expect(await screen.findByRole('heading', { name: 'Encanador Zé' })).toBeInTheDocument();
    expect(screen.getByText('Zé')).toBeInTheDocument();
    expect(screen.getByText('Encanador')).toBeInTheDocument();
    expect(screen.getByText('muito bom')).toBeInTheDocument();
    expect(screen.getByLabelText('Nota 5 de 5')).toBeInTheDocument();
  });

  it('mostra "não encontrada" quando a API falha', async () => {
    setUser('u9');
    getMock.mockRejectedValue(new Error('404'));
    renderPage();
    expect(await screen.findByText('Indicação não encontrada.')).toBeInTheDocument();
  });

  it('autor vê Editar e Excluir', async () => {
    setUser('u1'); // == recommendedByUserId
    getMock.mockResolvedValue(reco());
    renderPage();
    await screen.findByRole('heading', { name: 'Encanador Zé' });

    expect(screen.getByRole('link', { name: /editar/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /excluir/i })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /ocultar/i })).not.toBeInTheDocument();
  });

  it('terceiro sem permissão não vê ações', async () => {
    setUser('u9'); // != autor, sem moderate
    getMock.mockResolvedValue(reco());
    renderPage();
    await screen.findByRole('heading', { name: 'Encanador Zé' });

    expect(screen.queryByRole('link', { name: /editar/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /excluir/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /ocultar/i })).not.toBeInTheDocument();
  });

  it('moderador pode ocultar e a página recarrega', async () => {
    setUser('u9', ['RECOMMENDATION_MODERATE']);
    getMock.mockResolvedValue(reco());
    hideMock.mockResolvedValue(undefined);
    renderPage();
    await screen.findByRole('heading', { name: 'Encanador Zé' });

    await userEvent.click(screen.getByRole('button', { name: /ocultar/i }));

    expect(hideMock).toHaveBeenCalledWith('r1');
    await waitFor(() => expect(getMock).toHaveBeenCalledTimes(2));
  });

  it('autor exclui após confirmar e navega de volta', async () => {
    setUser('u1');
    getMock.mockResolvedValue(reco());
    deleteMock.mockResolvedValue(undefined);
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    renderPage();
    await screen.findByRole('heading', { name: 'Encanador Zé' });

    await userEvent.click(screen.getByRole('button', { name: /excluir/i }));

    expect(deleteMock).toHaveBeenCalledWith('r1');
    await waitFor(() =>
      expect(navigateMock).toHaveBeenCalledWith('/indicacoes', { replace: true })
    );
  });

  it('curtir chama voteRecommendation com LIKE', async () => {
    setUser('u9');
    getMock.mockResolvedValue(reco({ likeCount: 0, myVote: null }));
    voteMock.mockResolvedValue(reco({ likeCount: 1, myVote: 'LIKE' }));
    renderPage();
    await screen.findByRole('heading', { name: 'Encanador Zé' });

    await userEvent.click(screen.getByRole('button', { name: 'Curtir' }));

    await waitFor(() => expect(voteMock).toHaveBeenCalledWith('r1', 'LIKE'));
  });

  it('envia um comentário', async () => {
    setUser('u9');
    getMock.mockResolvedValue(reco());
    addCommentMock.mockResolvedValue({
      id: 'c1',
      authorUserId: 'u9',
      authorName: 'Eu',
      text: 'Top!',
      createdAt: '2026-06-15T00:00:00Z',
    } as never);
    renderPage();
    await screen.findByRole('heading', { name: 'Encanador Zé' });

    await userEvent.type(screen.getByLabelText('Comentário'), 'Top!');
    await userEvent.click(screen.getByRole('button', { name: 'Enviar' }));

    await waitFor(() => expect(addCommentMock).toHaveBeenCalledWith('r1', 'Top!'));
  });
});
