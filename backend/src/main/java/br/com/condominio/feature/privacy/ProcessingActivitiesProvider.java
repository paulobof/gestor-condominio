package br.com.condominio.feature.privacy;

import br.com.condominio.feature.privacy.dto.ProcessingActivityView;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Provedor estático das atividades de tratamento. Reflete o ROPA (docs/lgpd/ropa.md). Sempre que
 * mudar o conteúdo, atualizar este arquivo + o markdown — eles devem permanecer em sincronia.
 */
@Component
public class ProcessingActivitiesProvider {

  private static final List<ProcessingActivityView> ACTIVITIES =
      List.of(
          new ProcessingActivityView(
              "Autenticação e gestão da conta",
              "Execução de contrato (LGPD Art. 7, V)",
              List.of("nome completo", "e-mail", "telefone", "senha (hash)", "unidade"),
              "Enquanto a conta existir; 12 meses sem login → anonimização automática",
              List.of("Dokploy (hosting)", "PostgreSQL (banco)"),
              false),
          new ProcessingActivityView(
              "Validação de residência (comprovante)",
              "Execução de contrato + obrigação legal (Lei 4.591/64)",
              List.of("comprovante de residência (PDF/imagem)", "data de upload", "IP"),
              "180 dias após aprovação do master, então purga automática",
              List.of("MinIO (storage)", "PostgreSQL (metadados)"),
              false),
          new ProcessingActivityView(
              "Reset de senha via WhatsApp",
              "Execução de contrato (LGPD Art. 7, V)",
              List.of("telefone", "token (hash)"),
              "30 dias após consumo/expiração, então purga automática",
              List.of("Bot WhatsApp do Paulo (operador)", "PostgreSQL"),
              false),
          new ProcessingActivityView(
              "Comunicações operacionais via WhatsApp",
              "Consentimento (LGPD Art. 7, I) — opt-in revogável",
              List.of("telefone", "mensagens transacionais (reset, avisos)"),
              "Outbox: 90 dias",
              List.of("Bot WhatsApp do Paulo (operador)"),
              true),
          new ProcessingActivityView(
              "Auditoria e logs",
              "Legítimo interesse (LGPD Art. 7, IX) — defesa em juízo, segurança",
              List.of("IP", "user-agent", "ações realizadas", "timestamps"),
              "App stdout 30 dias; logs de auditoria DB indefinidos",
              List.of("Dokploy (coleta stdout)", "PostgreSQL"),
              false),
          new ProcessingActivityView(
              "Backups",
              "Legítimo interesse — continuidade do negócio",
              List.of("todos os dados acima"),
              "Política do provedor de backup",
              List.of("Dokploy (hosting do servidor de backup)"),
              false));

  public List<ProcessingActivityView> list() {
    return ACTIVITIES;
  }
}
