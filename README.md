# BFFA01 (BuildFFA)

A fully-featured BuildFFA minigame plugin for Minecraft servers, built natively for the Paper 1.21 API.

## Features

- **Map Management:** Easily configure, manage, and switch between multiple BuildFFA arenas.
- **Kit System & Editor:** Players can organize, customize, and save their preferred inventory layouts.
- **In-game Shop:** Purchase items, upgrades, or cosmetics using in-game statistics or currency.
- **Killstreaks & Stats:** Track kills and deaths, calculate K/D ratios, and reward players with killstreaks.
- **Scoreboards & Holograms:** Dynamic scoreboards and hologram integration to display top stats or player information.
- **Highly Customizable:** Extensive configuration via `config.yml`, `maps.yml`, and `scoreboard.yml`.

## Commands & Permissions

| Command | Description | Permission |
| :--- | :--- | :--- |
| `/bffa` | Main admin command for BuildFFA setup and configuration. | `bffa.admin` |
| `/build` | Toggle build mode to bypass arena protections and edit maps. | `bffa.admin` |
| `/kit` | Open the Kit Editor UI to customize your layout. | None |
| `/savekit` | Save your currently organized kit layout. | None |
| `/stats` | View your personal BuildFFA statistics. | None |
| `/setmap` | Map configuration command (spawns, height limits). | `bffa.admin` | *(Inferred)* |
| `/shop` | Open the in-game shop UI. | None | *(Inferred)* |

*(Note: Server administrators should have the `bffa.admin` permission to configure the arena)*

## Installation

1. Download the compiled `BFFA01-4.1.jar` file.
2. Place the `.jar` into your server's `plugins/` directory.
3. Restart or reload your server.
4. The default configuration files (`config.yml`, `maps.yml`, `scoreboard.yml`) will automatically generate in `plugins/BFFA01/`.
5. Setup your maps in-game using `/bffa` or by manually editing `maps.yml`.

## Configuration Files

- `config.yml` - Main plugin settings, localized messages, and shop pricing.
- `maps.yml` - Location data for your BuildFFA arenas (spawns, death zones, etc.).
- `scoreboard.yml` - Layout and design of the player side-scoreboards.

## Building from Source

This project uses Maven and requires **Java 21**.

1. Clone the repository.
2. Run `mvn clean package`.
3. The compiled jar will be located in the `target/` directory.

## Requirements

- Minecraft Server running **Paper 1.21** (or a compatible fork).
- **Java 21** or higher.
