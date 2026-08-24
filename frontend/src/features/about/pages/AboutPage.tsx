import { Link } from 'react-router-dom';
import { Building2, MessageCircle, ShieldCheck, Users } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { DeveloperCredit } from '@/components/branding/DeveloperCredit';

/**
 * Página pública de apresentação. Existe para responder, antes do cadastro, as três perguntas que
 * fazem alguém desistir: o que é isto, quem fez, e o que acontece com os meus dados.
 *
 * Não expõe nenhum conteúdo do condomínio — avisos, documentos e moradores seguem só para quem
 * entra.
 */
export function AboutPage() {
  return (
    <main className="mx-auto max-w-2xl p-4 pb-10">
      <header className="py-6 text-center">
        <img
          src="/icon-192.png"
          alt=""
          className="mx-auto mb-3 h-16 w-16 rounded-xl"
          width={64}
          height={64}
        />
        <h1 className="font-heading text-2xl font-semibold">HELBOR TRILOGY HOME</h1>
        <p className="mt-2 text-muted-foreground">
          Um aplicativo para facilitar o dia a dia de quem mora aqui.
        </p>
      </header>

      <Card className="mb-4">
        <CardContent className="space-y-4 pt-6 text-sm">
          <div className="flex gap-3">
            <Building2 className="mt-0.5 h-5 w-5 shrink-0 text-muted-foreground" aria-hidden />
            <p>
              <strong className="block">O que dá para fazer</strong>
              Ver os avisos do condomínio, consultar informações e documentos, e trocar indicações
              de serviços e classificados com os vizinhos.
            </p>
          </div>
          <div className="flex gap-3">
            <Users className="mt-0.5 h-5 w-5 shrink-0 text-muted-foreground" aria-hidden />
            <p>
              <strong className="block">Como funciona a entrada</strong>
              Você informa sua torre e apartamento. Se ninguém da sua unidade estiver cadastrado,
              você entra na hora e passa a responder por ela. Se já houver alguém, essa pessoa
              recebe seu pedido e decide — ninguém entra na sua unidade sem que você saiba.
            </p>
          </div>
          <div className="flex gap-3">
            <MessageCircle className="mt-0.5 h-5 w-5 shrink-0 text-muted-foreground" aria-hidden />
            <p>
              <strong className="block">Como falamos com você</strong>
              Só por WhatsApp, e só sobre o que você pediu: aviso de pedido de acesso, redefinição
              de senha e mudanças na sua conta. Nunca mandamos propaganda.
            </p>
          </div>
          <div className="flex gap-3">
            <ShieldCheck className="mt-0.5 h-5 w-5 shrink-0 text-muted-foreground" aria-hidden />
            <p>
              <strong className="block">Seus dados</strong>
              Pedimos o mínimo: nome, como prefere ser chamado, unidade, WhatsApp e e-mail. Você
              pode exportar ou apagar tudo quando quiser, direto no aplicativo. Detalhes na{' '}
              <Link to="/privacidade" className="underline">
                política de privacidade
              </Link>
              .
            </p>
          </div>
        </CardContent>
      </Card>

      <Card className="mb-6">
        <CardContent className="pt-6 text-sm text-muted-foreground">
          <strong className="block text-foreground">Aplicativo independente</strong>
          Feito por um morador, por conta própria. Não tem vínculo oficial com a administração, o
          síndico ou o conselho, e não substitui os canais oficiais do condomínio.
        </CardContent>
      </Card>

      <div className="flex flex-col gap-2 sm:flex-row">
        <Button asChild className="min-h-[44px] flex-1">
          <Link to="/register-master">Criar minha conta</Link>
        </Button>
        <Button asChild variant="outline" className="min-h-[44px] flex-1">
          <Link to="/login">Já tenho conta</Link>
        </Button>
      </div>

      <footer className="pt-8">
        <DeveloperCredit />
      </footer>
    </main>
  );
}
