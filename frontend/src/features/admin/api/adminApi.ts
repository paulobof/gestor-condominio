import { api } from '@/lib/api';

export interface PendingRegistration {
  userId: string;
  fullName: string;
  email: string;
  phone: string;
  unitCode: string;
  gender: string | null;
  birthDate: string | null;
  createdAt: string;
}

export async function listPending(page = 0, size = 20) {
  const r = await api.get('/registrations', { params: { page, size } });
  return r.data as { content: PendingRegistration[]; totalElements: number };
}

export async function approveRegistration(userId: string) {
  await api.post(`/registrations/${userId}/approve`);
}

export async function rejectRegistration(userId: string, reason: string) {
  await api.post(`/registrations/${userId}/reject`, { reason });
}
