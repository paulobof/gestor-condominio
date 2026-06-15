import { api } from '@/lib/api';

/** Registra a posse de uma unidade extra (multipart: unitCode + proof). Requer autenticação. */
export async function createUnitClaim(form: FormData) {
  const r = await api.post('/auth/me/unit-claims', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return r.data;
}
