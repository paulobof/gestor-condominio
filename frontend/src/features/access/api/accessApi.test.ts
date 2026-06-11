import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('@/lib/api', () => ({
  api: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}));

import { api } from '@/lib/api';
import {
  listUsers,
  listAssignableRoles,
  getUserRoleIds,
  assignRole,
  removeRole,
  getCreatableRoles,
  createUser,
  deleteUser,
  lookupUnit,
  getUser,
  updateUser,
} from './accessApi';

const get = vi.mocked(api.get);
const post = vi.mocked(api.post);
const del = vi.mocked(api.delete);

beforeEach(() => {
  vi.clearAllMocks();
});

describe('accessApi — contrato com o backend', () => {
  it('listUsers envia q, page e size como params', async () => {
    get.mockResolvedValue({ data: { content: [], number: 0, totalPages: 0, last: true } });
    await listUsers('ana', 1, 20);
    expect(get).toHaveBeenCalledWith('/access/users', { params: { q: 'ana', page: 1, size: 20 } });
  });

  it('listAssignableRoles faz GET em /access/roles', async () => {
    get.mockResolvedValue({ data: [] });
    await listAssignableRoles();
    expect(get).toHaveBeenCalledWith('/access/roles');
  });

  it('getUserRoleIds usa o id no path', async () => {
    get.mockResolvedValue({ data: [6] });
    const r = await getUserRoleIds('u1');
    expect(get).toHaveBeenCalledWith('/access/users/u1/roles');
    expect(r).toEqual([6]);
  });

  it('assignRole faz POST no path do usuário/role', async () => {
    post.mockResolvedValue({ data: undefined });
    await assignRole('u1', 6);
    expect(post).toHaveBeenCalledWith('/access/users/u1/roles/6');
  });

  it('removeRole faz DELETE no path do usuário/role', async () => {
    del.mockResolvedValue({ data: undefined });
    await removeRole('u1', 6);
    expect(del).toHaveBeenCalledWith('/access/users/u1/roles/6');
  });

  it('getCreatableRoles faz GET em /access/creatable-roles', async () => {
    get.mockResolvedValue({ data: [] });
    await getCreatableRoles();
    expect(get).toHaveBeenCalledWith('/access/creatable-roles');
  });

  it('createUser faz POST em /access/users com o payload', async () => {
    post.mockResolvedValue({ data: { id: 'u9', fullName: 'Ana', password: 'X1y!aaaa' } });
    const out = await createUser({
      fullName: 'Ana',
      email: 'ana@x.com',
      phone: '+5511999999999',
      unitId: null,
      roleIds: [4],
    });
    expect(post).toHaveBeenCalledWith('/access/users', {
      fullName: 'Ana',
      email: 'ana@x.com',
      phone: '+5511999999999',
      unitId: null,
      roleIds: [4],
    });
    expect(out.password).toBe('X1y!aaaa');
  });

  it('deleteUser faz DELETE no path do usuário', async () => {
    del.mockResolvedValue({ data: undefined });
    await deleteUser('u9');
    expect(del).toHaveBeenCalledWith('/access/users/u9');
  });

  it('lookupUnit faz GET em /units/lookup com o code', async () => {
    get.mockResolvedValue({ data: { id: 'unit1', code: '101A' } });
    const out = await lookupUnit('101A');
    expect(get).toHaveBeenCalledWith('/units/lookup', { params: { code: '101A' } });
    expect(out.id).toBe('unit1');
  });

  it('getUser faz GET em /access/users/:id', async () => {
    get.mockResolvedValue({ data: { id: 'u1', fullName: 'Ana', email: 'ana@x.com' } });
    const out = await getUser('u1');
    expect(get).toHaveBeenCalledWith('/access/users/u1');
    expect(out.email).toBe('ana@x.com');
  });

  it('updateUser faz PUT em /access/users/:id com o payload', async () => {
    const put = vi.mocked(api.put);
    put.mockResolvedValue({ data: undefined });
    const payload = {
      fullName: 'Ana',
      greetingName: 'Ana',
      phone: '+5511999999999',
      unitId: null,
      email: 'ana@x.com',
      gender: 'FEMALE',
      birthDate: '1990-01-02',
    };
    await updateUser('u1', payload);
    expect(put).toHaveBeenCalledWith('/access/users/u1', payload);
  });
});
