import { api } from '@/lib/api';

export interface UserSearchResult {
  id: string;
  displayName: string;
  unitLabel: string | null;
}

export interface AssignableRole {
  id: number;
  name: string;
  label: string;
}

export async function searchUsers(q: string) {
  const r = await api.get('/access/users', { params: { q } });
  return r.data as UserSearchResult[];
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
