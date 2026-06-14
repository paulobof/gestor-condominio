package br.com.condominio.feature.announcement;

/**
 * Nível de importância de um aviso do mural.
 *
 * <ul>
 *   <li>{@link #HIGH} — Urgente (vermelho)
 *   <li>{@link #MEDIUM} — Importante (amarelo) — padrão
 *   <li>{@link #LOW} — Informativo (azul)
 * </ul>
 */
public enum AnnouncementImportance {
  HIGH,
  MEDIUM,
  LOW
}
