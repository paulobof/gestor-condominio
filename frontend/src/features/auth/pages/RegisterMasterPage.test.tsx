import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';

let unitHasMaster = false;

vi.mock('@/components/UnitSelector', () => ({
  UnitSelector: ({ onChange }: { onChange: (c: string, h: boolean) => void }) => (
    <button onClick={() => onChange('602C', unitHasMaster)}>pick-unit</button>
  ),
}));
vi.mock('@/features/consent/ConsentBox', () => ({
  ConsentBox: ({ onChange }: { onChange: (a: boolean, v: string) => void }) => (
    <button onClick={() => onChange(true, '1.0.0')}>accept-consent</button>
  ),
}));
vi.mock('@/features/consent/api/consentApi', () => ({ registerMaster: vi.fn() }));
vi.mock('sonner', () => ({ toast: { error: vi.fn(), success: vi.fn(), info: vi.fn() } }));

const navigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom');
  return { ...actual, useNavigate: () => navigate };
});

const login = vi.fn();
vi.mock('@/features/auth/useAuth', () => ({ useAuth: () => ({ login }) }));

import { RegisterMasterPage } from './RegisterMasterPage';
import { registerMaster } from '@/features/consent/api/consentApi';

async function fillForm(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByRole('button', { name: 'pick-unit' }));
  await user.type(screen.getByLabelText('Nome completo'), 'Maria Souza');
  await user.type(screen.getByLabelText('Como prefere ser chamado'), 'Maria');
  await user.type(screen.getByLabelText('E-mail'), 'maria@example.com');
  await user.type(screen.getByLabelText('Telefone (WhatsApp)'), '11999990000');
  await user.click(screen.getByRole('button', { name: 'accept-consent' }));
}

describe('RegisterMasterPage', () => {
  beforeEach(() => {
    unitHasMaster = false;
    vi.clearAllMocks();
  });

  it('não pede comprovante, data de nascimento nem gênero', () => {
    render(
      <MemoryRouter>
        <RegisterMasterPage />
      </MemoryRouter>
    );
    expect(screen.queryByText(/comprovante/i)).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/nascimento/i)).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/g[êe]nero/i)).not.toBeInTheDocument();
  });

  it('habilita o envio mesmo quando a unidade já tem master (vira pedido)', async () => {
    unitHasMaster = true;
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <RegisterMasterPage />
      </MemoryRouter>
    );
    await fillForm(user);
    await user.type(screen.getByLabelText('Senha'), 'Senha@1234');
    await user.type(screen.getByLabelText('Confirmar senha'), 'Senha@1234');

    expect(screen.getByRole('button', { name: /criar minha conta/i })).not.toBeDisabled();
  });

  it('entra direto quando o cadastro volta ACTIVE', async () => {
    vi.mocked(registerMaster).mockResolvedValue({ userId: 'u1', status: 'ACTIVE' });
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <RegisterMasterPage />
      </MemoryRouter>
    );
    await fillForm(user);
    await user.type(screen.getByLabelText('Senha'), 'Senha@1234');
    await user.type(screen.getByLabelText('Confirmar senha'), 'Senha@1234');
    await user.click(screen.getByRole('button', { name: /criar minha conta/i }));

    await waitFor(() => expect(login).toHaveBeenCalledWith('maria@example.com', 'Senha@1234'));
    expect(navigate).toHaveBeenCalledWith('/', { replace: true });
  });

  it('vai para a espera quando o cadastro volta PENDING_APPROVAL', async () => {
    unitHasMaster = true;
    vi.mocked(registerMaster).mockResolvedValue({ userId: 'u1', status: 'PENDING_APPROVAL' });
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <RegisterMasterPage />
      </MemoryRouter>
    );
    await fillForm(user);
    await user.type(screen.getByLabelText('Senha'), 'Senha@1234');
    await user.type(screen.getByLabelText('Confirmar senha'), 'Senha@1234');
    await user.click(screen.getByRole('button', { name: /criar minha conta/i }));

    await waitFor(() =>
      expect(navigate).toHaveBeenCalledWith('/pending-approval', { replace: true })
    );
    expect(login).not.toHaveBeenCalled();
  });

  it('mantém o envio bloqueado com senha fraca ou confirmação divergente', async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <RegisterMasterPage />
      </MemoryRouter>
    );
    await fillForm(user);
    await user.type(screen.getByLabelText('Senha'), 'senha12345');
    expect(screen.getByRole('button', { name: /criar minha conta/i })).toBeDisabled();

    await user.clear(screen.getByLabelText('Senha'));
    await user.type(screen.getByLabelText('Senha'), 'Senha@1234');
    await user.type(screen.getByLabelText('Confirmar senha'), 'Senha@9999');
    expect(screen.getByRole('button', { name: /criar minha conta/i })).toBeDisabled();
    expect(screen.getByText(/as senhas n[ãa]o conferem/i)).toBeInTheDocument();
  });
});
