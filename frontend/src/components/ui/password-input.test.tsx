import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect } from 'vitest';
import { PasswordInput } from './password-input';

describe('PasswordInput', () => {
  it('toggles visibility when the eye button is clicked', async () => {
    render(<PasswordInput aria-label="senha" defaultValue="secret" />);
    const input = screen.getByLabelText('senha') as HTMLInputElement;
    expect(input.type).toBe('password');

    await userEvent.click(screen.getByRole('button', { name: 'Mostrar senha' }));
    expect(input.type).toBe('text');
    expect(screen.getByRole('button', { name: 'Ocultar senha' })).toBeInTheDocument();
  });
});
