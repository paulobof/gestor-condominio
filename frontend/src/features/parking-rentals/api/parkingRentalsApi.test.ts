import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('@/lib/api', () => ({
  api: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}));

import { api } from '@/lib/api';
import {
  listParkingRentals,
  getParkingRental,
  createParkingRental,
  updateParkingRental,
  deleteParkingRental,
} from './parkingRentalsApi';

const get = vi.mocked(api.get);
const post = vi.mocked(api.post);
const put = vi.mocked(api.put);
const del = vi.mocked(api.delete);

beforeEach(() => vi.clearAllMocks());

describe('parkingRentalsApi', () => {
  it('listParkingRentals passa status/page/size', async () => {
    get.mockResolvedValue({ data: { content: [], totalElements: 0, totalPages: 0, number: 0 } });
    await listParkingRentals('ACTIVE');
    expect(get).toHaveBeenCalledWith('/parking-rentals', {
      params: { status: 'ACTIVE', page: 0, size: 20 },
    });
  });

  it('getParkingRental busca por id', async () => {
    get.mockResolvedValue({ data: { id: 'r1' } });
    const r = await getParkingRental('r1');
    expect(get).toHaveBeenCalledWith('/parking-rentals/r1');
    expect(r.id).toBe('r1');
  });

  it('createParkingRental faz POST com o corpo', async () => {
    post.mockResolvedValue({ data: { id: 'r1' } });
    await createParkingRental({ tower: 'A', floor: '-1', spotNumber: '045', monthlyPrice: 350 });
    expect(post).toHaveBeenCalledWith('/parking-rentals', {
      tower: 'A',
      floor: '-1',
      spotNumber: '045',
      monthlyPrice: 350,
    });
  });

  it('updateParkingRental faz PUT com o corpo', async () => {
    put.mockResolvedValue({ data: { id: 'r1' } });
    await updateParkingRental('r1', {
      tower: 'A',
      floor: '-1',
      spotNumber: '045',
      monthlyPrice: 350,
      status: 'RENTED',
    });
    expect(put).toHaveBeenCalledWith('/parking-rentals/r1', {
      tower: 'A',
      floor: '-1',
      spotNumber: '045',
      monthlyPrice: 350,
      status: 'RENTED',
    });
  });

  it('deleteParkingRental faz DELETE', async () => {
    del.mockResolvedValue({ data: null });
    await deleteParkingRental('r1');
    expect(del).toHaveBeenCalledWith('/parking-rentals/r1');
  });
});
