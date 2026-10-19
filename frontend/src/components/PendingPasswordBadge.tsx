/** Selo de quem foi cadastrado por terceiros e ainda não criou a senha pelo link do WhatsApp. */
export function PendingPasswordBadge() {
  return (
    <span className="ml-2 rounded-full bg-amber-100 px-2 py-0.5 text-xs font-normal text-amber-900">
      Aguardando criar senha
    </span>
  );
}
