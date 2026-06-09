import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { toast } from 'sonner';
import { ArrowLeft } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { createAnnouncement, getAnnouncement, updateAnnouncement } from '../api/announcementsApi';

export function AnnouncementFormPage() {
  const { id } = useParams<{ id: string }>();
  const isEdit = !!id;
  const navigate = useNavigate();

  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const [loading, setLoading] = useState(isEdit);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!isEdit || !id) return;
    let active = true;
    setLoading(true);
    getAnnouncement(id)
      .then((a) => {
        if (!active) return;
        setTitle(a.title);
        setBody(a.body);
      })
      .catch(() => toast.error('Erro ao carregar o aviso.'))
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [id, isEdit]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim()) {
      toast.error('Informe um título.');
      return;
    }
    if (!body.trim()) {
      toast.error('Informe a mensagem do aviso.');
      return;
    }
    setSaving(true);
    try {
      const payload = { title: title.trim(), body: body.trim() };
      if (isEdit && id) {
        await updateAnnouncement(id, payload);
        toast.success('Aviso atualizado.');
        navigate(`/avisos/${id}`);
      } else {
        const created = await createAnnouncement(payload);
        toast.success('Aviso publicado.');
        navigate(`/avisos/${created.id}`);
      }
    } catch {
      toast.error('Erro ao salvar o aviso.');
    } finally {
      setSaving(false);
    }
  };

  if (loading) return <main className="mx-auto max-w-2xl p-4">Carregando…</main>;

  return (
    <main className="mx-auto max-w-2xl p-4 space-y-4">
      <Button asChild variant="ghost" className="min-h-[44px] px-2">
        <Link to={isEdit && id ? `/avisos/${id}` : '/avisos'}>
          <ArrowLeft aria-hidden="true" /> Voltar
        </Link>
      </Button>
      <Card>
        <CardHeader>
          <CardTitle>{isEdit ? 'Editar aviso' : 'Novo aviso'}</CardTitle>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} className="space-y-4" noValidate>
            <div className="space-y-2">
              <Label htmlFor="title">Título</Label>
              <Input
                id="title"
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                maxLength={140}
                aria-invalid={!title.trim()}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="body">Mensagem</Label>
              <textarea
                id="body"
                value={body}
                onChange={(e) => setBody(e.target.value)}
                rows={6}
                maxLength={5000}
                aria-invalid={!body.trim()}
                className="flex w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
              />
            </div>
            <Button type="submit" disabled={saving} className="min-h-[44px] w-full">
              {saving ? 'Salvando…' : isEdit ? 'Salvar alterações' : 'Publicar aviso'}
            </Button>
          </form>
        </CardContent>
      </Card>
    </main>
  );
}
