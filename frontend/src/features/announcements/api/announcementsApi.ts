import { api } from '@/lib/api';

export interface Announcement {
  id: string;
  title: string;
  body: string;
  position: number;
  publishedAt: string;
  authorUserId: string;
  updatedAt: string;
}

export interface AnnouncementPage {
  content: Announcement[];
  totalElements: number;
  totalPages: number;
  number: number;
}

export interface AnnouncementBody {
  title: string;
  body: string;
}

export async function listAnnouncements(page = 0, size = 20) {
  const r = await api.get('/announcements', { params: { page, size } });
  return r.data as AnnouncementPage;
}

export async function getAnnouncement(id: string) {
  const r = await api.get(`/announcements/${id}`);
  return r.data as Announcement;
}

export async function createAnnouncement(body: AnnouncementBody) {
  const r = await api.post('/announcements', body);
  return r.data as Announcement;
}

export async function updateAnnouncement(id: string, body: AnnouncementBody) {
  const r = await api.put(`/announcements/${id}`, body);
  return r.data as Announcement;
}

export async function reorderAnnouncements(items: { id: string; position: number }[]) {
  await api.put('/announcements/reorder', { items });
}

export async function deleteAnnouncement(id: string) {
  await api.delete(`/announcements/${id}`);
}
