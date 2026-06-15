import { api } from '@/lib/api';

export type DocumentType = 'RI' | 'AGE' | 'AGO' | 'ATA' | 'CONVENCAO' | 'EDITAL' | 'OUTRO';

export interface DocumentItem {
  id: string;
  title: string;
  type: DocumentType;
  filename: string;
  contentType: string;
  sizeBytes: number;
  uploadedByUserId: string;
  createdAt: string;
}

export async function listDocuments(): Promise<DocumentItem[]> {
  const r = await api.get('/documents');
  return r.data as DocumentItem[];
}

export async function uploadDocument(
  title: string,
  type: DocumentType,
  file: File
): Promise<DocumentItem> {
  const form = new FormData();
  form.append('title', title);
  form.append('type', type);
  form.append('file', file);
  const r = await api.post('/documents', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return r.data as DocumentItem;
}

/** Download autenticado e auditado pelo backend (MinIO permanece privado). */
export async function getDocumentBlob(id: string): Promise<Blob> {
  const r = await api.get(`/documents/${id}/file`, { responseType: 'blob' });
  return r.data as Blob;
}

export async function deleteDocument(id: string): Promise<void> {
  await api.delete(`/documents/${id}`);
}
