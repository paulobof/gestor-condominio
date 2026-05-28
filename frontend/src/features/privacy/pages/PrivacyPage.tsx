import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import axios from 'axios';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Download, Loader2, MessageCircle, ShieldCheck, Trash2 } from 'lucide-react';
import {
  type PersonalDataExport,
  type ProcessingActivity,
  anonymizeAccount,
  exportMyData,
  getProcessingActivities,
  updateWhatsappOptIn,
} from '@/features/privacy/api/privacyApi';
import { useAuth } from '@/features/auth/useAuth';

export function PrivacyPage() {
  const { logout } = useAuth();
  const navigate = useNavigate();

  const [activities, setActivities] = useState<ProcessingActivity[] | null>(null);
  const [me, setMe] = useState<PersonalDataExport | null>(null);
  const [loading, setLoading] = useState(true);
  const [updatingOptIn, setUpdatingOptIn] = useState(false);
  const [exporting, setExporting] = useState(false);

  // Anonimização state
  const [showAnonModal, setShowAnonModal] = useState(false);
  const [currentPassword, setCurrentPassword] = useState('');
  const [confirmText, setConfirmText] = useState('');
  const [anonymizing, setAnonymizing] = useState(false);

  useEffect(() => {
    Promise.all([getProcessingActivities(), exportMyData()])
      .then(([a, m]) => {
        setActivities(a);
        setMe(m);
      })
      .catch(() => toast.error('Falha ao carregar dados de privacidade.'))
      .finally(() => setLoading(false));
  }, []);

  const toggleOptIn = async (next: boolean) => {
    if (!me) return;
    setUpdatingOptIn(true);
    try {
      await updateWhatsappOptIn(next);
      setMe({ ...me, whatsappOptIn: next });
      toast.success(
        next
          ? 'Comunicações WhatsApp ativadas.'
          : 'Comunicações WhatsApp desativadas. Você ainda receberá mensagens essenciais (reset de senha).'
      );
    } catch {
      toast.error('Não foi possível atualizar a preferência.');
    } finally {
      setUpdatingOptIn(false);
    }
  };

  const downloadExport = async () => {
    setExporting(true);
    try {
      const data = await exportMyData();
      const blob = new Blob([JSON.stringify(data, null, 2)], {
        type: 'application/json',
      });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `meus-dados-${new Date().toISOString().slice(0, 10)}.json`;
      a.click();
      URL.revokeObjectURL(url);
      toast.success('Seus dados foram exportados.');
    } catch {
      toast.error('Falha ao exportar dados.');
    } finally {
      setExporting(false);
    }
  };

  const submitAnonymize = async () => {
    if (confirmText !== 'ANONIMIZAR') {
      toast.error("Digite a palavra 'ANONIMIZAR' para confirmar.");
      return;
    }
    setAnonymizing(true);
    try {
      await anonymizeAccount(currentPassword, confirmText);
      toast.success('Conta anonimizada. Você foi desconectado.');
      await logout();
      navigate('/login', { replace: true });
    } catch (e) {
      const code =
        axios.isAxiosError(e) && (e.response?.data as { code?: string } | undefined)?.code;
      if (code === 'INVALID_PASSWORD') {
        toast.error('Senha incorreta.');
      } else {
        toast.error('Não foi possível anonimizar agora. Tente novamente.');
      }
    } finally {
      setAnonymizing(false);
    }
  };

  if (loading) {
    return (
      <main className="min-h-dvh flex items-center justify-center">
        <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
      </main>
    );
  }

  return (
    <main className="min-h-dvh bg-background p-4 md:p-8">
      <div className="mx-auto max-w-3xl space-y-6">
        <header className="space-y-1">
          <h1 className="text-2xl font-semibold tracking-tight">Privacidade e meus dados</h1>
          <p className="text-sm text-muted-foreground">
            Direitos LGPD do titular (Lei 13.709/2018) — Art. 18.
          </p>
        </header>

        {/* === Atividades de tratamento === */}
        <Card>
          <CardHeader>
            <div className="flex items-center gap-2">
              <ShieldCheck className="h-5 w-5 text-primary" aria-hidden="true" />
              <CardTitle className="text-lg">Como tratamos seus dados</CardTitle>
            </div>
            <CardDescription>
              Todas as atividades de tratamento que aplicamos aos seus dados pessoais.
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-3">
            {activities?.map((a, i) => (
              <article key={i} className="rounded-lg border bg-card p-4 space-y-2">
                <div className="flex items-start justify-between gap-2">
                  <h3 className="font-medium">{a.purpose}</h3>
                  {a.revocable && (
                    <span className="rounded-full bg-primary/10 text-primary px-2 py-0.5 text-xs">
                      Revogável
                    </span>
                  )}
                </div>
                <p className="text-sm text-muted-foreground">
                  <strong className="text-foreground">Base legal:</strong> {a.legalBasis}
                </p>
                <p className="text-sm text-muted-foreground">
                  <strong className="text-foreground">Dados:</strong> {a.dataCategories.join(', ')}
                </p>
                <p className="text-sm text-muted-foreground">
                  <strong className="text-foreground">Retenção:</strong> {a.retention}
                </p>
                <p className="text-sm text-muted-foreground">
                  <strong className="text-foreground">Operadores:</strong> {a.operators.join(', ')}
                </p>
              </article>
            ))}
          </CardContent>
        </Card>

        {/* === Exportar dados === */}
        <Card>
          <CardHeader>
            <div className="flex items-center gap-2">
              <Download className="h-5 w-5 text-primary" aria-hidden="true" />
              <CardTitle className="text-lg">Exportar meus dados</CardTitle>
            </div>
            <CardDescription>
              Baixe um arquivo JSON com todos os seus dados pessoais (Art. 18, II).
            </CardDescription>
          </CardHeader>
          <CardContent>
            <Button onClick={downloadExport} disabled={exporting}>
              {exporting ? 'Preparando...' : 'Baixar JSON'}
            </Button>
          </CardContent>
        </Card>

        {/* === WhatsApp opt-in === */}
        <Card>
          <CardHeader>
            <div className="flex items-center gap-2">
              <MessageCircle className="h-5 w-5 text-primary" aria-hidden="true" />
              <CardTitle className="text-lg">Comunicações WhatsApp</CardTitle>
            </div>
            <CardDescription>
              Receber avisos operacionais via WhatsApp (não-essenciais). Reset de senha é sempre
              enviado, independente desta preferência.
            </CardDescription>
          </CardHeader>
          <CardContent className="flex items-center justify-between">
            <span className="text-sm">
              Status: <strong>{me?.whatsappOptIn ? 'Ativado' : 'Desativado'}</strong>
            </span>
            <Button
              variant={me?.whatsappOptIn ? 'outline' : 'default'}
              disabled={updatingOptIn}
              onClick={() => toggleOptIn(!me?.whatsappOptIn)}
            >
              {updatingOptIn ? 'Atualizando...' : me?.whatsappOptIn ? 'Desativar' : 'Ativar'}
            </Button>
          </CardContent>
        </Card>

        {/* === Anonimizar === */}
        <Card className="border-destructive/40">
          <CardHeader>
            <div className="flex items-center gap-2">
              <Trash2 className="h-5 w-5 text-destructive" aria-hidden="true" />
              <CardTitle className="text-lg text-destructive">Anonimizar minha conta</CardTitle>
            </div>
            <CardDescription>
              Direito ao apagamento (Art. 18, IV). Substituímos seu nome, e-mail, telefone e
              comprovante por dados anônimos. <strong>Esta ação é irreversível.</strong>
            </CardDescription>
          </CardHeader>
          <CardContent>
            {!showAnonModal ? (
              <Button variant="destructive" onClick={() => setShowAnonModal(true)}>
                Quero anonimizar minha conta
              </Button>
            ) : (
              <div className="space-y-3">
                <div className="space-y-2">
                  <Label htmlFor="anon-password">Sua senha atual</Label>
                  <Input
                    id="anon-password"
                    type="password"
                    autoComplete="current-password"
                    value={currentPassword}
                    onChange={(e) => setCurrentPassword(e.target.value)}
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="anon-confirm">
                    Digite <code>ANONIMIZAR</code> para confirmar
                  </Label>
                  <Input
                    id="anon-confirm"
                    autoComplete="off"
                    value={confirmText}
                    onChange={(e) => setConfirmText(e.target.value)}
                  />
                </div>
                <div className="flex gap-2">
                  <Button variant="destructive" onClick={submitAnonymize} disabled={anonymizing}>
                    {anonymizing ? 'Anonimizando...' : 'Confirmar anonimização'}
                  </Button>
                  <Button
                    variant="outline"
                    onClick={() => {
                      setShowAnonModal(false);
                      setCurrentPassword('');
                      setConfirmText('');
                    }}
                    disabled={anonymizing}
                  >
                    Cancelar
                  </Button>
                </div>
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </main>
  );
}
