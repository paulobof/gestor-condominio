import { useEffect, useState, type FormEvent } from 'react';
import { toast } from 'sonner';
import { Download, FileText, Trash2, Upload } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { useAuth } from '@/features/auth/useAuth';
import {
  deleteDocument,
  getDocumentBlob,
  listDocuments,
  uploadDocument,
  type DocumentItem,
  type DocumentType,
} from '../api/documentsApi';

const TYPE_LABELS: Record<DocumentType, string> = {
  RI: 'Regimento Interno',
  AGE: 'Assembleia Geral Extraordinária',
  AGO: 'Assembleia Geral Ordinária',
  ATA: 'Ata',
  CONVENCAO: 'Convenção',
  EDITAL: 'Edital',
  OUTRO: 'Outro',
};
const TYPES = Object.keys(TYPE_LABELS) as DocumentType[];

export function DocumentsPage() {
  const { user } = useAuth();
  const canManage = user?.authorities.includes('DOCUMENT_MANAGE') ?? false;

  const [items, setItems] = useState<DocumentItem[]>([]);
  const [loading, setLoading] = useState(true);

  const [title, setTitle] = useState('');
  const [type, setType] = useState<DocumentType>('RI');
  const [file, setFile] = useState<File | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const load = () => {
    setLoading(true);
    listDocuments()
      .then(setItems)
      .catch(() => toast.error('Erro ao carregar documentos.'))
      .finally(() => setLoading(false));
  };

  useEffect(load, []);

  const handleDownload = async (d: DocumentItem) => {
    try {
      const blob = await getDocumentBlob(d.id);
      const url = URL.createObjectURL(blob);
      window.open(url, '_blank', 'noopener,noreferrer');
      setTimeout(() => URL.revokeObjectURL(url), 60_000);
    } catch {
      toast.error('Erro ao abrir o documento.');
    }
  };

  const handleUpload = async (e: FormEvent) => {
    e.preventDefault();
    if (!title.trim()) {
      toast.error('Informe um título.');
      return;
    }
    if (!file) {
      toast.error('Selecione um arquivo PDF.');
      return;
    }
    if (file.type !== 'application/pdf') {
      toast.error('Apenas PDF é aceito.');
      return;
    }
    setSubmitting(true);
    try {
      await uploadDocument(title.trim(), type, file);
      toast.success('Documento enviado.');
      setTitle('');
      setType('RI');
      setFile(null);
      load();
    } catch {
      toast.error('Erro ao enviar o documento.');
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = async (id: string) => {
    try {
      await deleteDocument(id);
      toast.success('Documento removido.');
      setItems((prev) => prev.filter((x) => x.id !== id));
    } catch {
      toast.error('Erro ao remover o documento.');
    }
  };

  return (
    <main className="mx-auto max-w-3xl p-4">
      <h1 className="flex items-center gap-2 font-heading text-2xl font-semibold">
        <FileText className="h-6 w-6" aria-hidden="true" />
        Documentos
      </h1>
      <p className="mt-1 text-sm text-muted-foreground">
        Documentos do condomínio (regimento interno, assembleias e outros).
      </p>

      {canManage && (
        <Card className="mt-4">
          <CardHeader>
            <CardTitle className="text-base">Enviar documento</CardTitle>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleUpload} className="flex flex-col gap-3">
              <div>
                <Label htmlFor="doc-title">Título</Label>
                <Input
                  id="doc-title"
                  value={title}
                  maxLength={180}
                  onChange={(e) => setTitle(e.target.value)}
                />
              </div>
              <div>
                <Label htmlFor="doc-type">Tipo</Label>
                <select
                  id="doc-type"
                  value={type}
                  onChange={(e) => setType(e.target.value as DocumentType)}
                  className="block min-h-[44px] w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
                >
                  {TYPES.map((t) => (
                    <option key={t} value={t}>
                      {TYPE_LABELS[t]}
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <Label htmlFor="doc-file">Arquivo (PDF, até 5MB)</Label>
                <input
                  id="doc-file"
                  type="file"
                  accept="application/pdf,.pdf"
                  onChange={(e) => setFile(e.target.files?.[0] ?? null)}
                  className="block w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
                />
              </div>
              <div className="flex justify-end">
                <Button type="submit" disabled={submitting}>
                  <Upload className="mr-2 h-4 w-4" aria-hidden="true" />
                  Enviar
                </Button>
              </div>
            </form>
          </CardContent>
        </Card>
      )}

      <div className="mt-4">
        {loading ? (
          <p className="text-muted-foreground">Carregando…</p>
        ) : items.length === 0 ? (
          <p className="text-muted-foreground">Nenhum documento publicado.</p>
        ) : (
          <ul className="flex flex-col gap-2">
            {items.map((d) => (
              <li key={d.id}>
                <Card>
                  <CardContent className="flex items-center justify-between gap-3 py-3">
                    <div className="min-w-0">
                      <p className="truncate font-medium">{d.title}</p>
                      <p className="text-xs text-muted-foreground">{TYPE_LABELS[d.type]}</p>
                    </div>
                    <div className="flex shrink-0 gap-2">
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        onClick={() => handleDownload(d)}
                      >
                        <Download className="mr-1 h-4 w-4" aria-hidden="true" />
                        Baixar
                      </Button>
                      {canManage && (
                        <Button
                          type="button"
                          variant="ghost"
                          size="sm"
                          aria-label={`Excluir ${d.title}`}
                          onClick={() => handleDelete(d.id)}
                        >
                          <Trash2 className="h-4 w-4" aria-hidden="true" />
                        </Button>
                      )}
                    </div>
                  </CardContent>
                </Card>
              </li>
            ))}
          </ul>
        )}
      </div>
    </main>
  );
}
