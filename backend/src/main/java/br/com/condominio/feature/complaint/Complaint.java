package br.com.condominio.feature.complaint;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "complaint")
@DynamicInsert
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id", callSuper = false)
@ToString(of = {"id", "code", "status"})
@SQLDelete(sql = "UPDATE complaint SET deleted_at = now() WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at IS NULL")
public class Complaint {

  private static final int DESCRIPTION_MIN = 10;
  private static final int DESCRIPTION_MAX = 2000;
  private static final int DETAIL_MAX = 200;
  private static final int REASON_MAX = 500;

  @Id @GeneratedValue private UUID id;
  @Version private Long version;

  /** Número curto exibido como #123; vem da sequence no INSERT. */
  @Generated
  @Column(insertable = false, updatable = false)
  private Long code;

  @Column(name = "author_user_id", nullable = false, updatable = false)
  private UUID authorUserId;

  @Column(name = "author_unit_id", nullable = false, updatable = false)
  private UUID authorUnitId;

  @Column(name = "type_id", nullable = false)
  private UUID typeId;

  @Column(name = "subtype_id", nullable = false)
  private UUID subtypeId;

  @Column(name = "location_id", nullable = false)
  private UUID locationId;

  @Column(name = "location_detail", length = DETAIL_MAX)
  private String locationDetail;

  @Column(nullable = false, length = DESCRIPTION_MAX)
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ComplaintStatus status;

  @Column(name = "due_date")
  private LocalDate dueDate;

  @Column(name = "rejection_reason", length = REASON_MAX)
  private String rejectionReason;

  @Column(name = "resolution_note", length = REASON_MAX)
  private String resolutionNote;

  /** Resposta da síndica, redigida pelo admin; visível aos moradores. */
  @Column(name = "syndic_response", length = DESCRIPTION_MAX)
  private String syndicResponse;

  @Column(name = "syndic_response_at")
  private Instant syndicResponseAt;

  @Column(name = "batch_id")
  private UUID batchId;

  @Column(name = "forwarded_at")
  private Instant forwardedAt;

  @Column(name = "resolved_at")
  private Instant resolvedAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", insertable = false)
  private Instant updatedAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @Transient
  @Getter(AccessLevel.NONE)
  private List<ComplaintChange> changes = new ArrayList<>();

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }

  public static Complaint create(UUID authorId, UUID unitId, ComplaintFields f) {
    Complaint c = new Complaint();
    c.authorUserId = authorId;
    c.authorUnitId = unitId;
    c.apply(f);
    c.status = ComplaintStatus.OPEN;
    c.record(ComplaintEventKind.CREATED, authorId, null, ComplaintStatus.OPEN.name());
    return c;
  }

  public void editByAuthor(UUID actor, ComplaintFields f) {
    requireAuthor(actor);
    requireStatus(ComplaintStatus.OPEN);
    record(ComplaintEventKind.EDITED, actor, null, apply(f));
  }

  public void cancelByAuthor(UUID actor) {
    requireAuthor(actor);
    requireStatus(ComplaintStatus.OPEN);
    record(ComplaintEventKind.CANCELLED, actor, status.name(), null);
  }

  public void startReview(UUID actor) {
    transition(actor, ComplaintStatus.IN_REVIEW, ComplaintStatus.OPEN);
  }

  public void editByAdmin(UUID actor, ComplaintFields f) {
    requireStatus(ComplaintStatus.IN_REVIEW, ComplaintStatus.READY);
    record(ComplaintEventKind.EDITED, actor, null, apply(f));
  }

  public void markReady(UUID actor) {
    transition(actor, ComplaintStatus.READY, ComplaintStatus.IN_REVIEW);
  }

  public void reopenReview(UUID actor) {
    transition(actor, ComplaintStatus.IN_REVIEW, ComplaintStatus.READY);
  }

  public void reject(UUID actor, String reason) {
    String r = reason == null ? "" : reason.strip();
    if (r.isEmpty() || r.length() > REASON_MAX) {
      throw invalidInput("Informe o motivo da recusa (até 500 caracteres).");
    }
    transition(
        actor,
        ComplaintStatus.REJECTED,
        ComplaintStatus.OPEN,
        ComplaintStatus.IN_REVIEW,
        ComplaintStatus.READY);
    rejectionReason = r;
  }

  public void forward(UUID actor, UUID batchId, Instant now) {
    transition(actor, ComplaintStatus.FORWARDED, ComplaintStatus.READY);
    this.batchId = batchId;
    this.forwardedAt = now;
  }

  public void setDueDate(UUID actor, LocalDate due, LocalDate today) {
    requireStatus(ComplaintStatus.FORWARDED);
    if (due == null) throw invalidInput("Informe o prazo.");
    if (due.isBefore(today)) {
      throw new ComplaintException("DUE_DATE_PAST", "O prazo não pode estar no passado.");
    }
    String from = dueDate == null ? null : dueDate.toString();
    dueDate = due;
    record(ComplaintEventKind.DUE_DATE_SET, actor, from, due.toString());
  }

  /** Uma resposta por chamado; o admin edita à vontade. Em branco limpa. */
  public void updateSyndicResponse(UUID actor, String text, Instant now) {
    requireStatus(ComplaintStatus.FORWARDED, ComplaintStatus.RESOLVED);
    String t = text == null || text.isBlank() ? null : text.strip();
    if (t != null && t.length() > DESCRIPTION_MAX) {
      throw invalidInput("A resposta deve ter até 2000 caracteres.");
    }
    if (Objects.equals(t, syndicResponse)) return;
    syndicResponse = t;
    syndicResponseAt = t == null ? null : now;
    record(ComplaintEventKind.RESPONSE_SET, actor, null, null);
  }

  public void resolve(UUID actor, String note, Instant now) {
    String n = note == null || note.isBlank() ? null : note.strip();
    if (n != null && n.length() > REASON_MAX) {
      throw invalidInput("A observação deve ter até 500 caracteres.");
    }
    transition(actor, ComplaintStatus.RESOLVED, ComplaintStatus.FORWARDED);
    resolutionNote = n;
    resolvedAt = now;
  }

  public void requirePhotoEditableBy(UUID actor) {
    requireAuthor(actor);
    requireStatus(ComplaintStatus.OPEN);
  }

  public boolean isAuthor(UUID userId) {
    return authorUserId.equals(userId);
  }

  /** No mural: já encaminhado à síndica (ou resolvido depois disso). */
  public boolean isOnBoard() {
    return status == ComplaintStatus.FORWARDED || status == ComplaintStatus.RESOLVED;
  }

  /** Autor e gestor sempre veem; o mural só é visível para quem tem unidade vinculada. */
  public boolean isVisibleTo(UUID userId, UUID unitId, boolean canManage) {
    return canManage || isAuthor(userId) || (isOnBoard() && unitId != null);
  }

  /** Itens de catálogo já em uso — aceitos mesmo se desativados depois. */
  public Set<UUID> usedCatalogIds() {
    return Set.of(typeId, subtypeId, locationId);
  }

  public List<ComplaintChange> drainChanges() {
    List<ComplaintChange> out = List.copyOf(changes);
    changes.clear();
    return out;
  }

  /** Aplica os campos e devolve os nomes dos campos alterados (para o evento, sem conteúdo). */
  private String apply(ComplaintFields f) {
    String desc = f.description() == null ? "" : f.description().strip();
    // O CHECK do banco conta code points (length(btrim)); alinhar para não divergir com emojis.
    int descLen = desc.codePointCount(0, desc.length());
    if (descLen < DESCRIPTION_MIN || descLen > DESCRIPTION_MAX) {
      throw invalidInput("A descrição deve ter entre 10 e 2000 caracteres.");
    }
    String detail =
        f.locationDetail() == null || f.locationDetail().isBlank()
            ? null
            : f.locationDetail().strip();
    if (detail != null && detail.length() > DETAIL_MAX) {
      throw invalidInput("O complemento do local deve ter até 200 caracteres.");
    }
    List<String> changed = new ArrayList<>();
    if (!Objects.equals(typeId, f.typeId())) changed.add("type");
    if (!Objects.equals(subtypeId, f.subtypeId())) changed.add("subtype");
    if (!Objects.equals(locationId, f.locationId())) changed.add("location");
    if (!Objects.equals(locationDetail, detail)) changed.add("locationDetail");
    if (!Objects.equals(description, desc)) changed.add("description");
    typeId = f.typeId();
    subtypeId = f.subtypeId();
    locationId = f.locationId();
    locationDetail = detail;
    description = desc;
    return String.join(",", changed);
  }

  private void transition(UUID actor, ComplaintStatus to, ComplaintStatus... allowedFrom) {
    requireStatus(allowedFrom);
    ComplaintStatus from = status;
    status = to;
    record(ComplaintEventKind.STATUS_CHANGED, actor, from.name(), to.name());
  }

  private void requireStatus(ComplaintStatus... allowed) {
    if (!Arrays.asList(allowed).contains(status)) {
      throw new ComplaintException(
          "INVALID_STATE", "Ação não permitida para chamado com status " + status + ".");
    }
  }

  /** Não-autor recebe "não encontrado": não revela que o chamado existe. */
  private void requireAuthor(UUID actor) {
    if (!isAuthor(actor)) {
      throw new ComplaintException("NOT_FOUND", "Chamado não encontrado.");
    }
  }

  private void record(ComplaintEventKind kind, UUID actor, String from, String to) {
    changes.add(new ComplaintChange(kind, actor, from, to));
  }

  private static ComplaintException invalidInput(String msg) {
    return new ComplaintException("INVALID_INPUT", msg);
  }
}
