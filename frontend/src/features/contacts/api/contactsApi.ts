import { api } from '@/lib/api';
import type { OpeningHoursDto } from '@/components/openinghours/openingHours';

export interface Contact {
  id: string;
  name: string;
  category: string;
  phone: string;
  notes: string | null;
  is24h: boolean;
  openingHours: OpeningHoursDto[];
  updatedAt: string;
}

export interface ContactBody {
  name: string;
  category: string;
  phone: string;
  notes: string;
  is24h: boolean;
  openingHours: OpeningHoursDto[];
}

export async function listContacts() {
  return (await api.get('/contacts')).data as Contact[];
}

export async function createContact(b: ContactBody) {
  return (await api.post('/contacts', b)).data as Contact;
}

export async function updateContact(id: string, b: ContactBody) {
  return (await api.put(`/contacts/${id}`, b)).data as Contact;
}

export async function deleteContact(id: string) {
  await api.delete(`/contacts/${id}`);
}
