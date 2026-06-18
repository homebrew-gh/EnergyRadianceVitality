import { type InputHTMLAttributes, useState } from "react";

type Props = InputHTMLAttributes<HTMLInputElement>;

export function SecretInput(props: Props) {
  const [visible, setVisible] = useState(false);
  return (
    <div className="relative">
      <input
        {...props}
        type={visible ? "text" : "password"}
        className={`input pr-16 ${props.className ?? ""}`}
      />
      <button
        type="button"
        className="absolute right-2 top-1/2 -translate-y-1/2 text-xs text-muted"
        onClick={() => setVisible((v) => !v)}
        tabIndex={-1}
      >
        {visible ? "Hide" : "Show"}
      </button>
    </div>
  );
}
