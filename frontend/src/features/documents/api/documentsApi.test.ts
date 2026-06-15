import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('@/lib/api', () => ({
  api: { get: vi.fn(), post: vi.fn(), delete: vi.fn() },
}));

import { api } from '@/lib/api';
import { listDocuments, uploadDocument, getDocumentBlob, deleteDocument } from './documentsApi';

const get = vi.mocked(api.get);
const post = vi.mocked(api.post);
const del = vi.mocked(api.delete);

beforeEach(() => {
  vi.clearAllMocks();
});

describe('documentsApi', () => {
  it('listDocuments faz GET /documents', async () => {
    get.mockResolvedValue({ data: [] } as never);
    await listDocuments();
    expect(get).toHaveBeenCalledWith('/documents');
  });

  it('uploadDocument envia multipart com title/type/file', async () => {
    post.mockResolvedValue({ data: { id: 'd1' } } as never);
    const file = new File(['%PDF'], 'ri.pdf', { type: 'application/pdf' });

    await uploadDocument('RI 2026', 'RI', file);

    expect(post).toHaveBeenCalledTimes(1);
    const [url, form, config] = post.mock.calls[0];
    expect(url).toBe('/documents');
    expect(form).toBeInstanceOf(FormData);
    expect((form as FormData).get('title')).toBe('RI 2026');
    expect((form as FormData).get('type')).toBe('RI');
    expect((config as { headers: Record<string, string> }).headers['Content-Type']).toBe(
      'multipart/form-data'
    );
  });

  it('getDocumentBlob baixa como blob', async () => {
    get.mockResolvedValue({ data: new Blob(['x']) } as never);
    await getDocumentBlob('d1');
    expect(get).toHaveBeenCalledWith('/documents/d1/file', { responseType: 'blob' });
  });

  it('deleteDocument faz DELETE /documents/{id}', async () => {
    del.mockResolvedValue({} as never);
    await deleteDocument('d1');
    expect(del).toHaveBeenCalledWith('/documents/d1');
  });
});
