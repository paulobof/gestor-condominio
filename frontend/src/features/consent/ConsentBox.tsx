import { useEffect, useState } from 'react';
import { fetchCurrent } from './api/consentApi';

interface Props {
  accepted: boolean;
  onChange: (accepted: boolean, version: string | null) => void;
}

export function ConsentBox({ accepted, onChange }: Props) {
  const [doc, setDoc] = useState<{ version: string; body: string } | null>(null);

  useEffect(() => {
    fetchCurrent()
      .then(setDoc)
      .catch(() => setDoc(null));
  }, []);

  if (!doc) return <p className="text-sm text-muted-foreground">Carregando termo...</p>;

  return (
    <div className="space-y-3">
      <div className="max-h-48 overflow-y-auto rounded-md border border-border p-3 text-sm whitespace-pre-wrap bg-muted/30">
        {doc.body}
      </div>
      <label className="flex items-start gap-2 text-sm cursor-pointer">
        <input
          type="checkbox"
          checked={accepted}
          onChange={(e) => onChange(e.target.checked, doc.version)}
          className="mt-1"
        />
        <span>Li e aceito o termo de privacidade (versão {doc.version}).</span>
      </label>
    </div>
  );
}
