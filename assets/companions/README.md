# Companion artwork

This folder keeps editable source artwork separate from Android runtime resources.

- `sources/` contains the original supplied images with clear, stable names.
- `../../app/src/main/res/drawable-nodpi/companion_*.webp` contains the optimized,
  transparent WebP files packaged with the app.
- `excluded-watermarked/` contains supplied stock previews that are not packaged in
  the app. Replace them with clean, licensed originals before use.

Runtime artwork is capped at 768 px on its longest side and encoded as lossless WebP.
Background removal preserves real alpha transparency instead of a painted checkerboard.
The `companion_thumb_*.webp` resources are lightweight 192 px previews used by the
Customize screen so the full overlay artwork does not cause scrolling stalls.
