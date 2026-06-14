# Design: melhorias em Indicações — links sociais e associação ao morador

**Data:** 2026-06-13
**Status:** rascunho
**Contexto:** Pedido do Paulo (sessão 2026-06-13). Complementa o spec de remoção de consentimento
(`2026-06-09-indicacoes-sem-consentimento-design.md`) e o design geral
(`2026-05-24-gestor-condominio-design.md`, seção Indicações).

---

## 1. Motivação

O cadastro atual de indicações possui apenas `phone` (varchar 20) como campo de contato. Para
que uma indicação seja realmente útil, os moradores precisam de:

1. **Links de redes sociais** — Instagram e Facebook do profissional/estabelecimento.
2. **Link de WhatsApp** — atalho direto para conversa (wa.me).
3. **Link de catálogo / cardápio online** — website, iFood, link externo qualquer.
4. **Associação explícita ao morador-dono** — quando o profissional indicado é um morador, a
   unidade do morador deve aparecer vinculada; quando é o próprio morador cadastrando, ele só
   precisa sinalizar "sou eu" e a unidade vem do seu próprio token.

---

## 2. Estado atual (baseline)

### Entidade `Recommendation` (`V17__recommendations.sql`)

| Coluna | Tipo | Notas |
|---|---|---|
| `service_name` | varchar(120) NOT NULL | |
| `professional_name` | varchar(120) | |
| `phone` | varchar(20) | único campo de contato |
| `is_resident` | boolean NOT NULL | flag informativo |
| `resident_user_id` | uuid FK user | nullable; exige `is_resident=true` (CHECK) |
| `address_line` | varchar(255) | |
| `price_range` | varchar(40) | |
| `rating` | smallint | 1–5 |
| `comment` | text | |

Não existem campos para redes sociais, WhatsApp ou catálogo.

### Modelo atual de "morador" (`is_resident` / `resident_user_id`)

`CreateRecommendationRequest` recebe `isResident` (boolean) e `residentUserId` (UUID).
A entidade valida: se `resident=true` então `residentUserId != null`. A **unidade** do morador
indicado nunca é persistida na indicação — qualquer display de "mora aqui" usa apenas a flag
`is_resident`.

### JWT / `AuthenticatedUserPrincipal`

```java
public record AuthenticatedUserPrincipal(
    UUID userId, String displayName,
    List<String> roles, List<String> authorities,
    UUID unitId,       // nullable: null se usuário sem unidade
    boolean isUnitMaster) {}
```

O `unitId` está disponível em toda requisição autenticada sem round-trip ao banco.

---

## 3. Novos campos propostos

### 3.1 Links sociais e de contato

| Campo lógico | Coluna DB | Tipo DB | Constraint |
|---|---|---|---|
| Instagram | `instagram_url` | varchar(255) | nullable |
| Facebook | `facebook_url` | varchar(255) | nullable |
| WhatsApp | `whatsapp_url` | varchar(255) | nullable |
| Catálogo / cardápio | `catalog_url` | varchar(500) | nullable; URL longa (iFood, etc.) |

**Todos os campos são opcionais.** Nenhum deles substitui `phone` (que permanece).

#### Validação de formato (backend — Bean Validation)

```java
@URL(regexp = "https?://.*", message = "URL inválida.")
@Size(max = 255)
String instagramUrl;

@URL(regexp = "https?://.*", message = "URL inválida.")
@Size(max = 255)
String facebookUrl;

@URL(regexp = "https?://.*", message = "URL inválida.")
@Size(max = 255)
String whatsappUrl;   // aceita wa.me/… ou api.whatsapp.com/… já montado pelo cliente

@URL(regexp = "https?://.*", message = "URL inválida.")
@Size(max = 500)
String catalogUrl;
```

A anotação `@URL` do Hibernate Validator exige esquema `http` ou `https`; rejeita `@insta`,
`wa.me` sem esquema, etc.

#### Montagem do link de WhatsApp (frontend)

O campo `whatsappUrl` armazena a URL final (`https://wa.me/5511999999999`). O frontend oferece
**duas opções de entrada** (select + input):

- **Já tenho o link** — campo livre, validado como URL.
- **Tenho o telefone** — campo de telefone; o frontend monta `https://wa.me/<ddd+número>` antes
  de enviar (normalização: remove não-dígitos, prefixa `55` se não iniciar com código de país).

Essa normalização acontece no componente React (`buildWhatsAppUrl(phone: string): string`),
nunca no backend. O backend armazena e devolve a URL final; não conhece "telefone para wa.me".

### 3.2 Associação ao morador-dono (`owner_unit_id`)

#### Problema a resolver

Quando `is_resident=true`, a indicação está dizendo "esse profissional é um morador". Até hoje
não há vínculo de unidade na indicação — exibir "Ap 42B" requer uma consulta extra ao `user`.
Além disso, quando o **próprio morador** cadastra, ele não deve precisar informar o UUID de si
mesmo nem de sua unidade (ambos já estão no JWT).

#### Proposta de modelo

Adicionar à tabela `recommendation`:

| Coluna | Tipo | Notas |
|---|---|---|
| `owner_unit_id` | uuid FK unit | nullable; unidade do morador-dono |
| `owner_unit_code` | varchar(10) | snapshot do código (ex.: "42B") |

`owner_unit_id` é populado pelo serviço; nunca vem diretamente do cliente.
`owner_unit_code` é um snapshot desnormalizado para exibição sem JOIN, inspirado no padrão
usado em `recommendation.resident_user_id` (que já carrega o UUID sem materializar a entidade).

> **Decisão de design alternativa**: em vez de `owner_unit_code`, o service pode buscar o código
> da unidade ao montar `RecommendationView`. Preferência pelo snapshot para evitar JOIN em listas
> paginadas; fica como pergunta em aberto (seção 7).

#### Semântica da flag `is_resident` + novo campo

| Cenário | `is_resident` | `resident_user_id` | `owner_unit_id` |
|---|---|---|---|
| Indicação externa (empresa, autônomo externo) | false | null | null |
| Admin/conselho indica morador como prestador | true | UUID do morador | derivado do User pelo service |
| Próprio morador cadastra como "sou eu" | true | UUID do próprio usuário (vem do JWT) | derivado do JWT (`unitId`) |

O **backend** resolve `owner_unit_id` em ambos os casos de morador:
- Se o author for o próprio resident (`resident_user_id == authorId`): usa `principal.unitId()`.
- Se admin indicar outro morador: service busca `User.unitId` do `resident_user_id`.

O **frontend** não precisa mais enviar `residentUserId` quando o morador cadastra a si próprio
(novo modo "sou eu"). O backend detecta isso e preenche `resident_user_id = authorId`.

#### Mudança de CHECK no banco

O CHECK atual:

```sql
CONSTRAINT chk_reco_resident CHECK (is_resident = false OR resident_user_id IS NOT NULL)
```

Permanece igual — `owner_unit_id` pode ser null se o morador não tiver unidade cadastrada
(edge case: usuário staff, conta sem unidade). Não criar novo CHECK para `owner_unit_id` pois
um morador válido sem unidade ainda pode ter `is_resident=true`.

---

## 4. Mudanças por camada

### 4.1 Banco — migration `V22__recommendations_social_unit.sql`

Expand puro (apenas ADD COLUMN), backward-compatible:

```sql
-- flyway:transactional=true

ALTER TABLE recommendation
    ADD COLUMN instagram_url   varchar(255),
    ADD COLUMN facebook_url    varchar(255),
    ADD COLUMN whatsapp_url    varchar(255),
    ADD COLUMN catalog_url     varchar(500),
    ADD COLUMN owner_unit_id   uuid REFERENCES unit (id) ON DELETE RESTRICT,
    ADD COLUMN owner_unit_code varchar(10);
```

Sem rename, sem remove, sem DEFAULT NOT NULL (segue expand/contract do CLAUDE.md).

### 4.2 Backend — entidade `Recommendation`

Novos campos no `Recommendation.java`:

```java
@Column(name = "instagram_url", length = 255)
private String instagramUrl;

@Column(name = "facebook_url", length = 255)
private String facebookUrl;

@Column(name = "whatsapp_url", length = 255)
private String whatsappUrl;

@Column(name = "catalog_url", length = 500)
private String catalogUrl;

@Column(name = "owner_unit_id")
private UUID ownerUnitId;

@Column(name = "owner_unit_code", length = 10)
private String ownerUnitCode;
```

Métodos de domínio a atualizar:

- `create(...)` — receber os novos campos + `ownerUnitId` + `ownerUnitCode` (já resolvidos pelo service).
- `edit(...)` — aceitar os novos campos de link; **não** alterar `ownerUnitId`/`ownerUnitCode`
  (imutáveis após criação, assim como `recommendedByUserId`).

### 4.3 Backend — DTOs

#### `CreateRecommendationRequest`

```java
public record CreateRecommendationRequest(
    @NotBlank @Size(max = 120) String serviceName,
    @Size(max = 120) String professionalName,
    @Size(max = 20) String phone,
    // Novos:
    @URL @Size(max = 255) String instagramUrl,
    @URL @Size(max = 255) String facebookUrl,
    @URL @Size(max = 255) String whatsappUrl,
    @URL @Size(max = 500) String catalogUrl,
    // "sou eu" = true sem residentUserId; admin = true + residentUserId
    boolean isResident,
    UUID residentUserId,   // null quando morador cadastra a si próprio
    @Size(max = 255) String addressLine,
    @Size(max = 40) String priceRange,
    @Min(1) @Max(5) Integer rating,
    String comment,
    List<String> tagSlugs,
    List<OpeningHoursDto> openingHours) {}
```

A regra de negócio `is_resident=true + residentUserId=null` agora é válida (significa "sou eu").
A validação do CHECK antigo (`is_resident=true => residentUserId!=null`) saiu do DTO e vai para
o service, que resolve o UUID do próprio autor quando necessário.

#### `UpdateRecommendationRequest`

Adicionar os quatro campos de link (mesmas anotações). `isResident`, `residentUserId`,
`ownerUnitId` e `ownerUnitCode` **não** fazem parte do update (imutáveis).

#### `RecommendationView`

Adicionar ao record:
```java
String instagramUrl,
String facebookUrl,
String whatsappUrl,
String catalogUrl,
UUID ownerUnitId,
String ownerUnitCode,
```

### 4.4 Backend — `RecommendationService`

```java
@Transactional
public RecommendationView create(UUID authorId, CreateRecommendationRequest req,
                                  AuthenticatedUserPrincipal principal) {
    UUID resolvedResidentId = req.residentUserId();
    UUID resolvedUnitId     = null;
    String resolvedUnitCode = null;

    if (req.isResident()) {
        if (resolvedResidentId == null) {
            // Morador cadastrando a si próprio: pega do JWT
            resolvedResidentId = authorId;
        }
        // Resolve unidade
        if (resolvedResidentId.equals(authorId)) {
            resolvedUnitId = principal.unitId();
            if (resolvedUnitId != null) {
                resolvedUnitCode = unitRepository.findCodeById(resolvedUnitId).orElse(null);
            }
        } else {
            // Admin indicando outro morador: busca no banco
            resolvedUnitId = userRepository.findUnitIdById(resolvedResidentId).orElse(null);
            if (resolvedUnitId != null) {
                resolvedUnitCode = unitRepository.findCodeById(resolvedUnitId).orElse(null);
            }
        }
    }

    Recommendation r = Recommendation.create(
        authorId, req.serviceName(), req.professionalName(), req.phone(),
        req.instagramUrl(), req.facebookUrl(), req.whatsappUrl(), req.catalogUrl(),
        req.isResident(), resolvedResidentId,
        resolvedUnitId, resolvedUnitCode,
        req.addressLine(), req.priceRange(), req.rating(), req.comment());
    // ... resto igual
}
```

`principal` precisa ser adicionado como parâmetro; o controller já tem acesso via
`@AuthenticationPrincipal` e passa para o service (padrão já estabelecido em `getById`).

Se `unitRepository.findCodeById` for caro, pode-se fazer uma query única com JOIN. A escolha
de como buscar o `unit_code` fica em aberto (seção 7).

### 4.5 Backend — `RecommendationController`

Apenas repassar o `principal` ao `service.create`:

```java
@PostMapping
@PreAuthorize("isAuthenticated()")
public ResponseEntity<RecommendationView> create(
    @Valid @RequestBody CreateRecommendationRequest body,
    @AuthenticationPrincipal AuthenticatedUserPrincipal me) {
  return ResponseEntity.status(HttpStatus.CREATED).body(service.create(me.userId(), body, me));
}
```

### 4.6 Frontend — `recommendationsApi.ts`

Adicionar ao tipo `Recommendation`:
```ts
instagramUrl: string | null;
facebookUrl: string | null;
whatsappUrl: string | null;
catalogUrl: string | null;
ownerUnitId: string | null;
ownerUnitCode: string | null;
```

Adicionar ao `RecommendationBody`:
```ts
instagramUrl?: string;
facebookUrl?: string;
whatsappUrl?: string;
catalogUrl?: string;
// isResident permanece; residentUserId agora é opcional também para o morador
residentUserId?: string | null;
```

### 4.7 Frontend — `RecommendationFormPage.tsx`

#### Novos campos de link (ambos os modos: criação e edição)

Após o campo `phone`, adicionar quatro inputs opcionais:

```
Telefone (opcional) ← já existe

Instagram (opcional)   [ https://instagram.com/...         ]
Facebook (opcional)    [ https://facebook.com/...          ]
WhatsApp (opcional)    [ ○ Link pronto  ● Informar telefone ]
                         Telefone: [          ] → monta wa.me
Catálogo/cardápio (opcional) [ https://...                 ]
```

Validação client-side: testar `URL()` constructor antes de enviar. Exibir mensagem inline se
formato inválido (não bloquear submissão se campo vazio).

#### Seção "É morador do condomínio?" — novo comportamento por perfil

**Regra de exibição (baseada em `user.authorities` do `useAuth()`):**

| Quem está logado | O que vê |
|---|---|
| Usuário com `unitId != null` (morador com unidade) | Checkbox "Este sou eu (moro aqui)" — sem campo de UUID |
| Admin / conselho (`RECOMMENDATION_MODERATE` ou `USER_MANAGE`) | Checkbox "É morador do condomínio?" + campo de busca/UUID do morador |
| Usuário sem unidade (staff sem unidade) | Checkbox "É morador do condomínio?" + campo de UUID |

Quando o morador marca "Este sou eu":
- Envia `isResident: true`, `residentUserId: null` (ou omitido).
- O backend preenche `residentUserId` com `authorId` e `ownerUnitId`/`ownerUnitCode` via JWT.

Quando admin seleciona outro morador:
- Envia `isResident: true`, `residentUserId: <UUID do morador>`.
- O backend resolve a unidade do morador a partir do banco.

> **Campo de busca de morador (admin):** idealmente um autocomplete que busca usuários com
> unidade — depende de um endpoint de busca de usuários já existente
> (`GET /api/users?search=…` do sub-projeto gestao-usuarios-v2). Alternativamente, manter o
> campo de UUID texto por ora. Fica como decisão de UX (seção 7).

#### `RecommendationDetailPage.tsx` — exibição dos novos campos

Na seção `<dl>` de detalhes, após "Telefone:", adicionar links clicáveis:

```tsx
{rec.instagramUrl && (
  <div className="flex gap-2">
    <dt className="font-medium">Instagram:</dt>
    <dd><a href={rec.instagramUrl} target="_blank" rel="noopener noreferrer"
           className="text-primary underline underline-offset-2">
      Ver perfil
    </a></dd>
  </div>
)}
{rec.facebookUrl && (
  /* análogo */
)}
{rec.whatsappUrl && (
  <div className="flex gap-2">
    <dt className="font-medium">WhatsApp:</dt>
    <dd><a href={rec.whatsappUrl} target="_blank" rel="noopener noreferrer"
           className="text-primary underline underline-offset-2">
      Chamar no WhatsApp
    </a></dd>
  </div>
)}
{rec.catalogUrl && (
  <div className="flex gap-2">
    <dt className="font-medium">Catálogo / cardápio:</dt>
    <dd><a href={rec.catalogUrl} target="_blank" rel="noopener noreferrer"
           className="text-primary underline underline-offset-2">
      Ver catálogo
    </a></dd>
  </div>
)}
```

O badge "Mora aqui" já existe. Adicionar ao lado ou abaixo a unidade quando `ownerUnitCode`
estiver disponível:

```tsx
{rec.isResident && rec.ownerUnitCode && (
  <span className="text-xs text-muted-foreground">Ap {rec.ownerUnitCode}</span>
)}
```

---

## 5. Testes

### TDD — backend

Em `RecommendationServiceTest`:

- `create_withSocialLinks_persistsAllFields` — verifica que os quatro campos de link são salvos.
- `create_residentSelf_derivesResidentIdAndUnitFromPrincipal` — morador sem `residentUserId`
  no request; service preenche com `authorId` e `unitId` do principal.
- `create_residentByAdmin_derivesUnitFromUserRepo` — admin passa `residentUserId` de outro
  morador; service consulta `userRepository.findUnitIdById`.
- `create_residentWithNoUnit_ownerUnitIdIsNull` — morador sem unidade; aceito sem erro.
- `update_doesNotChangeOwnerUnit` — edição não altera `ownerUnitId` nem `residentUserId`.

Em `RecommendationControllerWebTest`:

- POST com links válidos → 201.
- POST com URL malformada em `instagramUrl` → 400.
- POST `isResident=true, residentUserId=null` por morador → 201, body contém `ownerUnitCode`.
- POST `isResident=true, residentUserId=<outro UUID>` sem `RECOMMENDATION_MODERATE` → 403
  (ver seção 7: quem pode indicar outro morador?).

### TDD — frontend

Em `RecommendationFormPage.test.tsx`:

- Morador com `unitId`: renderiza "Este sou eu" sem campo de UUID.
- Admin: renderiza campo de UUID/busca de morador.
- Submissão com telefone de WhatsApp: `residentUserId` ausente e `whatsappUrl` montada.
- URL inválida em Instagram exibe mensagem de erro inline.

Em `RecommendationDetailPage.test.tsx`:

- Links renderizados como `<a>` com `target="_blank" rel="noopener noreferrer"`.
- `ownerUnitCode` exibido junto ao badge "Mora aqui".
- Campos nulos não renderizam nada.

---

## 6. Fora de escopo

- Validação de existência real do handle do Instagram/Facebook (não consultamos a API deles).
- Verificação de que `whatsappUrl` é realmente um número ativo.
- Moderação automática de links externos (ex.: lista de domínios proibidos).
- Histórico de edições dos links.
- Tornar qualquer campo de link obrigatório.
- Mudar o design de fotos, tags ou horários de funcionamento.

---

## 7. Perguntas em aberto

1. **WhatsApp: link pronto vs. telefone?**
   O spec propõe os dois modos no frontend (link pronto ou telefone que vira `wa.me`), mas
   armazena sempre a URL final. É isso mesmo, ou Paulo prefere armazenar o número bruto e montar
   a URL apenas na exibição? Armazenar a URL é mais simples e genérico.

2. **Todos os links são opcionais?**
   Sim, conforme entendimento. Confirmar se há algum cenário em que pelo menos um link deveria
   ser obrigatório (ex.: catálogo obrigatório para comércio, WhatsApp obrigatório para
   prestadores).

3. **Quem pode marcar "de morador" e indicar outro morador?**
   Qualquer usuário pode criar `isResident=true` com `residentUserId` de outra pessoa? Ou só
   admin/conselho (`RECOMMENDATION_MODERATE`) pode fazer isso, enquanto moradores só podem
   marcar a si próprios?
   Impacto: se restrito, o controller precisa de um guard adicional antes de chamar o service.

4. **A unidade aparece publicamente na indicação?**
   O spec propõe exibir `ownerUnitCode` ("Ap 42B") na tela de detalhe. Isso é intencional para
   todos os usuários autenticados? Ou só para admin/conselho? Considerar LGPD: localização de
   morador pode ser dado sensível.

5. **Snapshot de `owner_unit_code` vs. JOIN dinâmico?**
   Persistir o código da unidade como snapshot (varchar desnormalizado) é mais simples para
   listas paginadas mas requer atualização se o código da unidade mudar (raro). Ou preferível
   fazer JOIN ao montar a view? Dado que unidades raramente mudam de código, snapshot parece
   razoável, mas merece confirmação.

6. **Normalização e validação do `@` do Instagram?**
   O usuário pode digitar `@meuhandle` (sem URL) ou `instagram.com/meuhandle` (sem esquema)?
   O spec atual rejeita ambos (exige `https://`). Preferível: o frontend normaliza o handle para
   `https://instagram.com/meuhandle` automaticamente, aceitando entrada no formato `@handle` ou
   URL completa?

7. **Busca de morador no formulário admin?**
   O campo de `residentUserId` atualmente é um input de UUID bruto. Faz sentido conectar ao
   endpoint `GET /api/users?search=…` (já existente no sub-projeto gestao-usuarios-v2) para
   exibir autocomplete com nome + unidade? Ou manter UUID por ora e deixar para o próximo ciclo?

8. **Tamanho máximo de `catalog_url` (500)?**
   Links do iFood podem ser curtos, mas links de Google Drive/Notion podem ser longos. 500
   caracteres cobre os casos conhecidos? Ou preferir 1000?
