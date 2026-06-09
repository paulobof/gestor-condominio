package br.com.condominio.feature.whatsapp;

import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Renderiza o texto final (PT-BR) das mensagens WhatsApp a partir do {@link WhatsAppTemplate} e do
 * mapa de {@code data}. Antes a renderização era responsabilidade do bot intermediário; com a
 * integração direta no Evolution API (que só recebe texto pronto) ela passou para o backend.
 *
 * <p>Campo obrigatório ausente/nulo lança {@link WhatsAppSendException} — nunca envia texto com
 * {@code null} ou placeholder cru.
 */
@Component
public class WhatsAppMessageRenderer {

  private static final String CONDO = "HELBOR TRILOGY HOME";

  public String render(WhatsAppTemplate template, Map<String, Object> data) {
    Map<String, Object> d = data == null ? Map.of() : data;
    return switch (template) {
      case PASSWORD_RESET ->
          "Olá, "
              + req(d, "greetingName", template)
              + "! 👋\n\n"
              + "Você pediu a redefinição da sua senha no "
              + CONDO
              + ".\n\n"
              + "Crie uma nova senha pelo link (válido por "
              + req(d, "ttlMinutes", template)
              + " min):\n"
              + req(d, "link", template)
              + "\n\n"
              + "Não foi você? Pode ignorar esta mensagem.";
      case PASSWORD_CHANGED ->
          "Olá, "
              + req(d, "greetingName", template)
              + "! ✅\n\n"
              + "Sua senha do "
              + CONDO
              + " foi alterada com sucesso.\n\n"
              + "Não reconhece? Fale com a administração imediatamente.";
      case INACTIVITY_WARNING ->
          "Olá, "
              + req(d, "greetingName", template)
              + ".\n\n"
              + "Sua conta no "
              + CONDO
              + " será anonimizada em "
              + req(d, "daysLeft", template)
              + " dias por inatividade (LGPD). Faça login para mantê-la ativa.";
    };
  }

  private static String req(Map<String, Object> data, String key, WhatsAppTemplate template) {
    Object value = data.get(key);
    if (value == null) {
      throw new WhatsAppSendException(
          "Campo '" + key + "' ausente no data do template " + template);
    }
    return String.valueOf(value);
  }
}
