import { describe, it, expect } from 'vitest';
import { isStrongPassword, passwordRules } from './passwordPolicy';

describe('passwordPolicy', () => {
  it('accepts a strong password', () => {
    expect(isStrongPassword('Senha@1234')).toBe(true);
  });

  it.each([
    ['too short', 'Aa1@bc'],
    ['no uppercase', 'senha@1234'],
    ['no lowercase', 'SENHA@1234'],
    ['no digit', 'Senha@abcd'],
    ['no special', 'Senha12345'],
  ])('rejects when %s', (_label, value) => {
    expect(isStrongPassword(value)).toBe(false);
  });

  it('exposes one rule per criterion', () => {
    expect(passwordRules).toHaveLength(5);
  });
});
