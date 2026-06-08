import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { PasswordChecklist } from './PasswordChecklist';

describe('PasswordChecklist', () => {
  it('marks all rules met for a strong password', () => {
    render(<PasswordChecklist value="Senha@1234" />);
    const items = screen.getAllByRole('listitem');
    expect(items).toHaveLength(5);
    items.forEach((li) => expect(li).toHaveAttribute('data-met', 'true'));
  });

  it('marks rules unmet for a weak password', () => {
    render(<PasswordChecklist value="abc" />);
    const met = screen
      .getAllByRole('listitem')
      .filter((li) => li.getAttribute('data-met') === 'true');
    expect(met).toHaveLength(1); // só "minúscula"
  });
});
