import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { formatDateBR } from '@/lib/date';
import { getComplaintPhotoUrl, type Complaint } from '../api/complaintsApi';
import { StatusBadge } from './StatusBadge';

/** URL assinada da primeira foto (menor `ordering`); falha é ignorada — o card segue sem imagem. */
function useFirstPhotoUrl(c: Complaint, enabled: boolean) {
  const first = enabled ? [...c.photos].sort((a, b) => a.ordering - b.ordering)[0] : undefined;
  const photoId = first?.id;
  const [url, setUrl] = useState<string | null>(null);

  useEffect(() => {
    setUrl(null);
    if (!photoId) return;
    let active = true;
    getComplaintPhotoUrl(c.id, photoId)
      .then((u) => active && setUrl(u))
      .catch(() => {
        /* sem miniatura */
      });
    return () => {
      active = false;
    };
  }, [c.id, photoId]);

  return url;
}

export function ComplaintCard({
  c,
  to,
  showThumbnail = false,
}: {
  c: Complaint;
  to: string;
  showThumbnail?: boolean;
}) {
  const thumb = useFirstPhotoUrl(c, showThumbnail);
  return (
    <li>
      <Link
        to={to}
        className="flex min-h-[44px] gap-3 rounded-lg border bg-card p-4 hover:bg-muted/50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
      >
        <div className="min-w-0 flex-1 space-y-1">
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
          <p className="line-clamp-2 break-words text-sm">{c.description}</p>
          {c.dueDate && <p className="text-sm font-medium">Prazo: {formatDateBR(c.dueDate)}</p>}
          {c.syndicResponse && <p className="line-clamp-2 text-sm">Síndica: {c.syndicResponse}</p>}
          {c.status === 'REJECTED' && c.rejectionReason && (
            <p className="text-sm text-red-800">Motivo: {c.rejectionReason}</p>
          )}
        </div>
        {thumb && (
          <img
            src={thumb}
            alt={c.code != null ? `Foto do chamado #${c.code}` : 'Foto do chamado'}
            loading="lazy"
            decoding="async"
            width={64}
            height={64}
            referrerPolicy="no-referrer"
            className="h-16 w-16 shrink-0 self-start rounded-md border bg-muted object-cover"
          />
        )}
      </Link>
    </li>
  );
}
