import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('@/lib/api', () => ({
  api: { get: vi.fn(), post: vi.fn() },
}));

import { api } from '@/lib/api';
import { searchTags, createTag } from './tagsApi';

const get = vi.mocked(api.get);
const post = vi.mocked(api.post);

beforeEach(() => {
  vi.clearAllMocks();
});

describe('tagsApi — contrato com o backend', () => {
  it('searchTags envia q como query param', async () => {
    get.mockResolvedValue({
      data: [{ id: 't1', slug: 'encanador', label: 'Encanador', color: null }],
    });

    const tags = await searchTags('enc');

    expect(get).toHaveBeenCalledWith('/tags', { params: { q: 'enc' } });
    expect(tags[0].slug).toBe('encanador');
  });

  it('createTag faz POST com slug e label', async () => {
    post.mockResolvedValue({
      data: { id: 't1', slug: 'encanador', label: 'Encanador', color: null },
    });

    await createTag('encanador', 'Encanador');

    expect(post).toHaveBeenCalledWith('/tags', { slug: 'encanador', label: 'Encanador' });
  });

  it('createTag funciona sem label', async () => {
    post.mockResolvedValue({ data: { id: 't1', slug: 'pintor', label: 'pintor', color: null } });

    await createTag('pintor');

    expect(post).toHaveBeenCalledWith('/tags', { slug: 'pintor', label: undefined });
  });
});
