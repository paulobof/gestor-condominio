import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi } from 'vitest';

vi.mock('@/components/UnitSelector', () => ({
  UnitSelector: ({ onChange }: { onChange: (c: string, h: boolean) => void }) => (
    <button onClick={() => onChange('602C', false)}>pick-unit</button>
  ),
}));
vi.mock('@/components/ProofUploader', () => ({
  ProofUploader: ({ onChange }: { onChange: (f: File) => void }) => (
    <button onClick={() => onChange(new File(['x'], 'p.jpg', { type: 'image/jpeg' }))}>
      pick-proof
    </button>
  ),
}));
vi.mock('@/features/consent/ConsentBox', () => ({
  ConsentBox: ({ onChange }: { onChange: (a: boolean, v: string) => void }) => (
    <button onClick={() => onChange(true, '1.0.0')}>accept-consent</button>
  ),
}));
vi.mock('@/features/consent/api/consentApi', () => ({ registerMaster: vi.fn() }));
vi.mock('sonner', () => ({ toast: { error: vi.fn(), success: vi.fn() } }));

import { RegisterMasterPage } from './RegisterMasterPage';

async function fillAllFieldsExceptPassword(user: ReturnType<typeof userEvent.setup>) {
  // Fill unit (sets unitCode + hasMaster=false)
  await user.click(screen.getByRole('button', { name: 'pick-unit' }));

  // Fill text fields via label
  await user.type(screen.getByLabelText('Nome completo'), 'Maria Souza');
  await user.type(screen.getByLabelText('Como prefere ser chamado'), 'Maria');
  await user.type(screen.getByLabelText('E-mail'), 'maria@example.com');
  await user.type(screen.getByLabelText('Telefone (WhatsApp)'), '11999990000');

  // Fill proof
  await user.click(screen.getByRole('button', { name: 'pick-proof' }));

  // Accept consent
  await user.click(screen.getByRole('button', { name: 'accept-consent' }));
}

describe('RegisterMasterPage — submit button gated on password strength', () => {
  it('submit is disabled with a weak password', async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <RegisterMasterPage />
      </MemoryRouter>
    );

    await fillAllFieldsExceptPassword(user);

    // Type a weak password (has length >= 8 but missing upper/special)
    await user.type(screen.getByLabelText('Senha'), 'senha12345');

    const submitBtn = screen.getByRole('button', { name: 'Enviar cadastro' });
    expect(submitBtn).toBeDisabled();
  });

  it('submit is enabled with a strong password confirmed', async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <RegisterMasterPage />
      </MemoryRouter>
    );

    await fillAllFieldsExceptPassword(user);

    // Type a strong password meeting all policy rules and confirm it
    await user.type(screen.getByLabelText('Senha'), 'Senha@1234');
    await user.type(screen.getByLabelText('Confirmar senha'), 'Senha@1234');

    const submitBtn = screen.getByRole('button', { name: 'Enviar cadastro' });
    expect(submitBtn).not.toBeDisabled();
  });

  it('submit is disabled when the confirmation does not match', async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <RegisterMasterPage />
      </MemoryRouter>
    );

    await fillAllFieldsExceptPassword(user);

    await user.type(screen.getByLabelText('Senha'), 'Senha@1234');
    await user.type(screen.getByLabelText('Confirmar senha'), 'Senha@9999');

    expect(screen.getByRole('button', { name: 'Enviar cadastro' })).toBeDisabled();
    expect(screen.getByText(/as senhas n[ãa]o conferem/i)).toBeInTheDocument();
  });
});
