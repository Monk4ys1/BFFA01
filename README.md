# BFFA01 (BuildFFA)
# SPOILER this PLugin is Semi-Vibecoded so just report bugs if you find any. Tanks in Advance for using it. :)
A BuildFFA minigame for Minecraft Java Edition. One jar runs on Spigot, Paper, Purpur, and Folia from 1.20 through the current release.

## Features

- **Map Management:** Configure arenas in `config.yml` and rotate them on a timer.
- **Kit layout:** Players arrange their hotbar and save it with `/savekit`.
- **Shop:** Permanent upgrades and one-time items, opened with `/shop`.
- **Killstreaks & Stats:** Track kills and deaths, calculate K/D ratios, and reward killstreaks.
- **Scoreboard:** Sidebar stats from `scoreboard.yml`, with `%bffa_*%` tokens when PlaceholderAPI is installed.
- **Integrations:** PlaceholderAPI, MiniPlaceholders, Vault, LuckPerms, WorldGuard, DecentHolograms, FancyHolograms, Geyser, Floodgate, ViaVersion, bStats, and Discord webhooks. Each one is optional. The jar still loads when none of them are installed.
- **Messages:** MiniMessage in `lang/en.yml` and `lang/de.yml`. Set `language` in `config.yml`.
- **Storage:** YAML by default. SQLite is bundled. MySQL or MariaDB uses a driver jar you place in `plugins/BFFA01/drivers/`.

## Commands & Permissions

| Command | Description | Permission |
| :--- | :--- | :--- |
| `/bffa` | Admin command: `setmap`, `swapmap`, `addcoins`, `removecoins`, `resetstats`, `reload`. | `bffa.admin` (or the matching child node) |
| `/kit` | Remind the player to arrange their hotbar. | `bffa.kit` |
| `/savekit` | Save the current hotbar layout. | `bffa.savekit` |
| `/stats` | View personal kills, deaths, K/D, and coins. | `bffa.stats` |
| `/shop` | Open the coin shop. | `bffa.shop` |

`bffa.admin` defaults to operators and grants `bffa.admin.setmap`, `bffa.admin.swapmap`, `bffa.admin.coins`, `bffa.admin.stats`, and `bffa.admin.reload`. The player commands default to everyone.

Tab completion works on every server. Paper and Purpur also receive Brigadier literals for `/bffa` when that server build still fires the command-registration event.

## Servers and Java

The jar is Java 17 bytecode, compiled against Paper API 1.20.4. Minecraft 1.20.0 through 1.20.4 can load it on Java 17. Newer game versions still run that bytecode, but the server itself needs the Java version that build requires (Java 21 from 1.20.5, Java 25 for Paper 26.x).

`plugin.yml` uses `api-version: '1.20'` and `folia-supported: true`. On Folia, block and entity work goes through the region scheduler. If that scheduler is missing, the plugin disables itself instead of touching the world from the wrong thread.

## Installation

**Do not download `BFFA01-4.1.jar` from git history or a raw GitHub URL.** That file is an old binary and does not match this source. Build with `./mvnw verify` from the current tree only (or install a Release asset that was built from it) and verify the checksum before you put the jar on a server.

Release 4.1 included `/build` and a separate `maps.yml`. This tree has neither. If you are upgrading from 4.1, copy each arena spawn into the `maps:` section of `config.yml`.

Plugin jars are not stored in this repository. Install a build you produced yourself, or a GitHub Release asset whose checksum you have verified.

1. Build from source (below) or download the jar **and its `.sha256` file** from [GitHub Releases](https://github.com/Monk4ys1/BFFA01/releases).
2. Verify the download before copying it onto a server:

   ```bash
   sha256sum -c BFFA01-<version>.jar.sha256
   ```

   The jar and the checksum file must be in the same directory. Install the jar only when that command prints `OK`.

   That check detects a corrupted download. A checksum published in the same GitHub Release does not prove who built the jar, because anyone who can replace the jar can replace the checksum. Prefer a signed release or published build provenance when one is available.
3. Place the verified jar in the server's `plugins/` directory.
4. Restart the server. `config.yml`, `scoreboard.yml`, and `lang/en.yml` plus `lang/de.yml` are created in `plugins/BFFA01/`.
5. Set map spawns with `/bffa setmap <name>` or by editing the `maps` section of `config.yml`.

An existing `config.yml` without `config-version` is copied to `config.yml.v1.bak` and then rewritten. Comments in that file are dropped by the rewrite. Maps, the kit, and a `messages:` section are kept. Strings under `messages:` still override the language file.

## Configuration Files

- `config.yml` — settings, kit, map spawns, `language`, and `storage`.
- `lang/en.yml`, `lang/de.yml` — MiniMessage (or `&` color codes). Placeholders look like `%player%`.
- `scoreboard.yml` — sidebar layout. Keep lines short. Team prefixes are capped at 64 characters, so named colors are safer than long gradients.
- `data.yml` — default stats file. Left untouched when it cannot be parsed.
- `plugins/BFFA01/drivers/` — optional MySQL (`com.mysql.cj.jdbc.Driver`) or MariaDB (`org.mariadb.jdbc.Driver`) jar. SQLite does not need one.

`/bffa reload` reloads config, language, the scoreboard file, the map list, and storage. It flushes the current store, then opens whatever `storage` now says. If that open fails, the store that was already running stays in use. Switching `storage.type` does not copy rows into the new store. The previous file or database is left as it was after the flush.

Holograms stay off until `holograms.enabled` is true. `holograms.backend` chooses `auto`, `decentholograms`, `fancyholograms`, or `armorstand`. `auto` uses DecentHolograms, then FancyHolograms, then armor stands at the current spawn.

## Integrations

Every hook has `integrations.<name>.enabled`. `false` turns that hook off even when the plugin is installed. Missing keys use the defaults in `config.yml`. An older `config.yml` does not need those keys: kill rewards stay at 0 coins until `integrations.vault.kill-amount` is set.

| Hook | What it does | Config |
| :--- | :--- | :--- |
| PlaceholderAPI | `%bffa_*%` expansion, identifier `bffa` | `integrations.placeholderapi.enabled` |
| MiniPlaceholders | `<bffa_kills>` and the other tags below | `integrations.miniplaceholders.enabled` |
| Vault | Shop currency and kill rewards | `integrations.vault.shop` is `internal` or `vault`. `kill-reward` is `internal`, `vault`, or `both`. `kill-amount` is the payout. The shipped file uses `5`. |
| LuckPerms | Contexts `bffa-map`, `bffa-streak` (`0`, `1-4`, `5-9`, `10+`), `bffa-bedrock`. Prefixs prefer LuckPerms, then Vault chat. | `integrations.luckperms.enabled` |
| WorldGuard | State flags `bffa-build` and `bffa-pvp`, default allow. Registered in `onLoad`. Optional arena region. | `integrations.worldguard.flags`, `arena-region`, `deny-build-outside`, `deny-pvp-outside`. A map can set `maps.<name>.region`. |
| DecentHolograms / FancyHolograms | Leaderboard hologram. Armor stands remain the fallback. | `holograms.enabled`, `holograms.backend`, `holograms.id` |
| Geyser / Floodgate | Bedrock detection. Floodgate players get Cumulus forms for `/shop` and `/stats`. Java players keep the chest GUI. | `integrations.floodgate.forms` |
| ViaVersion | `%bffa_protocol%` and `%bffa_client%` | `integrations.viaversion.enabled` |
| bStats | Custom charts for server software, storage, language, and active integrations. Nothing is sent while `plugin-id` is `0`. `plugins/bStats/config.yml` can still opt out. | `integrations.metrics.enabled`, `integrations.metrics.plugin-id` |
| Discord | Webhook embeds for kills, deaths, streak milestones, and map changes. Only `https` URLs on `discord.com`, `discordapp.com`, `canary.discord.com`, or `ptb.discord.com` whose path starts with `/api/webhooks/`. | `integrations.discord.*` |

PlaceholderAPI parameters (the `%` signs are added by PlaceholderAPI around `bffa_<param>`):

`kills`, `deaths`, `kd`, `coins`, `streak`, `rank`, `map`, `kit`, `prefix`, `bedrock`, `protocol`, `client`, `vault`, `online`, `kills_<name>`, `deaths_<name>`, `kd_<name>`, `coins_<name>`, `streak_<name>`, `rank_<name>`, `top_name_<n>`, `top_kills_<n>`, `top_kd_<n>`.

MiniPlaceholders uses the same names: `<bffa_kills>`, `<bffa_map>`, `<bffa_top_name:1>`.

## Developer API

Other plugins can take `infinode.org.example.bFFA01.api.BuildFFA` from `BuildFFAProvider.get()` or from Bukkit's services manager. The interface exposes the current map and spawn, kills, deaths, coins, K/D, streak, leaderboard position, kit name, Bedrock detection, ViaVersion protocol, prefix, and the active integration ids.

These events are not cancellable. Stats are already updated when they fire:

- `BuildFFAKillEvent`
- `BuildFFADeathEvent`
- `BuildFFAStreakEvent` (including a ruined streak)
- `BuildFFAMapChangeEvent`
- `BuildFFAPurchaseEvent`
- `BuildFFAReloadEvent` (`isStorageReconnected()` says whether the store swap succeeded)

## Building from Source

`./mvnw` downloads Maven 3.9.9 and checks its distribution checksum. Maven older than 3.9 ignores trusted checksums and does not fail the build, so the enforcer rejects anything below 3.9.0.

The compiler target is Java 17. A JDK 17 or newer can build the project.

```bash
./mvnw verify
```

The plugin jar is `target/BFFA01-<version>.jar`, where `<version>` is the version in `pom.xml`. `target/` is gitignored. Adventure is shaded and relocated. SQLite JDBC is shaded next to the plugin classes.

The Paper API dependency is the immutable build `1.20.4-R0.1-20241030.192207-176`. Maven Resolver trusted checksums in `.mvn/` cover every resolved artifact, and the build checks that jar's SHA-512.

To publish that jar, create a GitHub Release and attach both the jar and a checksum file:

```bash
( cd target && sha256sum BFFA01-*.jar ) | tee BFFA01-<version>.jar.sha256
```

Hash from inside `target/` so the checksum file contains only the jar basename (`BFFA01-<version>.jar`), not `target/...`. `sha256sum -c` then succeeds when both files are in the same directory. Upload `target/BFFA01-<version>.jar` and `BFFA01-<version>.jar.sha256` as Release assets. Do not commit either file.

## Requirements

- A Spigot, Paper, Purpur, or Folia server for Minecraft Java Edition **1.20** or newer.
- Java 17 or newer to build. The server JVM must still match the Minecraft version you run.
