import type { GroupColor, Member } from "@split/core";
import { X } from "lucide-react";
import { useEffect, useRef, type ButtonHTMLAttributes, type FormEvent, type ReactNode } from "react";
import { memberColor, signedMoney, signedPlain } from "../lib/format";

type ButtonVariant = "primary" | "secondary" | "ghost" | "danger";

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  small?: boolean;
  block?: boolean;
  icon?: boolean;
}

export function Button({ variant = "secondary", small, block, icon, className = "", type = "button", ...rest }: ButtonProps) {
  const classes = [
    "button",
    variant !== "secondary" && `button--${variant}`,
    small && "button--small",
    block && "button--block",
    icon && "button--icon",
    className,
  ]
    .filter(Boolean)
    .join(" ");
  return <button type={type} className={classes} {...rest} />;
}

/** A person: their name with their highlighter swiped behind it. */
export function Mark({ color, children }: { color: GroupColor; children: ReactNode }) {
  return (
    <span className="mark" data-hl={color}>
      {children}
    </span>
  );
}

/** A group's name, underlined with the group's highlighter. */
export function GroupName({ name, color }: { name: string; color: GroupColor }) {
  return (
    <span className="underline-mark" data-hl={color}>
      {name}
    </span>
  );
}

/** The dotted line between a name and its amount on a receipt. */
export const Leader = () => <span className="leader" aria-hidden="true" />;

/**
 * A team member or the team pot, by id: a person is their name on their
 * highlighter, the pot (not a person) a label printed in ink.
 */
export function Person({ members, memberId }: { members: Member[]; memberId: string }) {
  const member = members.find((m) => m.id === memberId);
  if (!member) return <span>Onbekend</span>;
  if (member.kind === "pot") return <span className="pot-label">{member.name}</span>;
  return <Mark color={memberColor(members, memberId)}>{member.name}</Mark>;
}

/** A rubber stamp: red on payments and fines, ink on contributions. */
export const Stamp = ({ children, ink }: { children: ReactNode; ink?: boolean }) => (
  <span className={`stamp${ink ? " stamp--ink" : ""}`}>{children}</span>
);

/** "Split", torn in two along a perforation, with the stamp "SPORT". */
export function Wordmark({ size }: { size?: "small" | "large" }) {
  return (
    <span className={`wordmark${size ? ` wordmark--${size}` : ""}`} role="img" aria-label="Split Sport">
      <span aria-hidden="true">Sp</span>
      <span className="wordmark__cut" aria-hidden="true" />
      <span aria-hidden="true">lit</span>
      <span className="wordmark__sport" aria-hidden="true">
        Sport
      </span>
    </span>
  );
}

/** A balance as a till prints it: "+ 24,95", "− 21,60", and "0,00" in muted ink. */
export function Balance({ cents, euro }: { cents: number; euro?: boolean }) {
  const text = euro ? signedMoney(cents) : signedPlain(cents);
  return <span className={`amount${cents === 0 ? " amount--zero" : ""}`}>{text}</span>;
}

export function Field({ label, hint, children }: { label: string; hint?: string; children: ReactNode }) {
  return (
    <label className="field">
      <span className="field__label">{label}</span>
      {children}
      {hint && <span className="field__hint">{hint}</span>}
    </label>
  );
}

export function Segmented<T extends string>({
  value,
  options,
  onChange,
  label,
}: {
  value: T;
  options: { value: T; label: string }[];
  onChange: (value: T) => void;
  label: string;
}) {
  return (
    <div className="segmented" role="group" aria-label={label}>
      {options.map((option) => (
        <button
          key={option.value}
          type="button"
          className="segmented__option"
          aria-pressed={option.value === value}
          onClick={() => onChange(option.value)}
        >
          {option.label}
        </button>
      ))}
    </div>
  );
}

export const ErrorBanner = ({ message }: { message: string | null | undefined }) =>
  message ? (
    <p className="banner" role="alert">
      {message}
    </p>
  ) : null;

export function Empty({ title, text, children }: { title: string; text?: string; children?: ReactNode }) {
  return (
    <div className="empty">
      <p className="empty__title">{title}</p>
      {text && <p className="empty__text">{text}</p>}
      {children && <div className="empty__actions">{children}</div>}
    </div>
  );
}

/** Placeholder lines while a receipt loads. */
export function SkeletonRows({ count = 3 }: { count?: number }) {
  return (
    <ul className="rows" style={{ paddingTop: 12 }} aria-busy="true" aria-label="Laden">
      {Array.from({ length: count }, (_, index) => (
        <li key={index} className="row">
          <span className="skeleton" style={{ width: `${70 - index * 12}%` }} />
          <span className="skeleton" style={{ width: "32%", marginTop: 10 }} />
        </li>
      ))}
    </ul>
  );
}

/**
 * A modal built on the native <dialog>: focus trapping, Escape and the backdrop
 * come from the browser. The content is a form; `onSubmit` is optional.
 */
export function Dialog({
  title,
  onClose,
  onSubmit,
  footer,
  narrow,
  children,
}: {
  title: string;
  onClose: () => void;
  onSubmit?: () => void;
  footer?: ReactNode;
  narrow?: boolean;
  children: ReactNode;
}) {
  const ref = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    const dialog = ref.current;
    if (dialog && !dialog.open) dialog.showModal();
  }, []);

  const submit = (event: FormEvent) => {
    event.preventDefault();
    onSubmit?.();
  };

  return (
    <dialog
      ref={ref}
      className={`dialog${narrow ? " dialog--narrow" : ""}`}
      aria-label={title}
      onCancel={(event) => {
        event.preventDefault();
        onClose();
      }}
      onClick={(event) => {
        if (event.target === ref.current) onClose();
      }}
    >
      <form className="dialog__form" onSubmit={submit} noValidate>
        <header className="dialog__header">
          <h2>{title}</h2>
          <Button variant="ghost" icon aria-label="Sluiten" onClick={onClose}>
            <X size={18} />
          </Button>
        </header>
        <div className="dialog__body">{children}</div>
        {footer && <footer className="dialog__footer">{footer}</footer>}
      </form>
    </dialog>
  );
}

export function ConfirmDialog({
  title,
  text,
  confirmLabel,
  busy,
  error,
  onConfirm,
  onClose,
}: {
  title: string;
  text: string;
  confirmLabel: string;
  busy?: boolean;
  error?: string | null;
  onConfirm: () => void;
  onClose: () => void;
}) {
  return (
    <Dialog
      title={title}
      narrow
      onClose={onClose}
      onSubmit={onConfirm}
      footer={
        <>
          <span className="spacer" />
          <Button onClick={onClose}>Annuleren</Button>
          <Button variant="danger" type="submit" disabled={busy}>
            {confirmLabel}
          </Button>
        </>
      }
    >
      <p className="muted">{text}</p>
      {error && (
        <div style={{ marginTop: 12 }}>
          <ErrorBanner message={error} />
        </div>
      )}
    </Dialog>
  );
}
