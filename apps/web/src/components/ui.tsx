import type { GroupColor } from "@split/core";
import { X } from "lucide-react";
import { useEffect, useRef, type ButtonHTMLAttributes, type FormEvent, type ReactNode } from "react";
import { initials, money, signedMoney } from "../lib/format";

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

/** A group's rounded tile with its initials. */
export function Tile({ name, color, size }: { name: string; color: GroupColor; size?: "small" | "large" }) {
  return (
    <span className={`tile${size ? ` tile--${size}` : ""}`} data-tint={color} aria-hidden="true">
      {initials(name)}
    </span>
  );
}

/** A person's circle with their initials. */
export function Avatar({ name, color, small }: { name: string; color: GroupColor; small?: boolean }) {
  return (
    <span className={`avatar${small ? " avatar--small" : ""}`} data-tint={color} aria-hidden="true">
      {initials(name)}
    </span>
  );
}

/** A balance: plus and green when money comes back, minus in ink when owed, grey at zero. */
export function Balance({ cents, currency, zero = "quitte" }: { cents: number; currency?: string; zero?: string }) {
  if (cents === 0) return <span className="amount amount--zero">{zero}</span>;
  return <span className={`amount${cents > 0 ? " amount--positive" : ""}`}>{signedMoney(cents, currency)}</span>;
}

export const Money = ({ cents, currency }: { cents: number; currency?: string }) => (
  <span className="amount">{money(cents, currency)}</span>
);

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

/** Placeholder rows while a list loads. */
export function SkeletonRows({ count = 3 }: { count?: number }) {
  return (
    <ul className="rows" aria-busy="true" aria-label="Laden">
      {Array.from({ length: count }, (_, index) => (
        <li key={index} className="row">
          <span className="skeleton" style={{ width: 44, height: 44, borderRadius: 12 }} />
          <span className="row__main">
            <span className="skeleton" style={{ display: "block", width: "45%", height: 14 }} />
            <span className="skeleton" style={{ display: "block", width: "30%", height: 12, marginTop: 8 }} />
          </span>
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
