import axios from 'axios';

export interface ConsentDoc {
  version: string;
  body: string;
  publishedAt: string;
}

const baseUrl = () => (import.meta.env.VITE_API_BASE_URL ?? '/api') as string;

export async function fetchCurrent(): Promise<ConsentDoc> {
  const r = await axios.get<ConsentDoc>(`${baseUrl()}/privacy/document/current`);
  return r.data;
}

export async function registerMaster(form: FormData) {
  const r = await axios.post(`${baseUrl()}/auth/register-master`, form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return r.data;
}

export async function registerOwner(fd: FormData) {
  const r = await axios.post(`${baseUrl()}/auth/register-owner`, fd, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return r.data;
}

export async function lookupUnit(code: string) {
  const r = await axios.get(`${baseUrl()}/units/lookup`, { params: { code } });
  return r.data as { id: string; code: string; hasActiveMaster: boolean };
}
