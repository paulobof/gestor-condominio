import { api } from '@/lib/api';

/** Espelha UnitMemberResponse (id, fullName, greetingName, email, phone, status). */
export interface UnitMember {
  id: string;
  fullName: string;
  greetingName: string;
  email: string;
  phone: string;
  status: string;
}

/** Espelha CreateUnitMemberRequest (sem password, sem unitId). */
export interface CreateMemberPayload {
  fullName: string;
  greetingName: string;
  email: string;
  phone: string;
  gender: string | null;
  birthDate: string | null;
  whatsappOptIn: boolean;
}

/** Espelha CreatedUnitMemberResponse: senha provisória mostrada uma única vez. */
export interface CreatedMember {
  id: string;
  fullName: string;
  password: string;
}

/** Espelha UpdateUnitMemberRequest (sem password, sem unitId; greetingName opcional). */
export interface UpdateMemberPayload {
  fullName: string;
  greetingName: string;
  phone: string;
  email: string;
  gender: string | null;
  birthDate: string | null;
}

export async function listMembers() {
  const r = await api.get('/units/me/members');
  return r.data as UnitMember[];
}

export async function createMember(payload: CreateMemberPayload) {
  const r = await api.post('/units/me/members', payload);
  return r.data as CreatedMember;
}

export async function updateMember(id: string, payload: UpdateMemberPayload) {
  await api.put(`/units/me/members/${id}`, payload);
}

export async function deleteMember(id: string) {
  await api.delete(`/units/me/members/${id}`);
}
