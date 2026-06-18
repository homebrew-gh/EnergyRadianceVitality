import type { Config } from "tailwindcss";

export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  darkMode: "class",
  theme: {
    extend: {
      colors: {
        bg: "var(--erv-bg)",
        surface: "var(--erv-surface)",
        surfaceVariant: "var(--erv-surface-variant)",
        primary: "var(--erv-primary)",
        onPrimary: "var(--erv-on-primary)",
        primaryContainer: "var(--erv-primary-container)",
        onPrimaryContainer: "var(--erv-on-primary-container)",
        secondary: "var(--erv-secondary)",
        header: "var(--erv-header)",
        heading: "var(--erv-heading)",
        onSurface: "var(--erv-on-surface)",
        onSurfaceVariant: "var(--erv-on-surface-variant)",
        outline: "var(--erv-outline)",
        error: "var(--erv-error)",
        success: "var(--erv-success)",
      },
      fontFamily: {
        sans: ["Nunito", "system-ui", "sans-serif"],
      },
      borderRadius: {
        card: "12px",
        pill: "9999px",
      },
      boxShadow: {
        card: "var(--erv-shadow-card)",
      },
    },
  },
  plugins: [],
} satisfies Config;
