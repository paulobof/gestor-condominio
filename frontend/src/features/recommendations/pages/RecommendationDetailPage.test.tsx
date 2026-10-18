import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';

const navigateMock = vi.fn();
const promptLoginMock = vi.fn();

vi.mock('react-router-dom', async (orig) => {
  const actual = await orig<typeof import('react-router-dom')>();
  return { ...actual, useNavigate: () => navigateMock };
});
vi.mock('@/features/auth/useAuth', () => ({ useAuth: vi.fn() }));
vi.mock('@/features/auth/useLoginPrompt', () => ({
  useLoginPrompt: () => ({ promptLogin: promptLoginMock }),
}));
vi.mock('../api/recommendationsApi', () => ({
  getRecommendation: vi.fn(),
  getRecommendationPhotoUrl: vi.fn(),
  deleteRecommendation: vi.fn(),
  hideRecommendation: vi.fn(),
  listReviews: vi.fn(),
  createReview: vi.fn(),
  updateReview: vi.fn(),
  deleteReview: vi.fn(),
  hideReview: vi.fn(),
}));
vi.mock('sonner', () => ({ toast: { error: vi.fn(), success: vi.fn() } }));

import { toast } from 'sonner';
import { RecommendationDetailPage } from './RecommendationDetailPage';
import { useAuth } from '@/features/auth/useAuth';
import {
  getRecommendation,
  deleteRecommendation,
  hideRecommendation,
  listReviews,
  createReview,
  updateReview,
  deleteReview,
  hideReview,
} from '../api/recommendationsApi';

const useAuthMock = vi.mocked(useAuth);
const getMock = vi.mocked(getRecommendation);
const deleteMock = vi.mocked(deleteRecommendation);
const hideMock = vi.mocked(hideRecommendation);
const listReviewsMock = vi.mocked(listReviews);
const createReviewMock = vi.mocked(createReview);
const updateReviewMock = vi.mocked(updateReview);
const deleteReviewMock = vi.mocked(deleteReview);
const hideReviewMock = vi.mocked(hideReview);

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
    rating: null,
    comment: null,
    recommendedByUserId: 'u1',
    status: 'ACTIVE',
    createdAt: '2026-06-06T00:00:00Z',
    tags: [{ id: 't1', slug: 'encanador', label: 'Encanador', color: null }],
    openingHours: [],
    photos: [],
    instagramUrl: null,
    facebookUrl: null,
    whatsappUrl: null,
    catalogUrl: null,
    ownerUnitId: null,
    ownerUnitCode: null,
    likeCount: 0,
    dislikeCount: 0,
    ...over,
  } as never;
}

function review(over: Record<string, unknown> = {}) {
  return {
    id: 'rv1',
    sentiment: 'LIKE',
    rating: 5,
    text: 'Muito bom atendimento, recomendo.',
    createdAt: '2026-06-06T00:00:00Z',
    mine: false,
    authorName: null,
    unitCode: null,
    sameUnit: false,
    ...over,
  } as never;
}

/**
 * `unitId` default 'unit-1': quase todo teste aqui é de morador. Passe `null` para o síndico —
 * quem não tem unidade lê as avaliações mas não vê o formulário (spec §3).
 */
function setUser(id: string | null, authorities: string[] = [], unitId: string | null = 'unit-1') {
  useAuthMock.mockReturnValue({ user: id ? { id, authorities, unitId } : null } as never);
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
  listReviewsMock.mockResolvedValue([]);
});

describe('RecommendationDetailPage', () => {
  it('renderiza os detalhes da indicação', async () => {
    setUser('u9');
    getMock.mockResolvedValue(reco());
    renderPage();

    expect(await screen.findByRole('heading', { name: 'Encanador Zé' })).toBeInTheDocument();
    expect(screen.getByText('Zé')).toBeInTheDocument();
    expect(screen.getByText('Encanador')).toBeInTheDocument();
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
    expect(screen.queryByRole('button', { name: /^ocultar$/i })).not.toBeInTheDocument();
  });

  it('terceiro sem permissão não vê ações de gestão da indicação', async () => {
    setUser('u9'); // != autor, sem moderate
    getMock.mockResolvedValue(reco());
    renderPage();
    await screen.findByRole('heading', { name: 'Encanador Zé' });

    expect(screen.queryByRole('link', { name: /editar/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /excluir/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^ocultar$/i })).not.toBeInTheDocument();
  });

  it('moderador pode ocultar a indicação e a página recarrega', async () => {
    setUser('u9', ['RECOMMENDATION_MODERATE']);
    getMock.mockResolvedValue(reco());
    hideMock.mockResolvedValue(undefined);
    renderPage();
    await screen.findByRole('heading', { name: 'Encanador Zé' });

    await userEvent.click(screen.getByRole('button', { name: /^ocultar$/i }));

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

  it('lista as avaliações, assinando "Morador do condomínio" quando authorName vem nulo', async () => {
    setUser('u9');
    getMock.mockResolvedValue(reco());
    listReviewsMock.mockResolvedValue([review({ authorName: null })]);
    renderPage();

    await screen.findByRole('heading', { name: 'Encanador Zé' });

    expect(screen.getByText('Morador do condomínio')).toBeInTheDocument();
    expect(screen.getByText('Muito bom atendimento, recomendo.')).toBeInTheDocument();
  });

  it('assina com o nome revelado quando authorName vem preenchido', async () => {
    setUser('u9');
    getMock.mockResolvedValue(reco());
    listReviewsMock.mockResolvedValue([review({ authorName: 'Ana' })]);
    renderPage();

    await screen.findByRole('heading', { name: 'Encanador Zé' });

    expect(screen.getByText('Ana')).toBeInTheDocument();
  });

  it('visitante vê botão de avaliar que abre o login', async () => {
    setUser(null);
    getMock.mockResolvedValue(reco());
    renderPage();
    await screen.findByRole('heading', { name: 'Encanador Zé' });

    const cta = screen.getByRole('button', { name: 'Avaliar' });
    await userEvent.click(cta);

    expect(promptLoginMock).toHaveBeenCalledWith({ reason: 'Avaliar é para quem tem conta.' });
    expect(screen.queryByLabelText('Comentário')).not.toBeInTheDocument();
  });

  it('morador cujo apartamento já avaliou (não é o autor) vê a mensagem sem editar/apagar', async () => {
    setUser('u9');
    getMock.mockResolvedValue(reco());
    listReviewsMock.mockResolvedValue([review({ mine: false, authorName: 'Ana', sameUnit: true })]);
    renderPage();
    await screen.findByRole('heading', { name: 'Encanador Zé' });

    expect(screen.getByText('Seu apartamento já avaliou, por Ana.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /editar/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /apagar/i })).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Comentário')).not.toBeInTheDocument();
  });

  it('autor da avaliação vê editar/apagar', async () => {
    setUser('u9');
    getMock.mockResolvedValue(reco());
    listReviewsMock.mockResolvedValue([review({ mine: true, authorName: 'Você', sameUnit: true })]);
    renderPage();
    await screen.findByRole('heading', { name: 'Encanador Zé' });

    expect(screen.getByRole('button', { name: /editar/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /apagar/i })).toBeInTheDocument();
  });

  it('moderador cujo apartamento não avaliou vê o formulário mesmo revelando o nome de outra unidade', async () => {
    // Regressão do bug: authorName vem preenchido pra quem modera (qualquer unidade), mas
    // sameUnit só é true quando é de fato a unidade do ator. Sem essa distinção, o moderador
    // caía no ramo "seu apartamento já avaliou" mesmo sem nunca ter avaliado.
    setUser('u9', ['RECOMMENDATION_MODERATE']);
    getMock.mockResolvedValue(reco());
    listReviewsMock.mockResolvedValue([
      review({ mine: false, authorName: 'Ana', sameUnit: false }),
    ]);
    renderPage();
    await screen.findByRole('heading', { name: 'Encanador Zé' });

    expect(screen.getByLabelText('Comentário')).toBeInTheDocument();
    expect(screen.queryByText('Seu apartamento já avaliou, por Ana.')).not.toBeInTheDocument();
  });

  it('morador sem avaliação vê o formulário e criar avaliação recarrega a lista', async () => {
    setUser('u9');
    getMock.mockResolvedValue(reco());
    listReviewsMock.mockResolvedValueOnce([]).mockResolvedValueOnce([review({ mine: true })]);
    createReviewMock.mockResolvedValue(review({ mine: true }));
    renderPage();
    await screen.findByRole('heading', { name: 'Encanador Zé' });

    await userEvent.click(screen.getByRole('button', { name: '5 estrelas' }));
    await userEvent.type(screen.getByLabelText('Comentário'), 'Muito bom, recomendo demais.');
    await userEvent.click(screen.getByRole('button', { name: /publicar avaliação/i }));

    await waitFor(() =>
      expect(createReviewMock).toHaveBeenCalledWith('r1', {
        sentiment: 'LIKE',
        rating: 5,
        text: 'Muito bom, recomendo demais.',
      })
    );
    await waitFor(() => expect(listReviewsMock).toHaveBeenCalledTimes(2));
  });

  it('mostra mensagem específica quando o backend recusa por REVIEW_UNIT_ALREADY_REVIEWED', async () => {
    setUser('u9');
    getMock.mockResolvedValue(reco());
    listReviewsMock.mockResolvedValue([]);
    createReviewMock.mockRejectedValue({
      response: { data: { code: 'REVIEW_UNIT_ALREADY_REVIEWED' } },
    });
    renderPage();
    await screen.findByRole('heading', { name: 'Encanador Zé' });

    await userEvent.click(screen.getByRole('button', { name: '3 estrelas' }));
    await userEvent.type(screen.getByLabelText('Comentário'), 'Comentário válido com 10+.');
    await userEvent.click(screen.getByRole('button', { name: /publicar avaliação/i }));

    await waitFor(() =>
      expect(toast.error).toHaveBeenCalledWith('Este apartamento já avaliou esta indicação.')
    );
  });

  it('editar a própria avaliação chama updateReview e recarrega', async () => {
    setUser('u9');
    getMock.mockResolvedValue(reco());
    const mine = review({
      mine: true,
      authorName: 'Você',
      sameUnit: true,
      rating: 3,
      text: 'Texto original aqui.',
    });
    listReviewsMock.mockResolvedValue([mine]);
    updateReviewMock.mockResolvedValue(review({ mine: true, rating: 4 }));
    renderPage();
    await screen.findByRole('heading', { name: 'Encanador Zé' });

    await userEvent.click(screen.getByRole('button', { name: /editar/i }));
    expect(screen.getByLabelText('Comentário')).toHaveValue('Texto original aqui.');

    await userEvent.click(screen.getByRole('button', { name: '4 estrelas' }));
    await userEvent.click(screen.getByRole('button', { name: /salvar avaliação/i }));

    await waitFor(() =>
      expect(updateReviewMock).toHaveBeenCalledWith('r1', 'rv1', {
        sentiment: 'LIKE',
        rating: 4,
        text: 'Texto original aqui.',
      })
    );
  });

  it('apagar a própria avaliação após confirmar chama deleteReview', async () => {
    setUser('u9');
    getMock.mockResolvedValue(reco());
    listReviewsMock.mockResolvedValue([review({ mine: true, authorName: 'Você', sameUnit: true })]);
    deleteReviewMock.mockResolvedValue(undefined);
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    renderPage();
    await screen.findByRole('heading', { name: 'Encanador Zé' });

    await userEvent.click(screen.getByRole('button', { name: /apagar/i }));

    await waitFor(() => expect(deleteReviewMock).toHaveBeenCalledWith('r1', 'rv1'));
  });

  it('síndico (sem unidade) lê as avaliações mas não vê o formulário', async () => {
    // Spec §3: "Síndico (sem unidade) lê e não avalia". Sem isso o formulário aparecia e ele só
    // descobria o 403 depois de escrever tudo e enviar.
    setUser('u9', ['RECOMMENDATION_MODERATE'], null);
    getMock.mockResolvedValue(reco());
    listReviewsMock.mockResolvedValue([review({ authorName: 'Ana' })]);
    renderPage();
    await screen.findByRole('heading', { name: 'Encanador Zé' });

    expect(screen.getByText('Muito bom atendimento, recomendo.')).toBeInTheDocument();
    expect(screen.queryByLabelText('Comentário')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /publicar avaliação/i })).not.toBeInTheDocument();
    expect(screen.getByText(/não está vinculada a uma unidade/i)).toBeInTheDocument();
  });

  it('a nota da avaliação é anunciada como texto, não como estrelas cruas', async () => {
    // aria-label num <p> é ignorado; sem role="img" o leitor de tela recita '★★★★★'.
    setUser('u9');
    getMock.mockResolvedValue(reco());
    listReviewsMock.mockResolvedValue([review({ rating: 4, sentiment: 'DISLIKE' })]);
    renderPage();
    await screen.findByRole('heading', { name: 'Encanador Zé' });

    expect(screen.getByRole('img', { name: 'Nota 4 de 5, não recomenda' })).toBeInTheDocument();
  });

  it('moderador pode ocultar uma avaliação individual', async () => {
    setUser('u9', ['RECOMMENDATION_MODERATE']);
    getMock.mockResolvedValue(reco());
    listReviewsMock.mockResolvedValue([review({ authorName: 'Ana' })]);
    hideReviewMock.mockResolvedValue(undefined);
    renderPage();
    await screen.findByRole('heading', { name: 'Encanador Zé' });

    await userEvent.click(screen.getByRole('button', { name: 'Ocultar avaliação' }));

    await waitFor(() => expect(hideReviewMock).toHaveBeenCalledWith('r1', 'rv1'));
  });
});
