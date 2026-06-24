import { useId } from "react";

type ErvLogoProps = {
  /** Header bar on red chrome; auth cards on light surfaces. */
  variant?: "onDark" | "onLight";
  showWordmark?: boolean;
  showTagline?: boolean;
  size?: "sm" | "md" | "lg";
  className?: string;
};

const sizeMap = {
  sm: { mark: 28, word: "text-base", tag: "text-[10px]" },
  md: { mark: 36, word: "text-lg", tag: "text-xs" },
  lg: { mark: 48, word: "text-2xl", tag: "text-sm" },
} as const;

/** Eight evenly spaced rays — matches ic_launcher_foreground / package icon geometry. */
const SUN_RAYS = Array.from({ length: 8 }, (_, index) => {
  const angle = (index * Math.PI) / 4;
  const cos = Math.cos(angle);
  const sin = Math.sin(angle);
  return {
    x1: 32 + 26 * cos,
    y1: 32 + 26 * sin,
    x2: 32 + 19.8 * cos,
    y2: 32 + 19.8 * sin,
  };
});

export function ErvSunMark({
  size = 36,
  variant = "onDark",
  className,
}: {
  size?: number;
  variant?: "onDark" | "onLight";
  className?: string;
}) {
  const gradientId = useId();
  const ray = variant === "onDark" ? "#ffd600" : "#c62828";
  const sunInner = "#fff9c4";
  const sunMid = "#ffd600";
  const sunOuter = variant === "onDark" ? "#ffab00" : "#ff8f00";

  return (
    <svg
      viewBox="0 0 64 64"
      width={size}
      height={size}
      className={className}
      aria-hidden
    >
      <g fill="none" stroke={ray} strokeLinecap="round" strokeWidth="4" opacity="0.92">
        {SUN_RAYS.map((rayLine, index) => (
          <line
            key={index}
            x1={rayLine.x1.toFixed(2)}
            y1={rayLine.y1.toFixed(2)}
            x2={rayLine.x2.toFixed(2)}
            y2={rayLine.y2.toFixed(2)}
          />
        ))}
      </g>
      <circle cx="32" cy="32" r="14" fill={`url(#${gradientId})`} />
      <defs>
        <radialGradient id={gradientId} cx="30" cy="30" r="14">
          <stop offset="0%" stopColor={sunInner} />
          <stop offset="55%" stopColor={sunMid} />
          <stop offset="100%" stopColor={sunOuter} />
        </radialGradient>
      </defs>
    </svg>
  );
}

export function ErvLogo({
  variant = "onDark",
  showWordmark = true,
  showTagline = false,
  size = "md",
  className = "",
}: ErvLogoProps) {
  const tokens = sizeMap[size];
  const wordClass =
    variant === "onDark"
      ? `${tokens.word} font-bold tracking-tight text-white`
      : `${tokens.word} font-bold tracking-tight text-heading`;
  const tagClass =
    variant === "onDark"
      ? `${tokens.tag} opacity-90 text-white`
      : `${tokens.tag} text-muted`;

  return (
    <div className={`flex items-center gap-2.5 ${className}`}>
      <ErvSunMark size={tokens.mark} variant={variant} />
      {showWordmark || showTagline ? (
        <div>
          {showWordmark ? <p className={wordClass}>ERV</p> : null}
          {showTagline ? (
            <p className={tagClass}>Relay-synced training library editor</p>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}
