import { api } from '@/lib/api';
import type { Tag } from './tagsApi';

export type RecommendationStatus = 'ACTIVE' | 'HIDDEN';

export interface OpeningHours {
  dayOfWeek: number;
  opensAt: string | null;
  closesAt: string | null;
  notes: string | null;
}

export interface RecommendationPhoto {
  id: string;
  ordering: number;
  contentType: string;
}

export interface Recommendation {
  id: string;
  serviceName: string;
  professionalName: string | null;
  phone: string | null;
  isResident: boolean;
  residentUserId: string | null;
  addressLine: string | null;
  priceRange: string | null;
  recommendedByUserId: string;
  status: RecommendationStatus;
  createdAt: string;
  tags: Tag[];
  openingHours: OpeningHours[];
  photos: RecommendationPhoto[];
  instagramUrl: string | null;
  facebookUrl: string | null;
  whatsappUrl: string | null;
  catalogUrl: string | null;
  ownerUnitId: string | null;
  ownerUnitCode: string | null;
  likeCount: number;
  dislikeCount: number;
  /** Total de avaliações. A média (não devolvida pelo backend) é `ratingSum / reviewCount`. */
  reviewCount: number;
  ratingSum: number;
}

export interface RecommendationPage {
  content: Recommendation[];
  totalElements: number;
  totalPages: number;
  number: number;
}

export interface RecommendationFilters {
  tag?: string;
  search?: string;
  page?: number;
  size?: number;
}

export async function listRecommendations(f: RecommendationFilters = {}) {
  const r = await api.get('/recommendations', { params: f });
  return r.data as RecommendationPage;
}

export async function getRecommendation(id: string) {
  const r = await api.get(`/recommendations/${id}`);
  return r.data as Recommendation;
}

export interface RecommendationBody {
  serviceName: string;
  professionalName?: string;
  phone?: string;
  isResident: boolean;
  residentUserId?: string | null;
  addressLine?: string;
  priceRange?: string;
  /**
   * Primeira avaliação da indicação — só na criação, e só quando o autor tem unidade (regra de
   * domínio validada pelo backend: manda os três ou nenhum). PUT não aceita estes campos.
   */
  sentiment?: ReviewSentiment;
  rating?: number;
  text?: string;
  tagSlugs: string[];
  openingHours: OpeningHours[];
  instagramUrl?: string | null;
  facebookUrl?: string | null;
  whatsappUrl?: string | null;
  catalogUrl?: string | null;
}

export async function createRecommendation(body: RecommendationBody) {
  const r = await api.post('/recommendations', body);
  return r.data as Recommendation;
}

export async function updateRecommendation(
  id: string,
  body: Omit<RecommendationBody, 'isResident' | 'residentUserId' | 'sentiment' | 'rating' | 'text'>
) {
  const r = await api.put(`/recommendations/${id}`, body);
  return r.data as Recommendation;
}

export async function deleteRecommendation(id: string) {
  await api.delete(`/recommendations/${id}`);
}

export async function hideRecommendation(id: string) {
  await api.post(`/recommendations/${id}/hide`);
}

export async function uploadRecommendationPhoto(id: string, file: File) {
  const form = new FormData();
  form.append('file', file);
  const r = await api.post(`/recommendations/${id}/photos`, form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return r.data as RecommendationPhoto;
}

export async function deleteRecommendationPhoto(id: string, photoId: string) {
  await api.delete(`/recommendations/${id}/photos/${photoId}`);
}

export async function getRecommendationPhotoUrl(id: string, photoId: string) {
  const r = await api.get(`/recommendations/${id}/photos/${photoId}/url`);
  return (r.data as { url: string }).url;
}

// ---- Avaliações (like/dislike + nota + comentário obrigatório) ------------------------

export type ReviewSentiment = 'LIKE' | 'DISLIKE';

/**
 * Avaliação como o app a exibe. `authorName`/`unitCode` vêm nulos para quase todo mundo — só
 * quem tem RECOMMENDATION_MODERATE ou mora na mesma unidade da avaliação os recebe preenchidos.
 * Essa decisão é do backend: nunca reimplementar aqui, só exibir o que veio.
 *
 * `sameUnit` é a metade "mesma unidade" dessa revelação, separada do motivo "modera": só ela diz
 * se É O MEU apartamento que avaliou. Usar isso (não `authorName != null`, que também fica
 * preenchido pra quem só modera) pra decidir se mostra "seu apartamento já avaliou".
 */
export interface Review {
  id: string;
  sentiment: ReviewSentiment;
  rating: number;
  text: string;
  createdAt: string;
  mine: boolean;
  authorName: string | null;
  unitCode: string | null;
  sameUnit: boolean;
}

export interface ReviewBody {
  sentiment: ReviewSentiment;
  rating: number;
  text: string;
}

export async function listReviews(id: string) {
  const r = await api.get(`/recommendations/${id}/reviews`);
  return r.data as Review[];
}

export async function createReview(id: string, body: ReviewBody) {
  const r = await api.post(`/recommendations/${id}/reviews`, body);
  return r.data as Review;
}

export async function updateReview(id: string, reviewId: string, body: ReviewBody) {
  const r = await api.put(`/recommendations/${id}/reviews/${reviewId}`, body);
  return r.data as Review;
}

export async function deleteReview(id: string, reviewId: string) {
  await api.delete(`/recommendations/${id}/reviews/${reviewId}`);
}

export async function hideReview(id: string, reviewId: string) {
  await api.post(`/recommendations/${id}/reviews/${reviewId}/hide`);
}
