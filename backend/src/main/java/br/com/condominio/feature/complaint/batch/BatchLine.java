package br.com.condominio.feature.complaint.batch;

/** Uma linha do lote, já com nomes resolvidos. Sem dados do autor por construção. */
public record BatchLine(
    long code,
    String typeName,
    int typeOrder,
    String subtypeName,
    String locationName,
    String locationDetail,
    String description) {}
