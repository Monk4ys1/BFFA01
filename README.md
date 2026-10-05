# BFFA01 (BuildFFA)
# SPOILER this PLugin is Semi-Vibecoded so just report bugs if you find any. Tanks in Advance for using it. :)
A fully-featured BuildFFA minigame plugin for Minecraft servers, built natively for the Paper 1.21 API.

## Features

- **Map Management:** Configure arenas in `config.yml` and rotate them on a timer.
- **Kit layout:** Players arrange their hotbar and save it with `/savekit`.
- **Killstreaks & Stats:** Track kills and deaths, calculate K/D ratios, and reward killstreaks.
- **Scoreboard:** Sidebar stats from `scoreboard.yml`.
- **Configuration:** Settings, messages, the kit, and map spawns live in `config.yml`.

## Commands & Permissions

| Command | Description | Permission |
| :--- | :--- | :--- |
| `/bffa` | Admin command: `setmap`, `swapmap`, `addcoins`, `removecoins`, `resetstats`. | `bffa.admin` |
| `/kit` | Remind the player to arrange their hotbar. | None |
| `/savekit` | Save the current hotbar layout. | None |
| `/stats` | View personal kills, deaths, K/D, and coins. | None |

Map spawns are set with `/bffa setmap <name>`. `bffa.admin` defaults to operators.

## Installation

**Do not download `BFFA01-4.1.jar` from git history or a raw GitHub URL.** That file is an old binary and does not match this source. Build with `mvn package` from the current tree only (or install a Release asset that was built from it) and verify the checksum before you put the jar on a server.

Release 4.1 included `/build` and a separate `maps.yml`. This tree has neither. If you are upgrading from 4.1, copy each arena spawn into the `maps:` section of `config.yml`.

Plugin jars are not stored in this repository. Install a build you produced yourself, or a GitHub Release asset whose checksum you have verified.

1. Build from source (below) or download the jar **and its `.sha256` file** from [GitHub Releases](https://github.com/Monk4ys1/BFFA01/releases).
2. Verify the download before copying it onto a server:

   ```bash
   sha256sum -c BFFA01-<version>.jar.sha256
   ```

   The jar and the checksum file must be in the same directory. Install the jar only when that command prints `OK`.
3. Place the verified jar in the server's `plugins/` directory.
4. Restart the server. `config.yml` and `scoreboard.yml` are created in `plugins/BFFA01/`.
5. Set map spawns with `/bffa setmap <name>` or by editing the `maps` section of `config.yml`.

## Configuration Files

- `config.yml` — settings, messages, kit, and map spawns.
- `scoreboard.yml` — sidebar layout.
- `data.yml` — created at runtime for stats, coins, and kit layouts.

## Building from Source

Java 21 and Maven are required.

```bash
mvn package
```

The plugin jar is `target/BFFA01-<version>.jar`, where `<version>` is the version in `pom.xml`. `target/` is gitignored.

To publish that jar, create a GitHub Release and attach both the jar and a checksum file:

```bash
( cd target && sha256sum BFFA01-*.jar ) | tee BFFA01-<version>.jar.sha256
```

Hash from inside `target/` so the checksum file contains only the jar basename (`BFFA01-<version>.jar`), not `target/...`. `sha256sum -c` then succeeds when both files are in the same directory. Upload `target/BFFA01-<version>.jar` and `BFFA01-<version>.jar.sha256` as Release assets. Do not commit either file.

## Requirements

- Minecraft Server running **Paper 1.21** (or a compatible fork).
- **Java 21** or higher.
