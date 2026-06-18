import type { ReactNode } from "react";

type Props = {
  title: string;
  subtitle?: string;
  children: ReactNode;
};

export function AuthCard({ title, subtitle, children }: Props) {
  return (
    <div className="min-h-full flex items-center justify-center p-6">
      <div className="card w-full max-w-md p-6 space-y-4">
        <div className="text-center space-y-1">
          <h1 className="text-2xl font-bold text-heading">{title}</h1>
          {subtitle ? (
            <p className="text-sm text-muted">{subtitle}</p>
          ) : null}
        </div>
        {children}
      </div>
    </div>
  );
}
