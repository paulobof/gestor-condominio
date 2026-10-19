import { describe, it, expect } from 'vitest';
import { inviteSentMessage } from './invite';

describe('inviteSentMessage', () => {
  it('monta a mensagem com os últimos dígitos do telefone mascarado', () => {
    expect(inviteSentMessage('Morador', '•••• 7777')).toBe(
      'Morador cadastrado. Enviamos por WhatsApp o link para criar a senha para o número terminado em 7777.'
    );
  });

  it('adapta o sujeito para o cadastro do admin', () => {
    expect(inviteSentMessage('Usuário', '•••• 1234')).toBe(
      'Usuário cadastrado. Enviamos por WhatsApp o link para criar a senha para o número terminado em 1234.'
    );
  });

  it('sem dígitos visíveis não cita o final do número', () => {
    expect(inviteSentMessage('Morador', '••••')).toBe(
      'Morador cadastrado. Enviamos por WhatsApp o link para criar a senha.'
    );
  });
});
