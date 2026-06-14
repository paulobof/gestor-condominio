import { api } from '@/lib/api';

export type ParkingRentalStatus = 'ACTIVE' | 'RENTED' | 'ARCHIVED';

export interface ParkingRental {
  id: string;
  tower: string;
  floor: string;
  spotNumber: string;
  monthlyPrice: number;
  status: ParkingRentalStatus;
  authorUserId: string;
  createdAt: string;
  authorName: string | null;
  authorPhone: string | null;
  authorWhatsapp: string | null;
}

export interface ParkingRentalPage {
  content: ParkingRental[];
  totalElements: number;
  totalPages: number;
  number: number;
}

export interface ParkingRentalBody {
  tower: string;
  floor: string;
  spotNumber: string;
  monthlyPrice: number;
}

export async function listParkingRentals(status?: ParkingRentalStatus, page = 0, size = 20) {
  const r = await api.get('/parking-rentals', { params: { status, page, size } });
  return r.data as ParkingRentalPage;
}

export async function getParkingRental(id: string) {
  const r = await api.get(`/parking-rentals/${id}`);
  return r.data as ParkingRental;
}

export async function createParkingRental(body: ParkingRentalBody) {
  const r = await api.post('/parking-rentals', body);
  return r.data as ParkingRental;
}

export async function updateParkingRental(
  id: string,
  body: ParkingRentalBody & { status?: ParkingRentalStatus }
) {
  const r = await api.put(`/parking-rentals/${id}`, body);
  return r.data as ParkingRental;
}

export async function deleteParkingRental(id: string) {
  await api.delete(`/parking-rentals/${id}`);
}
