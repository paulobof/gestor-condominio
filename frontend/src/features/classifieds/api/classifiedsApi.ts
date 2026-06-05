import { api } from '@/lib/api';

export type ClassifiedStatus = 'ACTIVE' | 'SOLD' | 'ARCHIVED';

export interface ClassifiedPhoto {
  id: string;
  ordering: number;
  contentType: string;
}

export interface Classified {
  id: string;
  title: string;
  description: string | null;
  price: number | null;
  status: ClassifiedStatus;
  authorUserId: string;
  createdAt: string;
  photos: ClassifiedPhoto[];
}

export interface ClassifiedPage {
  content: Classified[];
  totalElements: number;
  totalPages: number;
  number: number;
}

export async function listClassifieds(status?: ClassifiedStatus, page = 0, size = 20) {
  const r = await api.get('/classifieds', { params: { status, page, size } });
  return r.data as ClassifiedPage;
}

export async function getClassified(id: string) {
  const r = await api.get(`/classifieds/${id}`);
  return r.data as Classified;
}

export async function createClassified(body: {
  title: string;
  description?: string;
  price?: number | null;
}) {
  const r = await api.post('/classifieds', body);
  return r.data as Classified;
}

export async function updateClassified(
  id: string,
  body: { title: string; description?: string; price?: number | null; status?: ClassifiedStatus }
) {
  const r = await api.put(`/classifieds/${id}`, body);
  return r.data as Classified;
}

export async function deleteClassified(id: string) {
  await api.delete(`/classifieds/${id}`);
}

export async function uploadClassifiedPhoto(id: string, file: File) {
  const form = new FormData();
  form.append('file', file);
  const r = await api.post(`/classifieds/${id}/photos`, form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return r.data as ClassifiedPhoto;
}

export async function deleteClassifiedPhoto(id: string, photoId: string) {
  await api.delete(`/classifieds/${id}/photos/${photoId}`);
}

export async function getClassifiedPhotoUrl(id: string, photoId: string) {
  const r = await api.get(`/classifieds/${id}/photos/${photoId}/url`);
  return (r.data as { url: string }).url;
}
