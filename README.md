# BFFA01 (BuildFFA)

A BuildFFA minigame plugin for Minecraft servers: arena rotation, kits, a coin
shop, killstreaks, scoreboards and holograms.

**Runs on Minecraft 1.20.1 and every newer release** (Spigot, Paper and forks).
One jar covers all of them: the plugin is compiled against the oldest supported
API and resolves everything that changed in later releases at runtime.

## Features

- **Arenas** – rotate automatically, with chat warnings, a boss bar countdown
  and a title on every swap. Per-arena kill height and safe-zone height.
- **Building** – placed blocks are tracked, flash a warning colour and
  disappear again. The map itself is protected, player builds are not.
- **Kit & kit editor** – a configurable kit plus a menu where players arrange
  their own hotbar layout.
- **Shop** – permanent upgrades and 13 consumables, all priced, named and
  positioned from the config.
- **Combat tag** – action-bar timer, red/green names, no regeneration and no
  commands while fighting, combat logging counts as a death.
- **Killstreaks** – rewards defined entirely in the config (messages, sounds,
  coins, potion effects, items).
- **Scoreboard, tab list and holograms** – all placeholder driven.

## Commands

| Command | Description | Permission |
| :--- | :--- | :--- |
| `/bffa help` | Show the admin help. | `bffa.admin` |
| `/bffa setmap <name>` | Save your position as an arena spawn. | `bffa.admin` |
| `/bffa delmap <name>` | Delete an arena. | `bffa.admin` |
| `/bffa maps` | List every arena. | `bffa.admin` |
| `/bffa setlevel <map> <death\|safe> <y>` | Set the kill or safe-zone height. | `bffa.admin` |
| `/bffa swapmap [map]` | Start the swap countdown now. | `bffa.admin` |
| `/bffa addcoins\|removecoins\|setcoins <player> <amount>` | Manage balances. | `bffa.admin` |
| `/bffa resetstats <player>` | Clear kills, deaths and best streak. | `bffa.admin` |
| `/bffa reload` | Reload every configuration file. | `bffa.admin` |
| `/build [player]` | Toggle build mode (bypasses arena protection). | `bffa.build` |
| `/shop` | Open the shop. | `bffa.shop` (default: everyone) |
| `/kit` | Open the kit editor. | `bffa.kit` (default: everyone) |
| `/savekit` | Save the current hotbar as your layout. | `bffa.kit` |
| `/stats [player]` | Open the statistics menu. | `bffa.stats`, other players need `bffa.stats.other` |

Extra permission: `bffa.bypass.combat` exempts a player from the combat tag.
All commands support tab completion.

## Configuration

| File | Contents |
| :--- | :--- |
| `config.yml` | Arena timing, building, combat, rewards, protection, display, kit, killstreaks, shop and all messages. |
| `maps.yml` | Arena spawns and their kill/safe-zone heights. |
| `scoreboard.yml` | Sidebar title and lines. |
| `data.yml` | Player statistics. Written by the plugin, not meant for hand editing. |

Colours accept `&`-codes and hex (`&#55FFAA`) everywhere.

**Scoreboard placeholders:** `%player%`, `%kills%`, `%deaths%`, `%kd%`,
`%coins%`, `%streak%`, `%best_streak%`, `%rank%`, `%map%`, `%time%`,
`%online%`.

## Installation

1. Build the jar (see below) or take it from a release.
2. Put it into `plugins/` and start the server.
3. `config.yml`, `maps.yml` and `scoreboard.yml` are created in
   `plugins/BFFA01/`.
4. Stand on a spawn point and run `/bffa setmap <name>`, then set the heights
   with `/bffa setlevel <name> death <y>` and `/bffa setlevel <name> safe <y>`.

> The `BFFA01-4.1.jar` in this repository is the **previous** release. Version
> 5.0 has to be built from source until a new jar is published.

## Upgrading from 4.x

Nothing has to be done by hand. On first start the plugin:

- moves the `maps:` section out of `config.yml` into `maps.yml`,
- moves the flat tuning keys into their new sections
  (`map-swap-interval` → `arena.map-swap-interval`, `combat-regen-pause` →
  `combat.tag-seconds`, ...),
- adds every key introduced in 5.0 to the existing `config.yml`,
- keeps `data.yml` as it is, including purchased upgrades and saved layouts.

Custom messages and the `kit:` section keep their old paths and are preserved.

## Building from Source

Requires **JDK 17 or newer** and Maven.

```
mvn clean package
```

The jar lands in `target/BFFA01-5.0.jar`. It is compiled against the Paper
1.20.1 API and produces Java 17 bytecode, so it runs on 1.20.1 servers
(Java 17) as well as 1.20.5+ servers (Java 21).

## Requirements

- Minecraft **1.20.1 or newer** (Spigot, Paper or a compatible fork)
- **Java 17** or newer
