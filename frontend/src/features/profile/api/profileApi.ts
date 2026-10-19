import { api } from '@/lib/api';

/** Dados da própria conta ("Meu cadastro"). `phone` é o telefone privado da conta. */
export interface OwnProfile {
  fullName: string;
  greetingName: string | null;
  phone: string | null;
  email: string | null;
}

export interface UpdateOwnProfileBody {
  fullName: string;
  greetingName: string | null;
  phone: string | null;
}

export async function getOwnProfile(): Promise<OwnProfile> {
  const r = await api.get<OwnProfile>('/users/me/profile');
  return r.data;
}

export async function updateOwnProfile(body: UpdateOwnProfileBody): Promise<OwnProfile> {
  const r = await api.put<OwnProfile>('/users/me/profile', body);
  return r.data;
}

export async function changeOwnPassword(
  currentPassword: string,
  newPassword: string
): Promise<void> {
  await api.put('/users/me/password', { currentPassword, newPassword });
}
