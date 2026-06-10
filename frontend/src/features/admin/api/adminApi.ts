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

/**
 * Baixa o comprovante pelo backend (download autenticado e auditado; o MinIO
 * permanece privado). Retorna o conteúdo como Blob para abrir/visualizar.
 */
export async function getProofBlob(userId: string): Promise<Blob> {
  const r = await api.get(`/registrations/${userId}/proof`, { responseType: 'blob' });
  return r.data as Blob;
}
