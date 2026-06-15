package br.com.condominio.feature.activity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Config dos avisos de atividade no grupo WhatsApp de admins (prefixo {@code app.alerts.whatsapp}).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.alerts.whatsapp")
public class ActivityAlertProperties {

  /** Liga o envio dos avisos de atividade. Off por padrão (on no env de HML/PRD). */
  private boolean enabled = false;

  /** JID do grupo WhatsApp destino (ex.: {@code 120363409829888116@g.us}). */
  private String groupJid;
}
