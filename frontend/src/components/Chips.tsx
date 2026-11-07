export interface ChipOption {
  value: string
  label: string
}

/**
 * A single-choice row of pill buttons that scrolls sideways on narrow screens.
 * Big tap targets, and aria-pressed so screen readers announce the selection.
 */
export function Chips({ label, options, value, onChange }: {
  label: string
  options: ChipOption[]
  value: string
  onChange: (value: string) => void
}) {
  return (
    <div className="chips" role="group" aria-label={label}>
      {options.map((o) => (
        <button
          key={o.value}
          type="button"
          className="chip"
          aria-pressed={o.value === value}
          onClick={() => onChange(o.value)}
        >
          {o.label}
        </button>
      ))}
    </div>
  )
}
