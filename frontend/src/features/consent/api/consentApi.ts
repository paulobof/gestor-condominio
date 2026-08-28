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

export interface RegisterMasterPayload {
  fullName: string;
  greetingName: string;
  email: string;
  phone: string;
  unitCode: string;
  password: string;
  consentVersion: string;
  whatsappOptIn: boolean;
}

/**
 * Cadastro do morador (JSON, sem comprovante). A resposta diz o que aconteceu:
 * `ACTIVE` = entrou e virou master da unidade; `PENDING_APPROVAL` = a unidade já tinha
 * master e o pedido foi para a aprovação dele.
 */
export async function registerMaster(payload: RegisterMasterPayload) {
  const r = await axios.post<{ userId: string; status: string }>(
    `${baseUrl()}/auth/register-master`,
    payload
  );
  return r.data;
}

export async function lookupUnit(code: string) {
  const r = await axios.get(`${baseUrl()}/units/lookup`, { params: { code } });
  return r.data as { id: string; code: string; hasActiveMaster: boolean };
}
