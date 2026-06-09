import { api } from '@/lib/api';

export interface InfoSection {
  id: string;
  title: string;
  body: string;
  position: number;
  updatedAt: string;
}

export interface InfoSectionBody {
  title: string;
  body: string;
}

export async function listSections() {
  return (await api.get('/info-sections')).data as InfoSection[];
}

export async function createSection(b: InfoSectionBody) {
  return (await api.post('/info-sections', b)).data as InfoSection;
}

export async function updateSection(id: string, b: InfoSectionBody) {
  return (await api.put(`/info-sections/${id}`, b)).data as InfoSection;
}

export async function reorderSections(items: { id: string; position: number }[]) {
  await api.put('/info-sections/reorder', { items });
}

export async function deleteSection(id: string) {
  await api.delete(`/info-sections/${id}`);
}
