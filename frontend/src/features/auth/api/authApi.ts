import { api } from '@/lib/api';

export interface AuthenticatedUserView {
  id: string;
  fullName: string;
  greetingName?: string | null;
  email: string;
  unitId?: string | null;
  isUnitMaster: boolean;
  roles: string[];
  authorities: string[];
  mustChangePassword: boolean;
}

export interface LoginResponse {
  accessToken: string;
  user: AuthenticatedUserView;
}

export async function login(email: string, password: string): Promise<LoginResponse> {
  const r = await api.post<LoginResponse>('/auth/login', { email, password });
  return r.data;
}

export async function refresh(): Promise<LoginResponse> {
  const r = await api.post<LoginResponse>('/auth/refresh');
  return r.data;
}

export async function logout(): Promise<void> {
  await api.post('/auth/logout');
}

export async function me(): Promise<AuthenticatedUserView> {
  const r = await api.get<AuthenticatedUserView>('/auth/me');
  return r.data;
}
