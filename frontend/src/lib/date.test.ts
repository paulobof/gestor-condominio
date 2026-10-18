import { describe, it, expect } from 'vitest';
import { formatDateBR, formatInstantBR } from './date';

describe('date utils', () => {
  describe('formatDateBR', () => {
    it('converte ISO yyyy-MM-dd para dd/MM/yyyy', () => {
      expect(formatDateBR('1990-01-02')).toBe('02/01/1990');
      expect(formatDateBR('2003-09-05')).toBe('05/09/2003');
    });

    it('não desloca o dia (sem usar Date/UTC)', () => {
      // '2003-09-05' interpretado como Date UTC viraria 04/09 em America/Sao_Paulo
      expect(formatDateBR('2003-09-05')).toBe('05/09/2003');
    });

    it('ignora a parte de hora se vier um ISO datetime', () => {
      expect(formatDateBR('1990-01-02T00:00:00Z')).toBe('02/01/1990');
    });

    it('retorna travessão para nulo/indefinido/vazio', () => {
      expect(formatDateBR(null)).toBe('—');
      expect(formatDateBR(undefined)).toBe('—');
      expect(formatDateBR('')).toBe('—');
    });

    it('devolve o valor original se não for ISO reconhecível', () => {
      expect(formatDateBR('02/01/1990')).toBe('02/01/1990');
      expect(formatDateBR('abc')).toBe('abc');
    });
  });

  describe('formatInstantBR', () => {
    it('converte instante UTC para a data em America/Sao_Paulo', () => {
      expect(formatInstantBR('2026-10-18T02:30:00Z')).toBe('17/10/2026');
      expect(formatInstantBR('2026-10-18T12:00:00Z')).toBe('18/10/2026');
    });

    it('retorna travessão para nulo/indefinido/vazio', () => {
      expect(formatInstantBR(null)).toBe('—');
      expect(formatInstantBR(undefined)).toBe('—');
      expect(formatInstantBR('')).toBe('—');
    });
  });
});
