import { describe, it, expect } from 'vitest';
import {
  parsePhone,
  formatNational,
  isValidPhone,
  toFullDigits,
  onlyDigits,
  DEFAULT_DDI,
} from './phone';

describe('phone utils', () => {
  describe('onlyDigits', () => {
    it('remove tudo que não é dígito', () => {
      expect(onlyDigits('+55 (11) 98888-7777')).toBe('5511988887777');
      expect(onlyDigits('')).toBe('');
    });
  });

  describe('parsePhone', () => {
    it('número vazio cai no DDI padrão (55) com nacional vazio', () => {
      expect(parsePhone('')).toEqual({ ddi: '55', national: '' });
    });

    it('BR com DDI (13 díg.) separa 55 + celular', () => {
      expect(parsePhone('5511988887777')).toEqual({ ddi: '55', national: '11988887777' });
    });

    it('BR com DDI (12 díg.) separa 55 + fixo', () => {
      expect(parsePhone('551133334444')).toEqual({ ddi: '55', national: '1133334444' });
    });

    it('BR sem DDI (11 díg.) é tratado como nacional brasileiro', () => {
      expect(parsePhone('11988887777')).toEqual({ ddi: '55', national: '11988887777' });
    });

    it('aceita colar com símbolos', () => {
      expect(parsePhone('+55 (11) 98888-7777')).toEqual({ ddi: '55', national: '11988887777' });
    });

    it('reconhece outro DDI conhecido (Portugal +351)', () => {
      expect(parsePhone('351912345678')).toEqual({ ddi: '351', national: '912345678' });
    });
  });

  describe('formatNational', () => {
    it('mascara celular BR (5-4)', () => {
      expect(formatNational('55', '11988887777')).toBe('(11) 98888-7777');
    });

    it('mascara fixo BR (4-4)', () => {
      expect(formatNational('55', '1133334444')).toBe('(11) 3333-4444');
    });

    it('não mascara DDI não-BR (dígitos crus)', () => {
      expect(formatNational('351', '912345678')).toBe('912345678');
    });

    it('limita o nacional BR a 11 dígitos', () => {
      expect(onlyDigits(formatNational('55', '119888877771234'))).toHaveLength(11);
    });
  });

  describe('isValidPhone', () => {
    it('BR válido com 10 ou 11 dígitos', () => {
      expect(isValidPhone('55', '11988887777')).toBe(true);
      expect(isValidPhone('55', '1133334444')).toBe(true);
    });

    it('BR inválido com menos de 10 dígitos', () => {
      expect(isValidPhone('55', '119888')).toBe(false);
    });

    it('outro DDI aceita faixa 6–14 dígitos', () => {
      expect(isValidPhone('351', '912345678')).toBe(true);
      expect(isValidPhone('351', '123')).toBe(false);
    });
  });

  describe('toFullDigits', () => {
    it('concatena DDI + nacional', () => {
      expect(toFullDigits('55', '11988887777')).toBe('5511988887777');
    });

    it('vazio quando não há número', () => {
      expect(toFullDigits('55', '')).toBe('');
    });
  });

  it('DEFAULT_DDI é Brasil', () => {
    expect(DEFAULT_DDI).toBe('55');
  });
});
