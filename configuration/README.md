# SuperRTPQ configuration guide

This folder is a reference copy for editing and understanding settings. **The plugin does not load configuration from this folder inside the JAR.**

## Where the live config goes

1. Start the server once with SuperRTPQ installed.
2. Open `plugins/SuperRTPQ/config.yml`.
3. Edit the settings there.
4. Run `/rtpq admin reload` (or restart the server).

Keep a backup before changing settings. YAML indentation matters: use spaces, not tabs.

## Main sections

- `queue.enabled`: enables or disables queue joining.
- `queue.max-size`: maximum number of waiting players.
- `queue.process-interval-ticks`: how often the plugin looks for a pair.
- `queue.max-teleports-per-cycle`: limits how many pairs begin in one cycle.
- `queue.cooldown-seconds`: cooldown after successful teleport.
- `queue.countdown-seconds`: countdown before a paired match teleports.
- `queue.cancel-on-move`: cancel the paired countdown when a player moves.
- `rtp.dimension-worlds`: exact loaded world names used by `/rtp overworld|nether|end`.
- `rtp.worlds`: fallback world list used by queue/admin forced teleport.
- `rtp.min-distance` / `rtp.max-distance`: candidate distance from the world border center.
- `rtp.max-attempts`: maximum candidate locations per search.
- `rtp.load-chunks`: whether candidate chunks may be loaded/generated while searching.
- `rtp.respect-world-border` and `rtp.border-margin`: keep candidates inside the world border.
- `rtp.avoid-water`, `rtp.avoid-lava`, `rtp.avoid-fire`, `rtp.require-solid-ground`: safety checks.
- `messages`: MiniMessage-formatted chat text and prefix.
- `debug`: enables diagnostic logging.

## Important setup notes

- Set each world name to the exact name shown by your server. Nether and End worlds are often not loaded until a server or world manager loads them.
- `rtp.max-distance` is constrained by the world border. If the border is smaller than the configured range, many candidate points will be rejected.
- Allowing chunk loading can cause lag or generate new terrain. Start with a lower `rtp.max-attempts` and test during quiet hours.
- The GUI and all teleport behavior still use the live config at `plugins/SuperRTPQ/config.yml`; changes to this reference copy do not change a running server.
