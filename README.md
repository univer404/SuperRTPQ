# SuperRTPQ

**Random teleport, three dimension choices, and a FIFO 1v1 RTP queue** for Paper/Purpur servers. Designed to be easy to configure, with compact inventory menus and clear feedback.

## Highlights

- **Three-dimension RTP menu:** Overworld, Nether, and End, each showing its configured world and world-border size.
- **Safe-location search:** Bounded search with checks for passable feet/head space, solid ground, water, lava, fire and border margins.
- **RTP queue:** FIFO queue pairs two waiting online players for a paired teleport, with an optional countdown and movement cancellation.
- **Separate queue menu:** Join, check status, and leave without mixing queue actions with dimension selection.
- **Configurable:** World names, search distance, safety checks, queue limits, cooldown, countdown, and MiniMessage chat messages.
- **Admin tools:** Inspect queue/world status, clear the queue, remove a player, force a teleport, reload config, and toggle debug logging.
- **Straight GUI text:** GUI titles, item names, and lore explicitly disable Minecraft's default italic decoration.

## Requirements

- Java 21
- Paper or compatible Purpur server on the Paper 1.21.4 API line
- Correctly loaded worlds configured for RTP

No Vault, PlaceholderAPI, or other plugin is required by this implementation.

## Install

1. Download the `SuperRTPQ.jar` from a successful GitHub Actions build artifact.
2. Put it in the server's `plugins/` folder.
3. Start the server once so it creates `plugins/SuperRTPQ/config.yml`.
4. Edit the world names and search settings in that live config.
5. Restart or run `/rtpq admin reload`.
6. Test on a staging world before using it on a public server.

## Commands

### Instant random teleport

| Command | Behavior |
| --- | --- |
| `/rtp` | Opens the dimension selection GUI. |
| `/rtp gui` | Opens the dimension selection GUI. |
| `/rtp now` or `/rtp random` | Instant RTP in the configured Overworld. |
| `/rtp overworld` | Instant RTP in the configured Overworld. |
| `/rtp nether` | Instant RTP in the configured Nether. |
| `/rtp end` | Instant RTP in the configured End. |

### Queue

| Command | Behavior |
| --- | --- |
| `/rtpq` or `/rtpq gui` | Opens the queue menu. |
| `/rtpq join` | Joins the waiting queue. |
| `/rtpq leave` | Leaves the queue or cancels a paired countdown. |
| `/rtpq status` | Shows queue size and your position. |

### Admin

| Command | Behavior |
| --- | --- |
| `/rtpq admin status` | Shows queue size and the fallback RTP world status. |
| `/rtpq admin clearqueue` | Clears all queue entries and countdown pair states. |
| `/rtpq admin remove <player>` | Removes an online player from the queue. |
| `/rtpq admin forceteleport <player>` | Attempts a safe teleport in the first loaded fallback world. |
| `/rtpq admin reload` | Reloads config and restarts queue processing. |
| `/rtpq admin debug` | Toggles and saves debug logging. |

The shorter admin aliases `/rtpq status`, `/rtpq clearqueue`, `/rtpq remove`, `/rtpq forceteleport`, `/rtpq reload`, and `/rtpq debug` are also recognized.

## Permissions

| Permission | Default | Purpose |
| --- | --- | --- |
| `superrtpq.use` | Everyone | Use instant RTP and the dimension GUI. |
| `superrtpq.queue.use` | Everyone | Use the queue and queue GUI. |
| `superrtpq.admin` | Operators | Run admin commands. |
| `superrtpq.bypass.cooldown` | Operators | Bypass the RTP cooldown. |

## Configuration

The **live** configuration is `plugins/SuperRTPQ/config.yml`. A separately organized reference copy and explanations are in the `configuration/` folder of this repository.

Most important settings:

- `rtp.dimension-worlds.overworld/nether/end`: exact loaded world names for dimension RTP.
- `rtp.worlds`: fallback world list for queue/admin forced teleport.
- `rtp.min-distance` / `rtp.max-distance`: distance from the world-border center.
- `rtp.max-attempts`: bounds each safe-location search.
- `rtp.load-chunks`: allows chunk loading/generation when enabled; this may affect performance.
- `rtp.respect-world-border` / `rtp.border-margin`: keep candidates inside the world border.
- `queue.max-size`, `queue.cooldown-seconds`, `queue.countdown-seconds`, `queue.cancel-on-move`: queue behavior.
- `messages`: MiniMessage prefix and chat strings.
- `debug`: diagnostic logging.

Use spaces for YAML indentation, not tabs. World names must match exactly. Nether/End dimensions are unavailable until their configured worlds are loaded.

## Build from source

Requires JDK 21 and Maven:

```sh
mvn --batch-mode clean package
```

Output: `target/SuperRTPQ.jar`. GitHub Actions runs the same packaging command and publishes the JAR as a downloadable workflow artifact when the build succeeds.

## Important behavior and limitations

- The queue is an in-memory queue: entries and cooldowns do not persist across server restarts.
- Queue pairing uses the same selected safe destination, placing the second player two blocks away. Check that both positions are suitable for the chosen terrain and gamemode.
- Teleports and safe-location checks run on the server thread. Searching many locations or loading chunks can add tick time; keep attempt counts conservative.
- WorldGuard region exclusions, PlaceholderAPI expansion, persistent queue storage, and a fully atomic two-player teleport are not implemented.
- A successful build proves compilation/package success, not compatibility with every server build. Test your exact Paper/Purpur version and world setup before public release.

## Project layout

- `src/main/java/` — plugin behavior and commands.
- `src/main/resources/config.yml` — default config bundled into the plugin.
- `src/main/resources/plugin.yml` — commands and permissions.
- `configuration/` — separate human-readable config reference and copy.
- `.github/workflows/build.yml` — automated build workflow.
