import { useEffect, useState } from 'react';
import { getComplaintPhotoUrl, type ComplaintPhoto } from '../api/complaintsApi';

/** Galeria somente-leitura; URLs assinadas são buscadas uma a uma (falha individual é ignorada). */
export function ComplaintPhotos({
  complaintId,
  photos,
}: {
  complaintId: string;
  photos: ComplaintPhoto[];
}) {
  const [urls, setUrls] = useState<Record<string, string>>({});

  useEffect(() => {
    let active = true;
    photos.forEach((p) => {
      getComplaintPhotoUrl(complaintId, p.id)
        .then((url) => active && setUrls((prev) => ({ ...prev, [p.id]: url })))
        .catch(() => {
          /* ignora foto individual */
        });
    });
    return () => {
      active = false;
    };
  }, [complaintId, photos]);

  if (photos.length === 0) return null;
  return (
    <ul className="grid grid-cols-3 gap-2">
      {photos.map((p, i) => (
        <li key={p.id} className="aspect-square overflow-hidden rounded-md border bg-muted">
          {urls[p.id] && (
            <a href={urls[p.id]} target="_blank" rel="noreferrer">
              <img
                src={urls[p.id]}
                alt={`Foto ${i + 1} do chamado`}
                className="h-full w-full object-cover"
                referrerPolicy="no-referrer"
              />
            </a>
          )}
        </li>
      ))}
    </ul>
  );
}
