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

/**
 * Pulse sun: eight short rays offset by 22.5°, a gradient disc, and a heartbeat line.
 * Same geometry as ic_launcher_foreground / package icon, scaled to a 64-unit box.
 */
const SUN_RAYS = Array.from({ length: 8 }, (_, index) => {
  const angle = (index * Math.PI) / 4 + Math.PI / 8;
  const cos = Math.cos(angle);
  const sin = Math.sin(angle);
  return {
    x1: 32 + 22.08 * cos,
    y1: 32 + 22.08 * sin,
    x2: 32 + 29.44 * cos,
    y2: 32 + 29.44 * sin,
  };
});

const PULSE_PATH = "M14.52,32.92 L23.72,32.92 L27.4,24.64 L32,41.2 L36.14,28.32 L38.44,32.92 L49.48,32.92";

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

  return (
    <svg
      viewBox="0 0 64 64"
      width={size}
      height={size}
      className={className}
      aria-hidden
    >
      <defs>
        <radialGradient
          id={gradientId}
          cx="28.32"
          cy="30.16"
          r="27.09"
          gradientUnits="userSpaceOnUse"
        >
          <stop offset="0%" stopColor="#fff9c4" />
          <stop offset="55%" stopColor="#ffd600" />
          <stop offset="100%" stopColor="#ff8f00" />
        </radialGradient>
      </defs>
      <g fill="none" stroke={ray} strokeLinecap="round" strokeWidth="4.05">
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
      <circle cx="32" cy="32" r="17.48" fill={`url(#${gradientId})`} />
      <path
        d={PULSE_PATH}
        fill="none"
        stroke="#c62828"
        strokeWidth="3.13"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
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
