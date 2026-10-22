import { useTheme, type ThemeChoice } from "../../lib/theme";

const options: { value: ThemeChoice; label: string }[] = [
  { value: "system", label: "Auto" },
  { value: "light", label: "Light" },
  { value: "dark", label: "Dark" },
];

export function ThemeToggle() {
  const { choice, setChoice } = useTheme();

  return (
    <div className="segmented" role="group" aria-label="Colour theme">
      {options.map((option) => (
        <button
          key={option.value}
          type="button"
          className={option.value === choice ? "segmented-item segmented-item-active" : "segmented-item"}
          aria-pressed={option.value === choice}
          onClick={() => setChoice(option.value)}
        >
          {option.label}
        </button>
      ))}
    </div>
  );
}
