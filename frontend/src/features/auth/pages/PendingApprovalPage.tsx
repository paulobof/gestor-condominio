import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Home, Clock } from 'lucide-react';

export function PendingApprovalPage() {
  return (
    <main className="min-h-dvh flex items-center justify-center bg-background p-4">
      <Card className="w-full max-w-lg">
        <CardHeader className="space-y-3 text-center">
          <div className="flex justify-center gap-2 items-center">
            <Home className="text-primary" />
            <span className="font-heading font-semibold">HELBOR TRILOGY HOME</span>
          </div>
          <CardTitle>Cadastro recebido</CardTitle>
        </CardHeader>
        <CardContent className="text-center space-y-4">
          <Clock className="mx-auto w-12 h-12 text-accent" aria-hidden="true" />
          <p>
            Seu cadastro foi enviado e está em análise pelo síndico. Você receberá uma confirmação
            assim que for aprovado.
          </p>
          <a href="/login" className="text-primary hover:underline text-sm">
            Voltar para o login
          </a>
        </CardContent>
      </Card>
    </main>
  );
}
