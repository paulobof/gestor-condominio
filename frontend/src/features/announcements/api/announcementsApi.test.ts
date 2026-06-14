import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('@/lib/api', () => ({
  api: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}));

import { api } from '@/lib/api';
import {
  listAnnouncements,
  getAnnouncement,
  createAnnouncement,
  updateAnnouncement,
  deleteAnnouncement,
  reorderAnnouncements,
} from './announcementsApi';

const get = vi.mocked(api.get);
const post = vi.mocked(api.post);
const put = vi.mocked(api.put);
const del = vi.mocked(api.delete);

beforeEach(() => {
  vi.clearAllMocks();
});

describe('announcementsApi — contrato com o backend', () => {
  it('listAnnouncements envia page/size como params', async () => {
    const page = { content: [], totalElements: 0, totalPages: 0, number: 0 };
    get.mockResolvedValue({ data: page });

    const r = await listAnnouncements(2, 10);

    expect(get).toHaveBeenCalledWith('/announcements', { params: { page: 2, size: 10 } });
    expect(r).toBe(page);
  });

  it('listAnnouncements usa page=0/size=20 por padrão', async () => {
    get.mockResolvedValue({ data: {} });
    await listAnnouncements();
    expect(get).toHaveBeenCalledWith('/announcements', { params: { page: 0, size: 20 } });
  });

  it('getAnnouncement usa o id no path', async () => {
    get.mockResolvedValue({ data: { id: 'a1' } });
    await getAnnouncement('a1');
    expect(get).toHaveBeenCalledWith('/announcements/a1');
  });

  it('createAnnouncement faz POST com o corpo incluindo importance', async () => {
    post.mockResolvedValue({ data: { id: 'a1' } });
    await createAnnouncement({ title: 'Manutenção', body: 'corpo', importance: 'HIGH' });
    expect(post).toHaveBeenCalledWith('/announcements', {
      title: 'Manutenção',
      body: 'corpo',
      importance: 'HIGH',
    });
  });

  it('updateAnnouncement faz PUT no id com importance', async () => {
    put.mockResolvedValue({ data: { id: 'a1' } });
    await updateAnnouncement('a1', { title: 'Novo', body: 'corpo', importance: 'LOW' });
    expect(put).toHaveBeenCalledWith(
      '/announcements/a1',
      expect.objectContaining({ title: 'Novo', importance: 'LOW' })
    );
  });

  it('reorderAnnouncements faz PUT em /reorder com os items', async () => {
    put.mockResolvedValue({ data: undefined });
    await reorderAnnouncements([{ id: 'a1', position: 0 }]);
    expect(put).toHaveBeenCalledWith('/announcements/reorder', {
      items: [{ id: 'a1', position: 0 }],
    });
  });

  it('deleteAnnouncement faz DELETE no id', async () => {
    del.mockResolvedValue({ data: undefined });
    await deleteAnnouncement('a1');
    expect(del).toHaveBeenCalledWith('/announcements/a1');
  });
});
