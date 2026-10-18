import { Link } from 'react-router-dom';
import { formatDateBR } from '@/lib/date';
import type { Complaint } from '../api/complaintsApi';
import { StatusBadge } from './StatusBadge';

export function ComplaintCard({ c, to }: { c: Complaint; to: string }) {
  return (
    <li>
      <Link
        to={to}
        className="block min-h-[44px] space-y-1 rounded-lg border bg-card p-4 hover:bg-muted/50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
      >
        <div className="flex items-center justify-between gap-2">
          <span className="text-xs text-muted-foreground">
            {c.code != null ? `#${c.code}` : ''}
          </span>
          <StatusBadge status={c.status} />
        </div>
        <p className="font-medium">
          {c.typeName} › {c.subtypeName}
        </p>
        <p className="text-sm text-muted-foreground">
          {c.locationName}
          {c.locationDetail ? ` — ${c.locationDetail}` : ''}
        </p>
        <p className="line-clamp-2 text-sm">{c.description}</p>
        {c.dueDate && <p className="text-sm font-medium">Prazo: {formatDateBR(c.dueDate)}</p>}
        {c.status === 'REJECTED' && c.rejectionReason && (
          <p className="text-sm text-red-800">Motivo: {c.rejectionReason}</p>
        )}
      </Link>
    </li>
  );
}
