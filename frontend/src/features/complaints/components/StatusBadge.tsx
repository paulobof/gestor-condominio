import { STATUS_LABEL, type ComplaintStatus } from '../api/complaintsApi';

const TONE: Record<ComplaintStatus, string> = {
  OPEN: 'bg-slate-100 text-slate-800',
  IN_REVIEW: 'bg-amber-100 text-amber-900',
  READY: 'bg-blue-100 text-blue-900',
  FORWARDED: 'bg-indigo-100 text-indigo-900',
  RESOLVED: 'bg-green-100 text-green-900',
  REJECTED: 'bg-red-100 text-red-900',
};

export function StatusBadge({ status }: { status: ComplaintStatus }) {
  return (
    <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${TONE[status]}`}>
      {STATUS_LABEL[status]}
    </span>
  );
}
