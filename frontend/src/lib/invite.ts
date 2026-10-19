/**
 * Mensagem de confirmação do cadastro por terceiros (master/admin): não há senha provisória — a
 * pessoa recebe pelo WhatsApp o link para criar a sua. {@code inviteSentTo} vem do backend já
 * mascarado (ex.: "•••• 7777"); aqui só extraímos os dígitos visíveis.
 */
export function inviteSentMessage(subject: 'Morador' | 'Usuário', inviteSentTo: string): string {
  return `${subject} cadastrado. Enviamos por WhatsApp o link para criar a senha${numberSuffix(inviteSentTo)}.`;
}

/** Confirmação do reenvio do convite (botão "Reenviar link"). */
export function inviteResentMessage(inviteSentTo: string): string {
  return `Link reenviado por WhatsApp${numberSuffix(inviteSentTo)}.`;
}

function numberSuffix(inviteSentTo: string): string {
  const lastDigits = inviteSentTo.replace(/\D/g, '');
  return lastDigits ? ` para o número terminado em ${lastDigits}` : '';
}
