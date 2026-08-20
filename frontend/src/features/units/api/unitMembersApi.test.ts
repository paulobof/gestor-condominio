import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('@/lib/api', () => ({
  api: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}));

import { api } from '@/lib/api';
import {
  listMembers,
  getMemberDetail,
  createMember,
  updateMember,
  deleteMember,
  listJoinRequests,
  approveJoinRequest,
  rejectJoinRequest,
  type CreateMemberPayload,
  type UpdateMemberPayload,
} from './unitMembersApi';

const get = vi.mocked(api.get);
const post = vi.mocked(api.post);
const put = vi.mocked(api.put);
const del = vi.mocked(api.delete);

beforeEach(() => {
  vi.clearAllMocks();
});

describe('unitMembersApi — contrato com o backend', () => {
  it('listMembers faz GET em /units/me/members e devolve a lista', async () => {
    get.mockResolvedValue({
      data: [
        {
          id: 'm1',
          fullName: 'Bia Souza',
          greetingName: 'Bia',
          email: 'bia@x.com',
          phone: '+5511988887777',
          status: 'ACTIVE',
        },
      ],
    });
    const out = await listMembers();
    expect(get).toHaveBeenCalledWith('/units/me/members');
    expect(out).toHaveLength(1);
    expect(out[0].fullName).toBe('Bia Souza');
  });

  it('getMemberDetail faz GET em /units/me/members/:id e devolve gênero e nascimento', async () => {
    get.mockResolvedValue({
      data: {
        id: 'm1',
        fullName: 'Bia Souza',
        greetingName: 'Bia',
        phone: '+5511988887777',
        email: 'bia@x.com',
        gender: 'FEMALE',
        birthDate: '1990-01-02',
      },
    });
    const out = await getMemberDetail('m1');
    expect(get).toHaveBeenCalledWith('/units/me/members/m1');
    expect(out.gender).toBe('FEMALE');
    expect(out.birthDate).toBe('1990-01-02');
  });

  it('createMember faz POST em /units/me/members com o payload e devolve a senha 1x', async () => {
    post.mockResolvedValue({ data: { id: 'm9', fullName: 'Bia Souza', password: 'X1y!aaaa' } });
    const payload: CreateMemberPayload = {
      fullName: 'Bia Souza',
      greetingName: 'Bia',
      email: 'bia@x.com',
      phone: '+5511988887777',
      gender: 'FEMALE',
      birthDate: '1990-01-02',
      whatsappOptIn: true,
    };
    const out = await createMember(payload);
    expect(post).toHaveBeenCalledWith('/units/me/members', payload);
    expect(out.password).toBe('X1y!aaaa');
  });

  it('updateMember faz PUT em /units/me/members/:id com o payload', async () => {
    put.mockResolvedValue({ data: undefined });
    const payload: UpdateMemberPayload = {
      fullName: 'Bia Souza',
      greetingName: 'Bia',
      phone: '+5511988887777',
      email: 'bia@x.com',
      gender: 'FEMALE',
      birthDate: '1990-01-02',
    };
    await updateMember('m1', payload);
    expect(put).toHaveBeenCalledWith('/units/me/members/m1', payload);
  });

  it('deleteMember faz DELETE no path do morador', async () => {
    del.mockResolvedValue({ data: undefined });
    await deleteMember('m9');
    expect(del).toHaveBeenCalledWith('/units/me/members/m9');
  });

  it('listJoinRequests faz GET em /units/me/members/requests', async () => {
    get.mockResolvedValue({
      data: [
        {
          id: 'r1',
          fullName: 'Maria Souza',
          greetingName: 'Maria',
          email: 'maria@x.com',
          phone: '+5511988887777',
          unitId: 'u1',
          unitCode: '702C',
          requestedAt: '2026-08-20T12:00:00Z',
        },
      ],
    });
    const out = await listJoinRequests();
    expect(get).toHaveBeenCalledWith('/units/me/members/requests');
    expect(out[0].unitCode).toBe('702C');
  });

  it('approveJoinRequest faz POST no endpoint de aprovacao', async () => {
    post.mockResolvedValue({ data: null });
    await approveJoinRequest('r1');
    expect(post).toHaveBeenCalledWith('/units/me/members/requests/r1/approve');
  });

  it('rejectJoinRequest envia o motivo no corpo', async () => {
    post.mockResolvedValue({ data: null });
    await rejectJoinRequest('r1', 'nao conheco');
    expect(post).toHaveBeenCalledWith('/units/me/members/requests/r1/reject', {
      reason: 'nao conheco',
    });
  });
});
