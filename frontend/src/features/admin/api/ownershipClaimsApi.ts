import { api } from '@/lib/api';

/** Espelha OwnershipClaimView (pedido de posse de unidade pendente). */
export interface OwnershipClaim {
  id: string;
  userId: string;
  userName: string | null;
  unitId: string;
  unitCode: string | null;
  proofFilename: string | null;
  proofUploadedAt: string | null;
  createdAt: string;
}

export async function listClaims(page = 0, size = 20) {
  const r = await api.get('/ownership-claims', { params: { page, size } });
  return r.data as { content: OwnershipClaim[]; totalElements: number };
}

export async function approveClaim(id: string) {
  await api.post(`/ownership-claims/${id}/approve`);
}

export async function rejectClaim(id: string, reason: string) {
  await api.post(`/ownership-claims/${id}/reject`, { reason });
}

/** Baixa o comprovante do claim pelo backend (download autenticado e auditado, MinIO privado). */
export async function getClaimProofBlob(id: string): Promise<Blob> {
  const r = await api.get(`/ownership-claims/${id}/proof`, { responseType: 'blob' });
  return r.data as Blob;
}
