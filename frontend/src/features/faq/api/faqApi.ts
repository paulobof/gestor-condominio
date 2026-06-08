import { api } from '@/lib/api';

export interface Faq {
  id: string;
  question: string;
  answer: string;
  category: string;
  published: boolean;
  ordering: number;
  updatedAt: string;
}

export interface FaqBody {
  question: string;
  answer: string;
  category: string;
  published: boolean;
}

export async function listFaq() {
  return (await api.get('/faq')).data as Faq[];
}

export async function listAllFaq() {
  return (await api.get('/faq/all')).data as Faq[];
}

export async function createFaq(b: FaqBody) {
  return (await api.post('/faq', b)).data as Faq;
}

export async function updateFaq(id: string, b: FaqBody) {
  return (await api.put(`/faq/${id}`, b)).data as Faq;
}

export async function setFaqPublished(id: string, published: boolean) {
  return (await api.put(`/faq/${id}/publish`, { published })).data as Faq;
}

export async function reorderFaq(items: { id: string; ordering: number }[]) {
  await api.put('/faq/reorder', { items });
}

export async function deleteFaq(id: string) {
  await api.delete(`/faq/${id}`);
}
