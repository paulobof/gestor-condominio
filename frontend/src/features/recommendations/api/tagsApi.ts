import { api } from '@/lib/api';

export interface Tag {
  id: string;
  slug: string;
  label: string;
  color: string | null;
}

export async function searchTags(q: string) {
  const r = await api.get('/tags', { params: { q } });
  return r.data as Tag[];
}

export async function createTag(slug: string, label?: string) {
  const r = await api.post('/tags', { slug, label });
  return r.data as Tag;
}
