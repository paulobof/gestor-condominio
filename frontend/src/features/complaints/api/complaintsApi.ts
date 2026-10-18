import { api } from '@/lib/api';

export type ComplaintStatus =
  | 'OPEN'
  | 'IN_REVIEW'
  | 'READY'
  | 'FORWARDED'
  | 'RESOLVED'
  | 'REJECTED';

export const STATUS_LABEL: Record<ComplaintStatus, string> = {
  OPEN: 'Aberto',
  IN_REVIEW: 'Em análise',
  READY: 'Pronto p/ encaminhar',
  FORWARDED: 'Encaminhado',
  RESOLVED: 'Resolvido',
  REJECTED: 'Recusado',
};

export interface ComplaintPhoto {
  id: string;
  ordering: number;
  contentType: string;
}

export interface Complaint {
  id: string;
  code: number | null;
  typeId: string;
  typeName: string;
  subtypeId: string;
  subtypeName: string;
  locationId: string;
  locationName: string;
  locationDetail: string | null;
  description: string;
  status: ComplaintStatus;
  dueDate: string | null;
  rejectionReason: string | null;
  resolutionNote: string | null;
  syndicResponse: string | null;
  syndicResponseAt: string | null;
  createdAt: string;
  forwardedAt: string | null;
  resolvedAt: string | null;
  editable: boolean;
  photos: ComplaintPhoto[];
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
}

export interface CatalogItem {
  id: string;
  name: string;
  active: boolean;
  sortOrder: number;
}

export interface CatalogType extends CatalogItem {
  subtypes: CatalogItem[];
}

export interface Catalog {
  types: CatalogType[];
  locations: CatalogItem[];
}

export interface ComplaintFieldsBody {
  typeId: string;
  subtypeId: string;
  locationId: string;
  locationDetail?: string | null;
  description: string;
}

export interface AdminComplaintRow {
  complaint: Complaint;
  authorName: string | null;
  unitCode: string | null;
}

export interface ComplaintEvent {
  kind:
    | 'CREATED'
    | 'EDITED'
    | 'STATUS_CHANGED'
    | 'DUE_DATE_SET'
    | 'PHOTO_ADDED'
    | 'PHOTO_REMOVED'
    | 'CANCELLED'
    | 'RESPONSE_SET';
  actorName: string | null;
  fromValue: string | null;
  toValue: string | null;
  createdAt: string;
}

export interface AdminComplaintDetail extends AdminComplaintRow {
  events: ComplaintEvent[];
}

export interface BatchPreview {
  text: string;
  count: number;
}

export interface ComplaintBatch {
  id: string;
  sentAt: string;
  sentByName: string | null;
  complaintCount: number;
  renderedText: string;
}

export interface CatalogItemBody {
  name: string;
  sortOrder: number;
  active: boolean;
}

// ---- morador ----

export async function getCatalog() {
  return (await api.get('/complaints/catalog')).data as Catalog;
}

export async function createComplaint(body: ComplaintFieldsBody) {
  return (await api.post('/complaints', body)).data as Complaint;
}

export async function updateComplaint(id: string, body: ComplaintFieldsBody) {
  return (await api.put(`/complaints/${id}`, body)).data as Complaint;
}

export async function cancelComplaint(id: string) {
  await api.delete(`/complaints/${id}`);
}

export async function getComplaint(id: string) {
  return (await api.get(`/complaints/${id}`)).data as Complaint;
}

export async function listMine() {
  return (await api.get('/complaints/mine')).data as Complaint[];
}

export async function listBoard(params: {
  typeId?: string;
  locationId?: string;
  status?: 'FORWARDED' | 'RESOLVED';
  page?: number;
}) {
  const r = await api.get('/complaints/board', {
    params: {
      typeId: params.typeId,
      locationId: params.locationId,
      status: params.status,
      page: params.page ?? 0,
      size: 20,
    },
  });
  return r.data as Page<Complaint>;
}

export async function uploadComplaintPhoto(id: string, file: File) {
  const form = new FormData();
  form.append('file', file);
  const r = await api.post(`/complaints/${id}/photos`, form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return r.data as ComplaintPhoto;
}

export async function deleteComplaintPhoto(id: string, photoId: string) {
  await api.delete(`/complaints/${id}/photos/${photoId}`);
}

export async function getComplaintPhotoUrl(id: string, photoId: string) {
  return ((await api.get(`/complaints/${id}/photos/${photoId}/url`)).data as { url: string }).url;
}

// ---- admin ----

export async function adminQueue(statuses: ComplaintStatus[], page = 0) {
  const r = await api.get('/admin/complaints', {
    params: { status: statuses, page, size: 20 },
    // status=OPEN&status=READY (sem colchetes) — formato que o Spring entende
    paramsSerializer: { indexes: null },
  });
  return r.data as Page<AdminComplaintRow>;
}

export async function adminDetail(id: string) {
  return (await api.get(`/admin/complaints/${id}`)).data as AdminComplaintDetail;
}

export async function adminAction(id: string, action: 'start-review' | 'ready' | 'reopen-review') {
  return (await api.post(`/admin/complaints/${id}/${action}`)).data as AdminComplaintDetail;
}

export async function adminReject(id: string, reason: string) {
  return (await api.post(`/admin/complaints/${id}/reject`, { reason }))
    .data as AdminComplaintDetail;
}

export async function adminEdit(id: string, body: ComplaintFieldsBody) {
  return (await api.put(`/admin/complaints/${id}`, body)).data as AdminComplaintDetail;
}

export async function adminSetDueDate(id: string, dueDate: string) {
  return (await api.put(`/admin/complaints/${id}/due-date`, { dueDate }))
    .data as AdminComplaintDetail;
}

export async function adminResolve(id: string, note: string) {
  return (await api.post(`/admin/complaints/${id}/resolve`, { note: note || null }))
    .data as AdminComplaintDetail;
}

export async function previewBatch(ids: string[]) {
  return (await api.post('/admin/complaints/batches/preview', { ids })).data as BatchPreview;
}

/** `expectedText`: o texto exibido/copiado; o backend recusa (409 BATCH_STALE) se mudou. */
export async function createBatch(ids: string[], expectedText: string) {
  return (await api.post('/admin/complaints/batches', { ids, expectedText }))
    .data as ComplaintBatch;
}

export async function listBatches() {
  return (await api.get('/admin/complaints/batches')).data as ComplaintBatch[];
}

// ---- catálogo (admin) ----

export async function adminCatalog() {
  return (await api.get('/admin/complaint-catalog')).data as Catalog;
}

export async function createType(body: CatalogItemBody) {
  return (await api.post('/admin/complaint-catalog/types', body)).data as CatalogType;
}

export async function updateType(id: string, body: CatalogItemBody) {
  return (await api.put(`/admin/complaint-catalog/types/${id}`, body)).data as CatalogType;
}

export async function createSubtype(typeId: string, body: CatalogItemBody) {
  return (await api.post(`/admin/complaint-catalog/types/${typeId}/subtypes`, body))
    .data as CatalogItem;
}

export async function updateSubtype(id: string, body: CatalogItemBody) {
  return (await api.put(`/admin/complaint-catalog/subtypes/${id}`, body)).data as CatalogItem;
}

export async function createLocation(body: CatalogItemBody) {
  return (await api.post('/admin/complaint-catalog/locations', body)).data as CatalogItem;
}

export async function updateLocation(id: string, body: CatalogItemBody) {
  return (await api.put(`/admin/complaint-catalog/locations/${id}`, body)).data as CatalogItem;
}

/** Mensagem de erro do backend (ApiError.message) ou um fallback. */
export function backendMessage(e: unknown, fallback: string): string {
  return (e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? fallback;
}

export async function adminSetSyndicResponse(id: string, text: string) {
  return (await api.put(`/admin/complaints/${id}/response`, { text })).data as AdminComplaintDetail;
}

export type CatalogKind = 'types' | 'subtypes' | 'locations';

/** Troca o item com o vizinho (o backend renumera os irmãos numa transação). */
export async function moveCatalogItem(kind: CatalogKind, id: string, direction: 'UP' | 'DOWN') {
  await api.post(`/admin/complaint-catalog/${kind}/${id}/move`, { direction });
}
