package br.com.condominio.feature.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.condominio.feature.password.PasswordResetProperties;
import br.com.condominio.feature.password.PasswordResetTokenRepository;
import br.com.condominio.feature.password.event.UserInvitedEvent;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PasswordResetEventListenerTest {

  private WhatsAppOutboxService outbox;
  private WhatsAppNotificationClient client;
  private PasswordResetTokenRepository tokenRepo;
  private PasswordResetEventListener listener;
  private final UUID outboxId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    outbox = mock(WhatsAppOutboxService.class);
    client = mock(WhatsAppNotificationClient.class);
    tokenRepo = mock(PasswordResetTokenRepository.class);
    PasswordResetProperties props = new PasswordResetProperties();
    props.setBaseUrl("https://app/reset");
    listener = new PasswordResetEventListener(outbox, client, props, tokenRepo);
    WhatsAppOutboxEntry entry = mock(WhatsAppOutboxEntry.class);
    when(entry.getId()).thenReturn(outboxId);
    when(outbox.enqueue(anyString(), any(), any())).thenReturn(entry);
  }

  @SuppressWarnings("unchecked")
  @Test
  void onUserInvited_enviaConviteComLinkDePrimeiroAcessoEMarcaEntregue() {
    UUID tokenId = UUID.randomUUID();
    listener.onUserInvited(
        new UserInvitedEvent(UUID.randomUUID(), tokenId, "RAW", "+5511999997777", "Ana", 72));

    ArgumentCaptor<Map<String, Object>> data = ArgumentCaptor.forClass(Map.class);
    verify(client).send(eq("+5511999997777"), eq(WhatsAppTemplate.ACCOUNT_INVITE), data.capture());
    assertThat(data.getValue())
        .containsEntry("greetingName", "Ana")
        .containsEntry("link", "https://app/reset?token=RAW&primeiro=1")
        .containsEntry("ttlHours", 72L);
    verify(outbox).markSent(eq(outboxId), any(Instant.class));
    verify(tokenRepo).markDelivered(eq(tokenId), any(Instant.class));
  }

  @Test
  void onUserInvited_semNomeUsaVazio() {
    listener.onUserInvited(
        new UserInvitedEvent(
            UUID.randomUUID(), UUID.randomUUID(), "RAW", "+5511999997777", null, 72));

    verify(outbox)
        .enqueue(
            eq("+5511999997777"),
            eq(WhatsAppTemplate.ACCOUNT_INVITE),
            org.mockito.ArgumentMatchers.argThat(m -> "".equals(m.get("greetingName"))));
  }

  @Test
  void onUserInvited_falhaNoEnvioMarcaFailedENaoMarcaEntregue() {
    doThrow(new WhatsAppSendException("down")).when(client).send(anyString(), any(), any());

    listener.onUserInvited(
        new UserInvitedEvent(
            UUID.randomUUID(), UUID.randomUUID(), "RAW", "+5511999997777", "Ana", 72));

    verify(outbox).markFailed(eq(outboxId), eq("down"), any(Instant.class));
    verify(tokenRepo, never()).markDelivered(any(), any());
  }

  @Test
  void onUserInvited_semTelefoneIgnora() {
    listener.onUserInvited(
        new UserInvitedEvent(UUID.randomUUID(), UUID.randomUUID(), "RAW", " ", "Ana", 72));

    verifyNoInteractions(outbox, client, tokenRepo);
  }
}
