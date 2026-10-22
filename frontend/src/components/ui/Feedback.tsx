import type { ReactNode } from "react";

type Tone = "neutral" | "primary" | "success" | "danger";

export function Badge({ tone = "neutral", children }: { tone?: Tone; children: ReactNode }) {
  const suffix = tone === "neutral" ? "" : ` badge-${tone}`;
  return <span className={`badge${suffix}`}>{children}</span>;
}

export function Alert({ tone = "neutral", children }: { tone?: Tone; children: ReactNode }) {
  const suffix = tone === "danger" ? " alert-danger" : tone === "success" ? " alert-success" : "";
  return (
    <div className={`alert${suffix}`} role={tone === "danger" ? "alert" : undefined}>
      {children}
    </div>
  );
}

export function Empty({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <div className="empty stack">
      <h3>{title}</h3>
      {children ? <p className="small">{children}</p> : null}
    </div>
  );
}

export function Skeletons({ count = 6 }: { count?: number }) {
  return (
    <div className="grid">
      {Array.from({ length: count }, (_, index) => (
        <div className="skeleton" key={index} />
      ))}
    </div>
  );
}

export function SeatMeter({ taken, capacity }: { taken: number; capacity: number }) {
  const percent = capacity === 0 ? 0 : Math.min(100, Math.round((taken / capacity) * 100));
  return (
    <div className="stack" style={{ gap: 6 }}>
      <div className="meter">
        <div className="meter-fill" style={{ width: `${percent}%` }} />
      </div>
      <span className="small muted">
        {taken} of {capacity} seats taken
      </span>
    </div>
  );
}
