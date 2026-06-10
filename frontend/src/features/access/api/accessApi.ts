import { api } from '@/lib/api';

export interface RoleBadge {
  id: number;
  label: string;
}

export interface UserAccessRow {
  id: string;
  displayName: string;
  unitLabel: string | null;
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
