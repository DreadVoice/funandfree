import { useState } from "react";
import { Button } from "./ui/Button";
import { TextInput } from "./ui/Field";

interface SearchFiltersProps {
  initialQuery?: string;
  availableOnly: boolean;
  onSearch: (query: string) => void;
  onAvailableOnlyChange: (value: boolean) => void;
}

export function SearchFilters({
  initialQuery = "",
  availableOnly,
  onSearch,
  onAvailableOnlyChange,
}: SearchFiltersProps) {
  const [query, setQuery] = useState(initialQuery);

  return (
    <form
      className="card stack"
      onSubmit={(formEvent) => {
        formEvent.preventDefault();
        onSearch(query.trim());
      }}
    >
      <div className="row" style={{ flexWrap: "wrap" }}>
        <div style={{ flex: "1 1 240px" }}>
          <TextInput
            value={query}
            placeholder="Search events by title or description"
            aria-label="Search events"
            onChange={(inputEvent) => setQuery(inputEvent.target.value)}
          />
        </div>
        <Button type="submit">Search</Button>
        {initialQuery ? (
          <Button
            variant="text"
            onClick={() => {
              setQuery("");
              onSearch("");
            }}
          >
            Clear
          </Button>
        ) : null}
      </div>
      <label className="row small" style={{ gap: 8 }}>
        <input
          type="checkbox"
          checked={availableOnly}
          onChange={(inputEvent) => onAvailableOnlyChange(inputEvent.target.checked)}
        />
        Only show events with seats left
      </label>
    </form>
  );
}
