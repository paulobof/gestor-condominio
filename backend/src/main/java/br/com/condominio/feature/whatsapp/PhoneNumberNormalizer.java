package br.com.condominio.feature.whatsapp;

import org.springframework.stereotype.Component;

/**
 * Normaliza telefones para o formato que o Evolution API exige: apenas dígitos com DDI (ex.: {@code
 * 5511988887777}). Heurística BR-only (condomínio é nacional): remove tudo que não é dígito; se
 * sobrar 10 (fixo) ou 11 (celular) dígitos — DDD+número sem DDI — prefixa {@code 55}; 12/13 dígitos
 * são tratados como já contendo DDI. Qualquer outro tamanho lança {@link WhatsAppSendException}
 * para a outbox marcar FAILED com motivo rastreável.
 *
 * <p>Mensagens de erro nunca incluem o telefone (PII — CLAUDE.md), apenas o tamanho.
 */
@Component
public class PhoneNumberNormalizer {

  public String toEvolutionNumber(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new WhatsAppSendException("Telefone vazio para envio WhatsApp");
    }
    String digits = raw.replaceAll("\\D", "");
    if (digits.length() == 10 || digits.length() == 11) {
      digits = "55" + digits;
    }
    if (digits.length() != 12 && digits.length() != 13) {
      throw new WhatsAppSendException(
          "Telefone com tamanho inválido após normalização: " + digits.length() + " dígitos");
    }
    return digits;
  }
}
