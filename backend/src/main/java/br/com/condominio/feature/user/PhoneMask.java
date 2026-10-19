package br.com.condominio.feature.user;

/**
 * Máscara de telefone para exibição: só os 4 últimos dígitos ("•••• 7777"). Usada nas respostas de
 * cadastro para confirmar para onde o convite foi enviado sem devolver o número completo.
 */
public final class PhoneMask {

  private static final String DOTS = "••••";
  private static final int VISIBLE = 4;

  private PhoneMask() {}

  public static String lastFour(String phone) {
    if (phone == null) {
      return DOTS;
    }
    String digits = phone.replaceAll("\\D", "");
    if (digits.length() <= VISIBLE) {
      return DOTS;
    }
    return DOTS + " " + digits.substring(digits.length() - VISIBLE);
  }
}
