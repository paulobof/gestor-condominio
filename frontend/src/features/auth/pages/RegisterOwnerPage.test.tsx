import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('react-router-dom', async (orig) => {
  const actual = await orig<typeof import('react-router-dom')>();
  return { ...actual, useNavigate: () => vi.fn() };
});
vi.mock('@/components/UnitSelector', () => ({
  UnitSelector: ({ onChange }: { onChange: (c: string) => void }) => (
    <button onClick={() => onChange('602C')}>pick-unit</button>
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
vi.mock('@/features/consent/api/consentApi', () => ({
  registerOwner: vi.fn(),
  fetchCurrent: vi.fn().mockResolvedValue({ version: '1.0.0', body: 'Termo de privacidade.' }),
}));
vi.mock('sonner', () => ({ toast: { error: vi.fn(), success: vi.fn() } }));

import { RegisterOwnerPage } from './RegisterOwnerPage';

beforeEach(() => vi.clearAllMocks());

async function fillAllFieldsExceptPassword(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByRole('button', { name: 'pick-unit' }));
  await user.type(screen.getByLabelText('Nome completo'), 'Maria Souza');
  await user.type(screen.getByLabelText('Como prefere ser chamado'), 'Maria');
  await user.type(screen.getByLabelText('E-mail'), 'maria@example.com');
  await user.type(screen.getByLabelText('Telefone (WhatsApp)'), '11999990000');
  await user.click(screen.getByRole('button', { name: 'pick-proof' }));
  await user.click(screen.getByRole('button', { name: 'accept-consent' }));
}

describe('RegisterOwnerPage', () => {
  it('renderiza o cadastro de proprietário com comprovante de propriedade', () => {
    render(
      <MemoryRouter>
        <RegisterOwnerPage />
      </MemoryRouter>
    );
    expect(screen.getByRole('heading', { name: /propriet[áa]rio/i })).toBeInTheDocument();
    expect(screen.getByText(/comprovante de propriedade/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/nome completo/i)).toBeInTheDocument();
  });

  it('submit is enabled with a strong password confirmed', async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <RegisterOwnerPage />
      </MemoryRouter>
    );

    await fillAllFieldsExceptPassword(user);
    await user.type(screen.getByLabelText('Senha'), 'Senha@1234');
    await user.type(screen.getByLabelText('Confirmar senha'), 'Senha@1234');

    expect(screen.getByRole('button', { name: 'Enviar cadastro' })).not.toBeDisabled();
  });

  it('submit is disabled when the confirmation does not match', async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <RegisterOwnerPage />
      </MemoryRouter>
    );

    await fillAllFieldsExceptPassword(user);
    await user.type(screen.getByLabelText('Senha'), 'Senha@1234');
    await user.type(screen.getByLabelText('Confirmar senha'), 'Senha@9999');

    expect(screen.getByRole('button', { name: 'Enviar cadastro' })).toBeDisabled();
    expect(screen.getByText(/as senhas n[ãa]o conferem/i)).toBeInTheDocument();
  });
});
