import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('@/lib/api', () => ({
  api: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}));

import { api } from '@/lib/api';
import {
  adminQueue,
  createBatch,
  listBoard,
  previewBatch,
  uploadComplaintPhoto,
} from './complaintsApi';

const get = vi.mocked(api.get);
const post = vi.mocked(api.post);

beforeEach(() => {
  vi.clearAllMocks();
  get.mockResolvedValue({ data: {} } as never);
  post.mockResolvedValue({ data: {} } as never);
});

describe('complaintsApi', () => {
  it('listBoard envia filtros como query params', async () => {
    await listBoard({ typeId: 't1', status: 'FORWARDED', page: 1 });
    expect(get).toHaveBeenCalledWith('/complaints/board', {
      params: { typeId: 't1', locationId: undefined, status: 'FORWARDED', page: 1, size: 20 },
    });
  });

  it('adminQueue repete o parametro status', async () => {
    await adminQueue(['OPEN', 'READY'], 0);
    expect(get).toHaveBeenCalledWith('/admin/complaints', {
      params: { status: ['OPEN', 'READY'], page: 0, size: 20 },
      paramsSerializer: { indexes: null },
    });
  });

  it('upload usa multipart com campo file', async () => {
    const file = new File(['x'], 'a.jpg', { type: 'image/jpeg' });
    await uploadComplaintPhoto('c1', file);
    const [url, form] = post.mock.calls[0];
    expect(url).toBe('/complaints/c1/photos');
    expect((form as FormData).get('file')).toBe(file);
  });

  it('lote: preview manda os ids; criação manda ids e o texto exibido', async () => {
    await previewBatch(['a', 'b']);
    await createBatch(['a', 'b'], 'TEXTO');
    expect(post).toHaveBeenNthCalledWith(1, '/admin/complaints/batches/preview', {
      ids: ['a', 'b'],
    });
    expect(post).toHaveBeenNthCalledWith(2, '/admin/complaints/batches', {
      ids: ['a', 'b'],
      expectedText: 'TEXTO',
    });
  });
});
