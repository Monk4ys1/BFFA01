# BFFA01 (BuildFFA)
# SPOILER this PLugin is Semi-Vibecoded so just report bugs if you find any. Tanks in Advance for using it. :)
A BuildFFA minigame for Minecraft Java Edition. One jar runs on Spigot, Paper, Purpur, and Folia from 1.20 through the current release.

## Features

- **Map Management:** Configure arenas in `config.yml` and rotate them on a timer.
- **Menus:** `/menu` opens a chest GUI for the shop, kit editor, map vote, stats, and settings. The same inventories work through Geyser.
- **Kit layout:** Arrange the hotbar in `/kit`, or sort it in your inventory and run `/savekit`.
- **Shop:** Categories for permanent upgrades and one-time items, opened with `/shop`.
- **Killstreaks & Stats:** Track kills and deaths, calculate K/D ratios, and reward killstreaks. `/stats` opens the record and the leaderboard.
- **Look:** Palette and icons live in `theme.yml`. Scoreboard, tab header, boss bar, action bar, titles, kill effects, and chat use them.
- **Scoreboard:** Sidebar stats from `scoreboard.yml`, with PlaceholderAPI tokens when that plugin is installed. Long gradients stay intact on Paper and are resampled to the 64-character team limit on Spigot.
- **Messages:** MiniMessage in `lang/en.yml` and `lang/de.yml`. Set `language` in `config.yml`. `%color_primary%` and `%icon_map%` follow `theme.yml`.
- **Storage:** YAML by default. SQLite is bundled. MySQL or MariaDB uses a driver jar you place in `plugins/BFFA01/drivers/`.

## Commands & Permissions

| Command | Description | Permission |
| :--- | :--- | :--- |
| `/bffa` | Admin command: `setmap`, `swapmap`, `addcoins`, `removecoins`, `resetstats`, `reload`. | `bffa.admin` (or the matching child node) |
| `/menu` | Open the hub for shop, kit, maps, stats, and settings. | `bffa.menu` |
| `/kit` | Open the kit editor. | `bffa.kit` |
| `/savekit` | Save the current hotbar layout. | `bffa.savekit` |
| `/stats` | Open personal stats and the leaderboard. | `bffa.stats` |
| `/shop` | Open the coin shop. | `bffa.shop` |
| `/maps` | Vote for the next map. | `bffa.maps` |
| `/settings` | Toggle the sidebar, boss bar, action bar, and kill effect. | `bffa.settings` |

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
4. Restart the server. `config.yml`, `scoreboard.yml`, `theme.yml`, `menus.yml`, `presentation.yml`, and `lang/en.yml` plus `lang/de.yml` are created in `plugins/BFFA01/`.
5. Set map spawns with `/bffa setmap <name>` or by editing the `maps` section of `config.yml`.

An existing `config.yml` without `config-version` is copied to `config.yml.v1.bak` and then rewritten. Comments in that file are dropped by the rewrite. Maps, the kit, and a `messages:` section are kept. Strings under `messages:` still override the language file.

## Configuration Files

- `config.yml` — settings, kit, map spawns, `language`, and `storage`.
- `theme.yml` — palette and icons. Language lines can use `%color_primary%` and `%icon_map%`.
- `menus.yml` — chest menu slots, materials, and actions.
- `presentation.yml` — tab list, boss bar, action bar, titles, sounds, kill effects, and the first-join welcome.
- `lang/en.yml`, `lang/de.yml` — MiniMessage (or `&` color codes). Placeholders look like `%player%`.
- `scoreboard.yml` — sidebar layout. Gradients are sent in full on Paper. On Spigot they are resampled so each line stays within the 64-character prefix and suffix.
- `data.yml` — default stats file. Left untouched when it cannot be parsed.
- `plugins/BFFA01/drivers/` — optional MySQL (`com.mysql.cj.jdbc.Driver`) or MariaDB (`org.mariadb.jdbc.Driver`) jar. SQLite does not need one.

`/bffa reload` reloads config, language, the scoreboard file, and the map list. It does not reconnect storage. Change `storage` and restart.

Holograms are off until `holograms.enabled` is true. They are armor stands at the current spawn, not a hologram-plugin backend.

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
