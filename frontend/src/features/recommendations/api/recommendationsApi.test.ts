import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('@/lib/api', () => ({
  api: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}));

import { api } from '@/lib/api';
import {
  listRecommendations,
  getRecommendation,
  createRecommendation,
  updateRecommendation,
  deleteRecommendation,
  hideRecommendation,
  uploadRecommendationPhoto,
  deleteRecommendationPhoto,
  getRecommendationPhotoUrl,
  listReviews,
  createReview,
  updateReview,
  deleteReview,
  hideReview,
  type RecommendationBody,
  type ReviewBody,
} from './recommendationsApi';

const get = vi.mocked(api.get);
const post = vi.mocked(api.post);
const put = vi.mocked(api.put);
const del = vi.mocked(api.delete);

beforeEach(() => {
  vi.clearAllMocks();
});

describe('recommendationsApi — contrato com o backend', () => {
  it('listRecommendations envia filtros como query params e retorna a página', async () => {
    const page = { content: [], totalElements: 0, totalPages: 0, number: 0 };
    get.mockResolvedValue({ data: page });

    const result = await listRecommendations({ tag: 'encanador', search: 'ze', page: 2 });

    expect(get).toHaveBeenCalledWith('/recommendations', {
      params: { tag: 'encanador', search: 'ze', page: 2 },
    });
    expect(result).toBe(page);
  });

  it('getRecommendation usa o id no path', async () => {
    get.mockResolvedValue({ data: { id: 'r1' } });
    await getRecommendation('r1');
    expect(get).toHaveBeenCalledWith('/recommendations/r1');
  });

  it('createRecommendation faz POST com o corpo', async () => {
    const body: RecommendationBody = {
      serviceName: 'Encanador Zé',
      isResident: false,
      tagSlugs: ['encanador'],
      openingHours: [],
    };
    post.mockResolvedValue({ data: { id: 'r1' } });

    await createRecommendation(body);

    expect(post).toHaveBeenCalledWith('/recommendations', body);
  });

  it('updateRecommendation faz PUT no id', async () => {
    put.mockResolvedValue({ data: { id: 'r1' } });
    await updateRecommendation('r1', {
      serviceName: 'Novo',
      tagSlugs: [],
      openingHours: [],
    });
    expect(put).toHaveBeenCalledWith(
      '/recommendations/r1',
      expect.objectContaining({ serviceName: 'Novo' })
    );
  });

  it('deleteRecommendation faz DELETE no id', async () => {
    del.mockResolvedValue({ data: undefined });
    await deleteRecommendation('r1');
    expect(del).toHaveBeenCalledWith('/recommendations/r1');
  });

  it('hideRecommendation faz POST em /hide sem body', async () => {
    post.mockResolvedValue({ data: undefined });
    await hideRecommendation('r1');
    expect(post).toHaveBeenCalledWith('/recommendations/r1/hide');
  });

  it('uploadRecommendationPhoto envia FormData com multipart header', async () => {
    post.mockResolvedValue({ data: { id: 'p1', ordering: 0, contentType: 'image/jpeg' } });
    const file = new File([new Uint8Array([1, 2, 3])], 'foto.jpg', { type: 'image/jpeg' });

    await uploadRecommendationPhoto('r1', file);

    expect(post).toHaveBeenCalledTimes(1);
    const [url, form, config] = post.mock.calls[0];
    expect(url).toBe('/recommendations/r1/photos');
    expect(form).toBeInstanceOf(FormData);
    expect((form as FormData).get('file')).toBe(file);
    expect(config).toEqual({ headers: { 'Content-Type': 'multipart/form-data' } });
  });

  it('deleteRecommendationPhoto usa id e photoId no path', async () => {
    del.mockResolvedValue({ data: undefined });
    await deleteRecommendationPhoto('r1', 'p1');
    expect(del).toHaveBeenCalledWith('/recommendations/r1/photos/p1');
  });

  it('getRecommendationPhotoUrl extrai a url da resposta', async () => {
    get.mockResolvedValue({ data: { url: 'https://minio/signed' } });
    const url = await getRecommendationPhotoUrl('r1', 'p1');
    expect(get).toHaveBeenCalledWith('/recommendations/r1/photos/p1/url');
    expect(url).toBe('https://minio/signed');
  });

  it('listReviews faz GET em /reviews', async () => {
    const reviews = [
      {
        id: 'rv1',
        sentiment: 'LIKE',
        rating: 5,
        text: 'Ótimo atendimento, recomendo muito.',
        createdAt: '2026-06-06T00:00:00Z',
        mine: false,
        authorName: null,
        unitCode: null,
      },
    ];
    get.mockResolvedValue({ data: reviews });

    const result = await listReviews('r1');

    expect(get).toHaveBeenCalledWith('/recommendations/r1/reviews');
    expect(result).toBe(reviews);
  });

  it('createReview faz POST em /reviews com o corpo e retorna a avaliação criada', async () => {
    const body: ReviewBody = { sentiment: 'LIKE', rating: 5, text: 'Muito bom, recomendo.' };
    const created = { id: 'rv1', ...body, createdAt: '2026-06-06T00:00:00Z', mine: true };
    post.mockResolvedValue({ data: created });

    const result = await createReview('r1', body);

    expect(post).toHaveBeenCalledWith('/recommendations/r1/reviews', body);
    expect(result).toBe(created);
  });

  it('updateReview faz PUT em /reviews/{reviewId} com o corpo', async () => {
    const body: ReviewBody = { sentiment: 'DISLIKE', rating: 2, text: 'Não gostei do serviço.' };
    put.mockResolvedValue({ data: { id: 'rv1', ...body } });

    await updateReview('r1', 'rv1', body);

    expect(put).toHaveBeenCalledWith('/recommendations/r1/reviews/rv1', body);
  });

  it('deleteReview faz DELETE em /reviews/{reviewId}', async () => {
    del.mockResolvedValue({ data: undefined });
    await deleteReview('r1', 'rv1');
    expect(del).toHaveBeenCalledWith('/recommendations/r1/reviews/rv1');
  });

  it('hideReview faz POST em /reviews/{reviewId}/hide sem body', async () => {
    post.mockResolvedValue({ data: undefined });
    await hideReview('r1', 'rv1');
    expect(post).toHaveBeenCalledWith('/recommendations/r1/reviews/rv1/hide');
  });
});
