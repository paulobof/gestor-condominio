package br.com.condominio.feature.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.condominio.feature.role.PermissionResolver;
import br.com.condominio.feature.role.RoleName;
import br.com.condominio.feature.whatsapp.WhatsAppNotificationClient;
import br.com.condominio.feature.whatsapp.WhatsAppOutboxEntry;
import br.com.condominio.feature.whatsapp.WhatsAppOutboxService;
import br.com.condominio.feature.whatsapp.WhatsAppTemplate;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ActivityWhatsAppListenerTest {

  private static final String JID = "120363409829888116@g.us";

  private ActivityAlertProperties props;
  private WhatsAppOutboxService outbox;
  private WhatsAppNotificationClient client;
  private PermissionResolver permissionResolver;
  private ActivityWhatsAppListener listener;

  @BeforeEach
  void setUp() {
    props = new ActivityAlertProperties();
    props.setEnabled(true);
    props.setGroupJid(JID);
    outbox = mock(WhatsAppOutboxService.class);
    client = mock(WhatsAppNotificationClient.class);
    permissionResolver = mock(PermissionResolver.class);
    listener = new ActivityWhatsAppListener(props, outbox, client, permissionResolver, "hml");
  }

  @Test
  void disabled_doesNothing() {
    props.setEnabled(false);
    listener.onActivity(new ActivityEvent(ActivityAction.CREATED, "Documento", "x", null));
    verifyNoInteractions(outbox, client, permissionResolver);
  }

  @Test
  void enabled_enqueuesAndSends_withFormattedTextAndRole() {
    UUID actor = UUID.randomUUID();
    when(permissionResolver.roles(actor)).thenReturn(List.of(RoleName.MANAGER));
    WhatsAppOutboxEntry entry = mock(WhatsAppOutboxEntry.class);
    UUID outboxId = UUID.randomUUID();
    when(entry.getId()).thenReturn(outboxId);
    when(outbox.enqueue(eq(JID), eq(WhatsAppTemplate.ACTIVITY_ALERT), any())).thenReturn(entry);

    listener.onActivity(
        new ActivityEvent(ActivityAction.CREATED, "Indicação", "Encanador Zé", actor));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> data = ArgumentCaptor.forClass(Map.class);
    verify(client).send(eq(JID), eq(WhatsAppTemplate.ACTIVITY_ALERT), data.capture());
    String text = String.valueOf(data.getValue().get("text"));
    assertThat(text)
        .contains("[HML]")
        .contains("Criado")
        .contains("Indicação")
        .contains("Encanador Zé")
        .contains("MANAGER");
    verify(outbox).markSent(eq(outboxId), any(Instant.class));
  }

  @Test
  void buildText_noActor_omitsRole() {
    String text =
        listener.buildText(new ActivityEvent(ActivityAction.UPDATED, "Documento", "RI 2026", null));
    assertThat(text).isEqualTo("✏️ [HML] Editado · Documento: 'RI 2026'");
  }

  @Test
  void buildText_deleted_hasTrashIconAndExcluido() {
    String text =
        listener.buildText(new ActivityEvent(ActivityAction.DELETED, "Aviso", null, null));
    assertThat(text).startsWith("🗑️ [HML] Excluído · Aviso");
  }
}
