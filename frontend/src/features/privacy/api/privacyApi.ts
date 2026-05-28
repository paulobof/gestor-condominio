import { api } from '@/lib/api';

export interface PersonalDataExport {
  userId: string;
  fullName: string | null;
  greetingName: string | null;
  emails: string[];
  phone: string | null;
  phoneVerifiedAt: string | null;
  gender: string | null;
  birthDate: string | null;
  status: string;
  unit: { unitId: string; code: string; isUnitMaster: boolean } | null;
  residenceProof: {
    filename: string | null;
    contentType: string | null;
    uploadedAt: string | null;
    verifiedAt: string | null;
  } | null;
  consent: { documentVersion: string | null; acceptedAt: string | null };
  whatsappOptIn: boolean;
  whatsappOptInAt: string | null;
  createdAt: string;
  updatedAt: string;
  roles: string[];
  exportedAt: string;
}

export interface ProcessingActivity {
  purpose: string;
  legalBasis: string;
  dataCategories: string[];
  retention: string;
  operators: string[];
  revocable: boolean;
}

export async function exportMyData(): Promise<PersonalDataExport> {
  const r = await api.get<PersonalDataExport>('/privacy/me/export');
  return r.data;
}

export async function getProcessingActivities(): Promise<ProcessingActivity[]> {
  const r = await api.get<ProcessingActivity[]>('/privacy/me/processing-activities');
  return r.data;
}

export async function updateWhatsappOptIn(optIn: boolean): Promise<void> {
  await api.put('/users/me/whatsapp-opt-in', { optIn });
}

export async function anonymizeAccount(
  currentPassword: string,
  confirmText: string
): Promise<void> {
  await api.post('/privacy/me/anonymize', { currentPassword, confirmText });
}
