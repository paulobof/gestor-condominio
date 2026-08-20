import { api } from '@/lib/api';

/** Espelha UnitMemberResponse (inclui unitId/unitCode para multi-unidade). */
export interface UnitMember {
  id: string;
  fullName: string;
  greetingName: string;
  email: string;
  phone: string;
  status: string;
  unitId?: string | null;
  unitCode?: string | null;
}

/** Espelha MyUnitView: unidade sob gestão do usuário (seletor de unidade). */
export interface MyUnit {
  unitId: string;
  code: string;
}

/** Espelha UnitMemberDetail (inclui gênero e nascimento, ausentes na lista). */
export interface MemberDetail {
  id: string;
  fullName: string;
  greetingName: string;
  phone: string;
  email: string;
  gender: string | null;
  birthDate: string | null;
}

/** Espelha CreateUnitMemberRequest. unitId opcional: usado quando o master tem >1 unidade. */
export interface CreateMemberPayload {
  fullName: string;
  greetingName: string;
  email: string;
  phone: string;
  gender: string | null;
  birthDate: string | null;
  whatsappOptIn: boolean;
  unitId?: string | null;
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

/** Espelha UnitJoinRequestResponse: pedido de acesso aguardando o responsável da unidade. */
export interface UnitJoinRequest {
  id: string;
  fullName: string;
  greetingName: string | null;
  email: string | null;
  phone: string | null;
  unitId: string | null;
  unitCode: string | null;
  requestedAt: string;
}

export async function listMembers() {
  const r = await api.get('/units/me/members');
  return r.data as UnitMember[];
}

/** Unidades sob gestão do usuário (para o seletor quando há mais de uma). */
export async function listMyUnits() {
  const r = await api.get('/units/me');
  return r.data as MyUnit[];
}

export async function getMemberDetail(id: string) {
  const r = await api.get(`/units/me/members/${id}`);
  return r.data as MemberDetail;
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

/** Pedidos de acesso à minha unidade, feitos por quem se cadastrou informando ela. */
export async function listJoinRequests() {
  const r = await api.get('/units/me/members/requests');
  return r.data as UnitJoinRequest[];
}

export async function approveJoinRequest(id: string) {
  await api.post(`/units/me/members/requests/${id}/approve`);
}

export async function rejectJoinRequest(id: string, reason?: string) {
  await api.post(`/units/me/members/requests/${id}/reject`, { reason: reason ?? null });
}
