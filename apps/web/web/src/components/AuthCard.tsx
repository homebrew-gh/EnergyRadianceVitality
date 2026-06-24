import type { ReactNode } from "react";
import { ErvLogo } from "./ErvLogo";

type Props = {
  title: string;
  subtitle?: string;
  showLogo?: boolean;
  children: ReactNode;
};

export function AuthCard({ title, subtitle, showLogo = true, children }: Props) {
  return (
    <div className="min-h-full flex items-center justify-center p-6">
      <div className="card w-full max-w-md p-6 space-y-4">
        {showLogo ? (
          <div className="flex justify-center">
            <ErvLogo variant="onLight" size="lg" showWordmark />
          </div>
        ) : null}
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
