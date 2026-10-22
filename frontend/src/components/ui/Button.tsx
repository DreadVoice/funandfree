import type { ButtonHTMLAttributes, ReactNode } from "react";

type Variant = "filled" | "outline" | "text" | "danger";

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  size?: "md" | "sm";
  icon?: boolean;
  children?: ReactNode;
}

export function Button({
  variant = "filled",
  size = "md",
  icon = false,
  className,
  type = "button",
  children,
  ...rest
}: ButtonProps) {
  const classes = [
    "btn",
    `btn-${variant}`,
    size === "sm" ? "btn-sm" : "",
    icon ? "btn-icon" : "",
    className ?? "",
  ]
    .filter(Boolean)
    .join(" ");

  return (
    <button type={type} className={classes} {...rest}>
      {children}
    </button>
  );
}
