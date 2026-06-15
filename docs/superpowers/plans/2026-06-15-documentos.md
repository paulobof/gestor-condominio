# Documentos do Condomínio — Plano de Implementação

**Goal:** Tela de Documentos onde qualquer autenticado vê/baixa documentos (RI, AGE, etc., PDFs no MinIO), mas só quem tem a role **Editor de Documentos** (permission `DOCUMENT_MANAGE`) pode subir/excluir.

**Arquitetura:** Feature vertical atrás da flag `app.feature.documents.enabled`. Backend Spring (entity soft-delete + service + controller `@ConditionalOnProperty` + `@PreAuthorize`), upload no MinIO (bucket `documents`, magic-bytes PDF). Nova permission `DOCUMENT_MANAGE` (id 21) + nova role `DOCUMENT_EDITOR` (id 8, atribuível pela tela de acessos) — espelha o padrão de `MURAL_EDITOR`/`ANNOUNCEMENT_MANAGE`. Frontend React (lista + upload gated por authority, download via blob autenticado).

## Backend
- `feature/document/`: `DocumentType` (RI/AGE/AGO/ATA/CONVENCAO/EDITAL/OUTRO), `Document` (entity `@SQLDelete`/`@SQLRestriction`/`@Version`), `DocumentException` (code), `DocumentRepository`, `dto/DocumentView`, `DocumentService` (upload fora de tx, list, download bytes, soft delete), `DocumentController` (`/api/documents`).
- Endpoints: `GET /api/documents` (autenticado), `POST /api/documents` multipart (`DOCUMENT_MANAGE`), `GET /api/documents/{id}/file` (autenticado, stream), `DELETE /api/documents/{id}` (`DOCUMENT_MANAGE`).
- Infra: `MinioProperties.bucketDocuments`, `MinioBootstrap` (+bucket), `MagicBytesValidator.isAcceptedForDocument` (PDF), `PermissionCode.DOCUMENT_MANAGE`, `RoleName.DOCUMENT_EDITOR`, `GlobalExceptionHandler.handleDocument`, migration `V34__documents.sql` (tabela + permission 21 + role 8 + role_permission p/ DOCUMENT_EDITOR e MANAGER), `application.yml` (bucket + flag), env de exemplo.
- Testes: `DocumentServiceTest` (Mockito: upload feliz/PDF-only/too-large/blank-title, list, download, delete), `DocumentControllerWebTest` (`@WebMvcTest`: 201 com DOCUMENT_MANAGE, 403 sem, 401 anon, list 200, exceção→status).

## Frontend
- `features/documents/api/documentsApi.ts` (list/upload multipart/getBlob/delete), `pages/DocumentsPage.tsx` (lista + badge de tipo + download; form de upload e botão excluir só se `authorities.includes('DOCUMENT_MANAGE')`).
- Rota `/documentos`, item no Sidebar (ícone FileText, visível a todo autenticado).
- Testes: `DocumentsPage.test.tsx` (lista, upload visível só com permission, fluxo de upload, download via blob).

## Rollout
- Flag off por padrão. Em HML: `APP_FEATURE_DOCUMENTS_ENABLED=true` + `MINIO_BUCKET_DOCUMENTS` no Dokploy; atribuir a role "Editor de Documentos" pela tela de acessos.
