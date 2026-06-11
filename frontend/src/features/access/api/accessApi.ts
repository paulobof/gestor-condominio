import { api } from '@/lib/api';

export interface RoleBadge {
  id: number;
  label: string;
}

export interface UserAccessRow {
  id: string;
  displayName: string;
  unitLabel: string | null;
  phone: string | null;
  roles: RoleBadge[];
}

export interface PageResult<T> {
  content: T[];
  number: number;
  totalPages: number;
  last: boolean;
}

export interface AssignableRole {
  id: number;
  name: string;
  label: string;
}

export async function listUsers(q: string, page = 0, size = 20) {
  const r = await api.get('/access/users', { params: { q, page, size } });
  return r.data as PageResult<UserAccessRow>;
}

export async function listAssignableRoles() {
  const r = await api.get('/access/roles');
  return r.data as AssignableRole[];
}

export async function getUserRoleIds(userId: string) {
  const r = await api.get(`/access/users/${userId}/roles`);
  return r.data as number[];
}

export async function assignRole(userId: string, roleId: number) {
  await api.post(`/access/users/${userId}/roles/${roleId}`);
}

export async function removeRole(userId: string, roleId: number) {
  await api.delete(`/access/users/${userId}/roles/${roleId}`);
}

export interface CreateUserPayload {
  fullName: string;
  email: string;
  phone: string;
  unitId: string | null;
  roleIds: number[];
}

export interface CreatedUser {
  id: string;
  fullName: string;
  password: string;
}

export async function getCreatableRoles() {
  const r = await api.get('/access/creatable-roles');
  return r.data as AssignableRole[];
}

export async function createUser(payload: CreateUserPayload) {
  const r = await api.post('/access/users', payload);
  return r.data as CreatedUser;
}

export async function deleteUser(id: string) {
  await api.delete(`/access/users/${id}`);
}

export async function lookupUnit(code: string) {
  const r = await api.get('/units/lookup', { params: { code } });
  return r.data as { id: string; code: string };
}
