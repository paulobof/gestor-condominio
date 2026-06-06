import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('@/lib/api', () => ({
  api: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}));

import { api } from '@/lib/api';
import {
  listClassifieds,
  getClassified,
  createClassified,
  updateClassified,
  deleteClassified,
  uploadClassifiedPhoto,
  deleteClassifiedPhoto,
  getClassifiedPhotoUrl,
} from './classifiedsApi';

const get = vi.mocked(api.get);
const post = vi.mocked(api.post);
const put = vi.mocked(api.put);
const del = vi.mocked(api.delete);

beforeEach(() => {
  vi.clearAllMocks();
});

describe('classifiedsApi — contrato com o backend', () => {
  it('listClassifieds envia status/page/size como params', async () => {
    const page = { content: [], totalElements: 0, totalPages: 0, number: 0 };
    get.mockResolvedValue({ data: page });

    const result = await listClassifieds('ACTIVE', 1, 10);

    expect(get).toHaveBeenCalledWith('/classifieds', {
      params: { status: 'ACTIVE', page: 1, size: 10 },
    });
    expect(result).toBe(page);
  });

  it('listClassifieds usa page=0/size=20 por padrão', async () => {
    get.mockResolvedValue({ data: {} });
    await listClassifieds();
    expect(get).toHaveBeenCalledWith('/classifieds', {
      params: { status: undefined, page: 0, size: 20 },
    });
  });

  it('getClassified usa o id no path', async () => {
    get.mockResolvedValue({ data: { id: 'c1' } });
    await getClassified('c1');
    expect(get).toHaveBeenCalledWith('/classifieds/c1');
  });

  it('createClassified faz POST com o corpo', async () => {
    post.mockResolvedValue({ data: { id: 'c1' } });
    await createClassified({ title: 'Sofá', price: 500 });
    expect(post).toHaveBeenCalledWith('/classifieds', { title: 'Sofá', price: 500 });
  });

  it('updateClassified faz PUT no id', async () => {
    put.mockResolvedValue({ data: { id: 'c1' } });
    await updateClassified('c1', { title: 'Sofá', status: 'SOLD' });
    expect(put).toHaveBeenCalledWith(
      '/classifieds/c1',
      expect.objectContaining({ title: 'Sofá', status: 'SOLD' })
    );
  });

  it('deleteClassified faz DELETE no id', async () => {
    del.mockResolvedValue({ data: undefined });
    await deleteClassified('c1');
    expect(del).toHaveBeenCalledWith('/classifieds/c1');
  });

  it('uploadClassifiedPhoto envia FormData com multipart header', async () => {
    post.mockResolvedValue({ data: { id: 'p1', ordering: 0, contentType: 'image/jpeg' } });
    const file = new File([new Uint8Array([1, 2, 3])], 'foto.jpg', { type: 'image/jpeg' });

    await uploadClassifiedPhoto('c1', file);

    const [url, form, config] = post.mock.calls[0];
    expect(url).toBe('/classifieds/c1/photos');
    expect(form).toBeInstanceOf(FormData);
    expect((form as FormData).get('file')).toBe(file);
    expect(config).toEqual({ headers: { 'Content-Type': 'multipart/form-data' } });
  });

  it('deleteClassifiedPhoto usa id e photoId no path', async () => {
    del.mockResolvedValue({ data: undefined });
    await deleteClassifiedPhoto('c1', 'p1');
    expect(del).toHaveBeenCalledWith('/classifieds/c1/photos/p1');
  });

  it('getClassifiedPhotoUrl extrai a url da resposta', async () => {
    get.mockResolvedValue({ data: { url: 'https://minio/signed' } });
    const url = await getClassifiedPhotoUrl('c1', 'p1');
    expect(get).toHaveBeenCalledWith('/classifieds/c1/photos/p1/url');
    expect(url).toBe('https://minio/signed');
  });
});
