# Design — Senha/UX (mostrar senha + critérios de segurança)

**Data:** 2026-06-08
**Feature flag:** não (mudança de UX/validação em telas existentes, sem WIP em produção)
**Branch:** `feat/senha-ux`

## Problema

1. Nenhuma tela de senha (login, cadastro, reset) tem opção de **mostrar a senha** — usuário digita às cegas, gera erro e frustração (pior no mobile).
2. O **cadastro** (`RegisterMasterPage`) valida só `length >= 8`, sem critérios fortes — diferente do reset, que já exige maiúscula/minúscula/número/especial.
3. Os critérios fortes hoje vivem **só no frontend** (zod do `ResetPasswordPage`). O backend (`PasswordResetService.consumeReset` e `RegisterMasterRequest`) só exige `length >= 8` — dá para burlar a política batendo direto na API.

## Objetivo

- Olho "mostrar/ocultar senha" nas 3 telas.
- Critérios fortes no cadastro, com **checklist ao vivo** (regras acendem conforme digita), espelhando o reset.
- Política forte validada **também no backend** (cadastro + reset), fechando a brecha de bypass via API.

## Não-objetivos (YAGNI)

- Critério de força no **login** (não se cria senha lá; só autentica) — login recebe apenas o olho.
- Campo "confirmar senha" no cadastro (o reset já tem; cadastro fica para depois, se desejado).
- Refatorar o `RegisterMasterPage` para `react-hook-form` (mantém `useState` para reduzir risco/escopo).
- Medidor de força (barra/score) — só o checklist booleano de regras.

## Política de senha (fonte única)

Mesmas regras nos dois lados:

- Mínimo **8** caracteres (máx. 128).
- Pelo menos **uma letra maiúscula** (`[A-Z]`).
- Pelo menos **uma letra minúscula** (`[a-z]`).
- Pelo menos **um número** (`[0-9]`).
- Pelo menos **um caractere especial** (`[^A-Za-z0-9]`).

## Frontend

Stack existente: React + shadcn/ui + Tailwind + `react-hook-form`/`zod` (login e reset) + `useState` (cadastro).

1. **`components/ui/password-input.tsx` — `PasswordInput`**
   - Encapsula o `Input` shadcn com um botão de olho (`Eye`/`EyeOff` do `lucide-react`) posicionado à direita.
   - Alterna `type` entre `password` e `text`; estado local `visible`.
   - Acessibilidade: `aria-label` dinâmico ("Mostrar senha"/"Ocultar senha"), `type="button"` (não submete), alvo de toque `≥44px`, foco visível.
   - Repassa `ref` e props (`...props`) para compor com `register(...)` do RHF e com `value/onChange` controlado do cadastro.
   - Substitui os `<Input type="password">` em login, cadastro e reset.

2. **`features/auth/passwordPolicy.ts` — fonte única**
   - Exporta `passwordSchema` (zod) com as regras acima (move o schema hoje duplicado no `ResetPasswordPage`).
   - Exporta `passwordRules: { id: string; label: string; test: (v: string) => boolean }[]` para o checklist.
   - Exporta `isStrongPassword(v: string): boolean` (todas as regras) para o `canSubmit` do cadastro.

3. **`components/auth/PasswordChecklist.tsx` — `PasswordChecklist`**
   - Recebe o valor atual; renderiza cada regra de `passwordRules` com ícone de check verde (atende) ou neutro/muted (pendente).
   - `aria-live="polite"` para leitores de tela; cada item com texto, não só cor (WCAG AA).
   - Usado em **cadastro** e **reset**.

4. **Telas**
   - `LoginPage`: troca o input de senha por `PasswordInput`. Sem checklist.
   - `ResetPasswordPage`: usa `PasswordInput` nos dois campos; importa `passwordSchema` de `passwordPolicy.ts` (remove a duplicata); adiciona `PasswordChecklist` sob "Nova senha".
   - `RegisterMasterPage`: usa `PasswordInput`; adiciona `PasswordChecklist`; `canSubmit` passa a exigir `isStrongPassword(password)`; label deixa de dizer só "mínimo 8 caracteres".

## Backend

Stack: Spring Boot + Jakarta Bean Validation.

5. **`@StrongPassword` — constraint customizada**
   - Nova anotação `@StrongPassword` + `StrongPasswordValidator` (em `shared/validation`), com as mesmas regras (regex) da política.
   - Mensagem padrão: "Senha não atende à política mínima."
   - Aplicada em:
     - `RegisterMasterRequest.password` (substitui `@Size(min = 8)`; mantém `@NotBlank`).
     - `ConsumeResetRequest.newPassword` (substitui validação fraca).
     - `CreateUnitMemberRequest.password` (síndico cadastrando morador de unidade — terceiro ponto de criação de senha, descoberto na revisão final; mesma correção, mantém `@NotBlank`).
   - Validação no boundary do controller → resposta **400** com erro de campo (via `GlobalExceptionHandler` existente).

6. **`PasswordResetService.consumeReset`**
   - Remove o check manual `newPassword.length() < 8` (agora redundante — coberto por `@StrongPassword` no DTO). Mantém o restante (anti-reuso etc.).

## Tratamento de erro

- Front: mensagens de campo do zod (cadastro/reset) + checklist visual. Login mantém o toast genérico atual.
- Back: violação de `@StrongPassword` → 400 com `field`/`message`, já mapeado pelo `GlobalExceptionHandler`. Reset mantém os códigos `INVALID_OR_EXPIRED_TOKEN` / `PASSWORD_REUSED`.

## Testes (TDD — testes primeiro)

**Backend**
- `StrongPasswordValidatorTest` (unit): aceita senha forte; rejeita cada regra individualmente (curta, sem maiúscula, sem minúscula, sem número, sem especial).
- `RegisterMasterControllerWebTest`: senha fraca → 400 (ajustar o teste atual que usa `senha12345`, que falharia na nova regra — passar a usar senha forte no caso de sucesso e adicionar caso de senha fraca → 400).
- `PasswordResetControllerWebTest`: `consume-reset` com senha fraca → 400.

**Frontend**
- `PasswordInput`: clicar no olho alterna `type` password↔text e o `aria-label`.
- `PasswordChecklist`: regras acendem/apagam conforme o valor.
- `RegisterMasterPage`: botão "Enviar cadastro" desabilitado com senha fraca; habilitado quando todas as regras passam (com os demais campos válidos).

## Arquivos afetados (estimativa)

Novos: `password-input.tsx`, `passwordPolicy.ts`, `PasswordChecklist.tsx`, `StrongPassword.java`, `StrongPasswordValidator.java` (+ testes).
Editados: `LoginPage.tsx`, `ResetPasswordPage.tsx`, `RegisterMasterPage.tsx`, `RegisterMasterRequest.java`, `ConsumeResetRequest.java`, `PasswordResetService.java`, testes existentes de registro/reset.

PR coeso, dentro do limite de ~400 linhas.
