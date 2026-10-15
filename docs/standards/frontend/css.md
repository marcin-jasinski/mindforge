## CSS

### Consistent Methodology
Stick to the project's chosen approach (Tailwind, BEM, CSS modules, etc.) across the entire codebase.

### Work With the Framework
Use framework patterns as intended rather than fighting them with excessive overrides.

### Design Tokens
Establish and document consistent values for colors, spacing, and typography.

MindForge uses one dark theme. The tokens are the `--mf-*` custom properties in `frontend/src/styles.scss`; Material
reads its `--mat-sys-*` roles from the same values through `mat.theme-overrides`. Shared classes (`.card`, `.chip`,
`.status-*`, `.icon-tile`, `.empty`, `.alert`, `.skeleton`, `.progress-track`, `.kbd`, `.markdown`) live there too —
use them before writing component CSS, which has an 8 kB budget per component. Icons are `<app-icon name="…">`
(`shared/icon.ts`, inline SVG paths): the CSP admits only self-hosted fonts, so there is no icon font. The typeface
is Inter, bundled from `@fontsource-variable/inter`.

### Minimize Custom CSS
Prefer framework utilities to reduce custom styling maintenance.

### Production Optimization
Use CSS purging or tree-shaking to remove unused styles.
