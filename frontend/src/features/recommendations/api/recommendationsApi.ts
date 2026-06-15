import { api } from '@/lib/api';
import type { Tag } from './tagsApi';

export type RecommendationStatus = 'ACTIVE' | 'HIDDEN';

export type VoteValue = 'LIKE' | 'DISLIKE';

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
  rating: number | null;
  comment: string | null;
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
  myVote: VoteValue | null;
  commentCount: number;
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
  rating?: number | null;
  comment?: string;
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
  body: Omit<RecommendationBody, 'isResident' | 'residentUserId'>
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

export interface RecommendationComment {
  id: string;
  authorUserId: string;
  authorName: string | null;
  text: string;
  createdAt: string;
}

/** Define/alterna o voto (mesmo valor remove). Retorna a indicação com contadores atualizados. */
export async function voteRecommendation(id: string, value: VoteValue) {
  const r = await api.post(`/recommendations/${id}/vote`, { value });
  return r.data as Recommendation;
}

export async function listRecommendationComments(id: string) {
  const r = await api.get(`/recommendations/${id}/comments`);
  return r.data as RecommendationComment[];
}

export async function addRecommendationComment(id: string, text: string) {
  const r = await api.post(`/recommendations/${id}/comments`, { text });
  return r.data as RecommendationComment;
}

export async function deleteRecommendationComment(id: string, commentId: string) {
  await api.delete(`/recommendations/${id}/comments/${commentId}`);
}
