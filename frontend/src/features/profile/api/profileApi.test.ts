import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('@/lib/api', () => ({
  api: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}));

import { api } from '@/lib/api';
import { changeOwnPassword, getOwnProfile, updateOwnProfile } from './profileApi';

const get = vi.mocked(api.get);
const put = vi.mocked(api.put);

const profile = {
  fullName: 'Ana Silva',
  greetingName: 'Ana',
  phone: '5511988887777',
  email: 'ana@x.com',
};

beforeEach(() => vi.clearAllMocks());

describe('profileApi', () => {
  it('getOwnProfile busca /users/me/profile', async () => {
    get.mockResolvedValue({ data: profile });
    await expect(getOwnProfile()).resolves.toEqual(profile);
    expect(get).toHaveBeenCalledWith('/users/me/profile');
  });

  it('updateOwnProfile envia PUT com o corpo', async () => {
    put.mockResolvedValue({ data: profile });
    const body = { fullName: 'Ana Silva', greetingName: null, phone: null };
    await expect(updateOwnProfile(body)).resolves.toEqual(profile);
    expect(put).toHaveBeenCalledWith('/users/me/profile', body);
  });

  it('changeOwnPassword envia senha atual e nova', async () => {
    put.mockResolvedValue({ data: undefined });
    await changeOwnPassword('Atual@123', 'Nova@Senha123');
    expect(put).toHaveBeenCalledWith('/users/me/password', {
      currentPassword: 'Atual@123',
      newPassword: 'Nova@Senha123',
    });
  });
});
