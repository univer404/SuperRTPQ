# SuperRTPQ

SuperRTPQ is a Java/Maven Minecraft plugin that implements a configurable FIFO random-teleport queue for Paper and Purpur. It is an original implementation inspired by common server RTP queue behavior; it does not use DonutSMP source code, private assets, or proprietary fonts.

## Requirements

- Java 21
- Maven 3.9+
- Paper or Purpur compatible with the Paper 1.21.4 API line
- A configured world loaded by the server

## Build

Run from the project root:

```sh
mvn clean package
```

The JAR should be created at `target/SuperRTPQ.jar`. Copy it into your server's `plugins` directory and restart. The plugin creates `plugins/SuperRTPQ/config.yml` on first launch.

## Commands

| Command | Description |
| --- | --- |
| `/rtp` or `/rtp join` | Join the queue |
| `/rtp leave` | Leave the queue |
| `/rtp status` | Show queue size and your position |
| `/rtp gui` | Reserved GUI entry point; GUI is not implemented yet |
| `/rtpq status` | Show current queue size |
| `/rtpq clearqueue` | Clear all queue entries |
| `/rtpq remove <player>` | Remove an online player from the queue |
| `/rtpq forceteleport <player>` | Find a destination and teleport an online player |
| `/rtpq reload` | Reload configuration and restart queue processing |
| `/rtpq debug` | Toggle debug logging |

## Permissions

- `superrtpq.use` — use the queue (default: everyone)
- `superrtpq.admin` — use admin commands (default: operators)
- `superrtpq.bypass.cooldown` — bypass the RTP cooldown (default: operators)

## Configuration notes

Edit `plugins/SuperRTPQ/config.yml` and add your actual world name under `rtp.worlds`. By default, the plugin will not load new chunks while searching; a candidate is skipped if its chunk is not already loaded. Set `rtp.load-chunks: true` only after considering server performance and chunk-generation policy.

The destination search is bounded by `rtp.max-attempts`. If no safe location is found, the current player is moved to the back of the queue so other players can continue.

## Current implementation status

This is an initial development build, not a claim of production readiness. The basic queue, cooldown, countdown, bounded destination search, player commands, and admin commands have been added. The GUI, PlaceholderAPI integration, WorldGuard region exclusion, LuckPerms-specific integration, automated tests, and a live-server test pass are not implemented yet. Test on a staging server before using it on a public network.

## Compatibility

The project currently targets Paper API 1.21.4 and Java 21. Compatibility with other Minecraft versions must be verified by building and testing against those versions. Test the exact Paper/Purpur build you plan to use.
