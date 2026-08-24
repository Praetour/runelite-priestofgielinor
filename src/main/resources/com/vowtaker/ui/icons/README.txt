Drop 48x48 (or 64x64) PNG icons here for the god picker cards.

Required filenames (all lowercase, exact match to the GodAlignment enum name):
  saradomin.png
  zamorak.png
  guthix.png
  armadyl.png
  bandos.png
  zaros.png

Icons are scaled to 48x48 at load time, so a source of 64x64 or 96x96 gives
better antialiasing. Transparency (alpha) is respected.

If an icon file is missing, the card falls back to name + tagline only — no
crash. So it's safe to add them one at a time.
