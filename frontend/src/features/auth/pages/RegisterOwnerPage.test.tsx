import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('react-router-dom', async (orig) => {
  const actual = await orig<typeof import('react-router-dom')>();
  return { ...actual, useNavigate: () => vi.fn() };
});
vi.mock('@/features/consent/api/consentApi', () => ({
  registerOwner: vi.fn(),
  fetchCurrent: vi.fn().mockResolvedValue({ version: '1.0.0', body: 'Termo de privacidade.' }),
}));
vi.mock('sonner', () => ({ toast: { error: vi.fn(), success: vi.fn() } }));

import { RegisterOwnerPage } from './RegisterOwnerPage';

beforeEach(() => vi.clearAllMocks());

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
});
