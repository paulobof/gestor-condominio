# Crédito da desenvolvedora (Wizortech) — Design

> Data: 2026-06-14 · Status: aprovado

## Objetivo

Exibir o crédito "Desenvolvido por Wizortech" de forma discreta no app, presente mas
sem competir com a identidade visual do HELBOR TRILOGY HOME, linkando para site,
e-mail e WhatsApp da desenvolvedora.

## Componente — `DeveloperCredit`

- Arquivo: `frontend/src/components/branding/DeveloperCredit.tsx`.
- Renderiza o texto `Desenvolvido por Wizortech` + 3 ações de contato como ícones
  pequenos do `lucide-react`: site (`Globe`), e-mail (`Mail`), WhatsApp (`MessageCircle`).
- Cada ação tem `aria-label` e `title`; alvos de toque ≥44px; cor `text-muted-foreground`
  (discreto), contraste WCAG AA.
- Links externos com `target="_blank" rel="noopener noreferrer"`; e-mail via `mailto:`.
- Constantes centralizadas num objeto `WIZORTECH` no topo do arquivo (único lugar de
  manutenção dos dados de contato).
- Aceita `className` opcional para ajustes de espaçamento por contexto.

## Dados de contato

- Site: `https://wizortech.com.br/`
- E-mail: `contato@wizortech.com.br`
- WhatsApp: `https://api.whatsapp.com/send/?phone=551145801261&text&type=phone_number&app_absent=0`

## Pontos de uso

1. **Login** (`LoginPage.tsx`): abaixo do `Card`, centralizado, dentro do `<main>`.
2. **Footer global** (`Shell.tsx`): um `<footer>` discreto no rodapé das telas
   autenticadas, largura total, abaixo do conteúdo.

## Testes (TDD)

- `DeveloperCredit.test.tsx`: verifica o texto "Wizortech", os 3 links com `href`
  corretos e `rel="noopener noreferrer"` nos links externos.

## Fora de escopo (YAGNI)

- Logo/imagem da Wizortech.
- Página "Sobre" dedicada.
- i18n / configuração via env.
