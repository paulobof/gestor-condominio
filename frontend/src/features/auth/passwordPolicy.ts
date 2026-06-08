import { z } from 'zod';

export const passwordRules = [
  { id: 'len', label: 'Mínimo 8 caracteres', test: (v: string) => v.length >= 8 },
  { id: 'upper', label: 'Uma letra maiúscula', test: (v: string) => /[A-Z]/.test(v) },
  { id: 'lower', label: 'Uma letra minúscula', test: (v: string) => /[a-z]/.test(v) },
  { id: 'digit', label: 'Um número', test: (v: string) => /[0-9]/.test(v) },
  { id: 'special', label: 'Um caractere especial', test: (v: string) => /[^A-Za-z0-9]/.test(v) },
] as const;

export const passwordSchema = z
  .string()
  .min(8, 'Mínimo 8 caracteres')
  .max(128, 'Máximo 128 caracteres')
  .regex(/[A-Z]/, 'Pelo menos uma letra maiúscula')
  .regex(/[a-z]/, 'Pelo menos uma letra minúscula')
  .regex(/[0-9]/, 'Pelo menos um número')
  .regex(/[^A-Za-z0-9]/, 'Pelo menos um caractere especial');

export function isStrongPassword(v: string): boolean {
  return passwordRules.every((r) => r.test(v));
}
