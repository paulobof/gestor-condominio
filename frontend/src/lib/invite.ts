/**
 * Mensagem de confirmação do cadastro por terceiros (master/admin): não há senha provisória — a
 * pessoa recebe pelo WhatsApp o link para criar a sua. {@code inviteSentTo} vem do backend já
 * mascarado (ex.: "•••• 7777"); aqui só extraímos os dígitos visíveis.
 */
export function inviteSentMessage(subject: 'Morador' | 'Usuário', inviteSentTo: string): string {
  const lastDigits = inviteSentTo.replace(/\D/g, '');
  const target = lastDigits ? ` para o número terminado em ${lastDigits}` : '';
  return `${subject} cadastrado. Enviamos por WhatsApp o link para criar a senha${target}.`;
}
