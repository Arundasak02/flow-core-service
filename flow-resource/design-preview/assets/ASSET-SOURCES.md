# Asset Sources and Usage

## Icon Libraries

- **Lucide** (primary icon set)
  - Website: https://lucide.dev
  - Usage: line icons in sidebar, header, actions, and graph controls.
- **Heroicons** (secondary/fallback)
  - Website: https://heroicons.com
  - Usage: fallback when a required icon is missing from Lucide, or for occasional solid icons.

## PNG and raster assets

Use PNG only when vector is not appropriate.

- Product logos / partner marks:
  - Source from official brand kits or media pages.
- Photos / illustration backgrounds:
  - Use licensed stock or in-house generated assets.
- Graph thumbnails / previews:
  - Export from internal Figma frames.

## Folder structure

- `flow-resource/design-preview/assets/png/` - raster assets
- `flow-resource/design-preview/assets/svg/` - vector assets
- `flow-resource/design-preview/inspiration/` - benchmark captures

## Rules

- Prefer SVG for UI icons and logos.
- Document source URL and license for each external asset.
- Keep naming consistent:
  - `brand-<name>.svg`
  - `bg-<scene>-v1.png`
  - `illustration-<topic>-v1.png`
