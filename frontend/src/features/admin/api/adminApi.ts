import { api } from '@/lib/api';

export interface PendingRegistration {
  userId: string;
  fullName: string;
  email: string;
  phone: string;
  unitCode: string;
  gender: string | null;
  birthDate: string | null;
  residenceProofFilename: string;
  residenceProofUploadedAt: string;
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

export async function getProofUrl(userId: string): Promise<string> {
  const r = await api.get(`/registrations/${userId}/proof-url`);
  return (r.data as { url: string }).url;
}
