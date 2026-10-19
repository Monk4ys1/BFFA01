# BFFA01 (BuildFFA)

BuildFFA für Minecraft Java Edition. Ein Jar läuft auf Spigot, Paper, Purpur und Folia von **1.20** bis zur aktuellen Version.

A BuildFFA minigame for Minecraft Java Edition. One jar runs on Spigot, Paper, Purpur, and Folia from **1.20** through the current release.

Version **3.6**. Das Plugin ist teilweise KI-unterstützt entstanden. Fehler bitte melden.

**`BFFA01-4.1.jar` nicht aus der Git-Historie oder von einer rohen GitHub-URL laden.** Diese Datei ist ein altes Binary und passt nicht zu diesem Quelltext. Bauen mit `./mvnw verify` aus dem aktuellen Baum (oder ein Release-Asset, das daraus gebaut wurde) und die Prüfsumme prüfen, bevor das Jar auf einen Server kommt.

Release 4.1 hatte `/build` und eine eigene `maps.yml`. Dieser Baum hat beides nicht. Beim Wechsel von 4.1 die Arena-Spawns in den Abschnitt `maps:` von `config.yml` übernehmen.

## Funktionen

- **Karten:** Arenen in `config.yml`, Wechsel über einen Timer. `/bffa reload` lädt die Kartenliste neu, ohne eine laufende Runde zu beenden oder den Timer doppelt zu starten.
- **Ein Menümodell:** `/menu`, `/shop`, `/kit`, `/maps`, `/stats` und `/settings` bauen dieselbe Seite. Java-Spieler bekommen eine Kiste. Floodgate-Bedrock-Spieler bekommen dasselbe Menü als Cumulus-Formular, wenn `integrations.floodgate.forms` an ist.
- **Kit:** Hotbar in `/kit` anordnen oder im Inventar sortieren und mit `/savekit` speichern.
- **Shop:** Dauerhafte Upgrades und Einmal-Items. Angezeigte Münzen sind das Guthaben, das auch abgebucht wird: interne Münzen oder, bei `integrations.vault.shop: vault`, das formatierte Vault-Guthaben.
- **Killstreaks und Stats:** Kills, Tode, K/D, Streaks. `/stats` zeigt den eigenen Stand und die Rangliste.
- **Aussehen:** Palette und Icons in `theme.yml`. Scoreboard, Tab, Bossbar, Actionbar, Titel, Kill-Effekte, Chat und Hologramme nutzen dieselben Tokens (`%color_primary%`, `%icon_map%`).
- **Scoreboard:** Zeilen aus `scoreboard.yml`. Lokale Tokens, `%bffa_*%`, PlaceholderAPI und MiniPlaceholders werden vor MiniMessage eingesetzt. Lange Verläufe bleiben auf Paper erhalten und werden auf Spigot auf das 64-Zeichen-Limit der Teams gekürzt.
- **Sprache:** MiniMessage in `lang/en.yml` und `lang/de.yml`. `language` in `config.yml`.
- **Integrationen:** Alle optional. Das Jar startet auch ohne sie. Siehe Tabelle unten.
- **Speicher:** YAML als Standard. SQLite ist enthalten. MySQL oder MariaDB über einen Treiber in `plugins/BFFA01/drivers/`.

## Befehle und Rechte

| Befehl | Beschreibung | Permission |
| :--- | :--- | :--- |
| `/bffa` | Admin: `setmap`, `swapmap`, `addcoins`, `removecoins`, `resetstats`, `reload`. | `bffa.admin` (oder der passende Unterknoten) |
| `/menu` | Hub für Shop, Kit, Karten, Stats und Einstellungen. | `bffa.menu` |
| `/kit` | Kit-Editor. | `bffa.kit` |
| `/savekit` | Aktuelle Hotbar speichern. | `bffa.savekit` |
| `/stats` | Eigene Stats und Rangliste. | `bffa.stats` |
| `/shop` | Münz-Shop. | `bffa.shop` |
| `/maps` | Nächste Karte wählen. | `bffa.maps` |
| `/settings` | Sidebar, Bossbar, Actionbar und Kill-Effekt. | `bffa.settings` |

`bffa.admin` gilt standardmäßig für Operatoren und enthält `bffa.admin.setmap`, `bffa.admin.swapmap`, `bffa.admin.coins`, `bffa.admin.stats` und `bffa.admin.reload`. Die Spielerbefehle gelten für alle.

Tab-Vervollständigung gibt es auf jedem Server. Paper und Purpur bekommen zusätzlich Brigadier-Literale für `/bffa`, wenn dieser Build das Command-Event noch auslöst.

## Server und Java

Das Jar ist Java-17-Bytecode, kompiliert gegen Paper API 1.20.4. Minecraft 1.20.0 bis 1.20.4 lädt es auf Java 17. Neuere Spielversionen führen denselben Bytecode aus, der Server braucht aber die Java-Version dieser Version (Java 21 ab 1.20.5, Java 25 für Paper 26.x).

`plugin.yml` setzt `api-version: '1.20'` und `folia-supported: true`. Auf Folia laufen Block- und Entity-Arbeit über den Region-Scheduler. Fehlt der Scheduler, schaltet sich das Plugin ab, statt die Welt vom falschen Thread zu berühren.

Der Bytecode-Scan im Build lehnt Klassenversionen über 61, direkte `InventoryView`-Aufrufe und Verweise auf Paper-, NMS- oder CraftBukkit-Typen ab.

## Installation

Plugin-Jars liegen nicht in diesem Repository. Eine selbst gebaute Datei installieren oder ein GitHub-Release-Asset, dessen Prüfsumme geprüft wurde.

1. Aus dem Quelltext bauen (unten) oder das Jar **und** die Datei `.sha256` von [GitHub Releases](https://github.com/Monk4ys1/BFFA01/releases) laden.
2. Vor dem Kopieren prüfen:

   ```bash
   sha256sum -c BFFA01-<version>.jar.sha256
   ```

   Jar und Prüfsumme müssen im selben Verzeichnis liegen. Nur installieren, wenn die Ausgabe `OK` ist.

   Das erkennt einen beschädigten Download. Eine Prüfsumme im selben GitHub-Release beweist nicht, wer das Jar gebaut hat, weil jemand, der das Jar austauscht, auch die Prüfsumme austauschen kann. Eine signierte Veröffentlichung oder Build-Provenance ist vorzuziehen, sobald sie existiert.
3. Das geprüfte Jar in `plugins/` legen.
4. Server neu starten. `config.yml`, `scoreboard.yml`, `theme.yml`, `menus.yml`, `presentation.yml` sowie `lang/en.yml` und `lang/de.yml` entstehen in `plugins/BFFA01/`. `preferences.yml` entsteht, sobald ein Spieler Anzeige-Einstellungen ändert.
5. Spawns mit `/bffa setmap <name>` setzen oder den Abschnitt `maps` in `config.yml` bearbeiten.

Eine vorhandene `config.yml` ohne `config-version` wird nach `config.yml.v1.bak` kopiert und neu geschrieben. Kommentare gehen dabei verloren. Karten, Kit und ein Abschnitt `messages:` bleiben. Texte unter `messages:` überschreiben weiterhin die Sprachdatei.

## Konfigurationsdateien

| Datei | Inhalt |
| :--- | :--- |
| `config.yml` | Sprache, Speicher, Hologramme, Integrationen, Kartenwechsel, Kit, Spawns. `config-version` bleibt `2`. |
| `theme.yml` | Palette (`primary`, `secondary`, `accent`, `success`, `danger`, `muted`, `soft`, `warm`) und Icons. |
| `menus.yml` | Slots, Materialien und Aktionen der Kistenmenüs. |
| `presentation.yml` | Tabliste, Bossbar, Actionbar, Titel, Sounds, Kill-Effekte, Willkommen beim ersten Join. |
| `scoreboard.yml` | Titel und Zeilen der Sidebar. |
| `lang/en.yml`, `lang/de.yml` | MiniMessage oder `&`-Farbcodes. Platzhalter wie `%player%`. |
| `preferences.yml` | Sidebar, Bossbar, Actionbar und Kill-Effekt je Spieler. Wird zur Laufzeit geschrieben, nicht im Jar mitgeliefert. |
| `data.yml` | Standard-Statistik. Wird nicht geladen, wenn sie nicht lesbar ist, damit die Datei nicht überschrieben wird. |
| `plugins/BFFA01/drivers/` | Optionaler MySQL- (`com.mysql.cj.jdbc.Driver`) oder MariaDB-Treiber (`org.mariadb.jdbc.Driver`). SQLite braucht keinen. |

`/bffa reload` lädt Config, Sprache, Theme, Menüs, Präsentation, Scoreboard, Kartenliste, Integrationen und den Speicher neu. Der aktuelle Speicher wird zuerst geschrieben, danach öffnet das Plugin das in `storage` genannte Ziel. Schlägt das Öffnen fehl, bleibt der laufende Speicher. Ein Wechsel von `storage.type` kopiert keine Zeilen. Offene BuildFFA-Menüs werden geschlossen. Die Kartenrotation läuft weiter; nur das Intervall wird begrenzt (mindestens 10 Sekunden), und eine noch nie gestartete Rotation startet, wenn nach dem Reload Karten vorhanden sind.

Hologramme bleiben aus, bis `holograms.enabled` wahr ist. `holograms.backend` ist `auto`, `decentholograms`, `fancyholograms` oder `armorstand`. `auto` nimmt DecentHolograms, dann FancyHolograms, sonst Rüstungsständer am aktuellen Spawn. Die Zeilen laufen durch dasselbe Theme wie Scoreboard und Sprache.

## Integrationen

Jeder Hook hat `integrations.<name>.enabled`. `false` schaltet den Hook aus, auch wenn das Plugin installiert ist. Fehlende Schlüssel nutzen die Defaults. Eine ältere `config.yml` ohne `integrations.vault.kill-amount` zahlt 0 Münzen pro Kill. Die mitgelieferte Datei setzt `5`.

| Hook | Wirkung | Config |
| :--- | :--- | :--- |
| PlaceholderAPI | Expansion `%bffa_*%`, Kennung `bffa`. Dieselben Tokens in Theme, Scoreboard, Tab, Bossbar, Titeln und Hologrammen. | `integrations.placeholderapi.enabled` |
| MiniPlaceholders | `<bffa_kills>` und die übrigen Tags. Format-Tags wie `<gradient>` bleiben MiniMessage. | `integrations.miniplaceholders.enabled` |
| Vault | Shop-Währung und Kill-Belohnung. Im Shop, in Menüs und in `%coins%` steht dasselbe ausgabefähige Guthaben. | `integrations.vault.shop` ist `internal` oder `vault`. `kill-reward` ist `internal`, `vault` oder `both`. `kill-amount` ist die Auszahlung. |
| LuckPerms | Kontexte `bffa-map`, `bffa-streak` (`0`, `1-4`, `5-9`, `10+`), `bffa-bedrock`. Prefixe: LuckPerms, sonst Vault-Chat. | `integrations.luckperms.enabled` |
| WorldGuard | Zustandsflags `bffa-build` und `bffa-pvp`, Standard erlauben. Registrierung in `onLoad`. Optionale Arena-Region. | `integrations.worldguard.flags`, `arena-region`, `deny-build-outside`, `deny-pvp-outside`. Eine Karte kann `maps.<name>.region` setzen. |
| DecentHolograms / FancyHolograms | Rangliste als Hologramm, mit Theme-Zeilen. Rüstungsständer bleiben der Fallback. | `holograms.enabled`, `holograms.backend`, `holograms.id` |
| Geyser / Floodgate | Bedrock-Erkennung. Floodgate-Spieler bekommen Formulare für Hub, Shop, Kit, Karten, Stats und Einstellungen. Java-Spieler behalten die Kiste. | `integrations.floodgate.forms` |
| ViaVersion | `%bffa_protocol%` und `%bffa_client%` | `integrations.viaversion.enabled` |
| bStats | Diagramme für Serversoftware, Speicher, Sprache und aktive Integrationen. Bei `plugin-id` `0` wird nichts gesendet. `plugins/bStats/config.yml` kann weiter abwählen. | `integrations.metrics.enabled`, `integrations.metrics.plugin-id` |
| Discord | Webhook-Embeds für Kills, Tode, Streak-Meilensteine und Kartenwechsel. Nur `https` ohne Userinfo, Query und Fragment, Host `discord.com`, `discordapp.com`, `canary.discord.com` oder `ptb.discord.com`, Pfad genau `/api/webhooks/{id}/{token}`. | `integrations.discord.*` |

## Platzhalter

Lokal in Scoreboard, Tab, Bossbar, Actionbar und Titeln, bevor Platzhalter-Plugins laufen:

`%map%`, `%time%`, `%kills%`, `%deaths%`, `%kd%`, `%streak%`, `%coins%`, `%players%`.

Theme, überall dort, wo eine Zeile durch die Anzeige läuft, inklusive Sprachdatei und Hologramm:

`%color_primary%`, `%color_secondary%`, `%color_accent%`, `%color_success%`, `%color_danger%`, `%color_muted%`, `%color_soft%`, `%color_warm%`, sowie `%icon_map%`, `%icon_kills%`, `%icon_deaths%`, `%icon_coins%`, `%icon_streak%`, `%icon_time%`, `%icon_players%`, `%icon_shop%`, `%icon_kit%`, `%icon_stats%`, `%icon_settings%`, `%icon_vote%`.

PlaceholderAPI hängt die `%` um `bffa_<parameter>`:

`kills`, `deaths`, `kd`, `coins`, `streak`, `rank`, `map`, `kit`, `prefix`, `bedrock`, `protocol`, `client`, `vault`, `online`, `kills_<name>`, `deaths_<name>`, `kd_<name>`, `coins_<name>`, `streak_<name>`, `rank_<name>`, `top_name_<n>`, `top_kills_<n>`, `top_kd_<n>`.

MiniPlaceholders nutzt dieselben Namen: `<bffa_kills>`, `<bffa_map>`, `<bffa_top_name:1>`.

Eingefügte Werte (Namen, Karte, Kit, Client) können keine MiniMessage-Tags öffnen. Die Rangliste wird zwischengespeichert und erst bei Kill, Reset, Namensänderung oder erfolgreichem Speicher-Reconnect neu sortiert.

## Entwickler-API

Andere Plugins holen `infinode.org.example.bFFA01.api.BuildFFA` über `BuildFFAProvider.get()` oder den Bukkit-Service-Manager. Das Interface liefert aktuelle Karte und Spawn, Kills, Tode, Münzen, K/D, Streak, Rang, Kit-Name, Bedrock-Erkennung, ViaVersion-Protokoll, Prefix und die aktiven Integrations-IDs.

Diese Events sind nicht abbrechbar. Die Stats sind dann schon geschrieben:

- `BuildFFAKillEvent`
- `BuildFFADeathEvent`
- `BuildFFAStreakEvent` (auch eine gebrochene Streak)
- `BuildFFAMapChangeEvent`
- `BuildFFAPurchaseEvent`
- `BuildFFAReloadEvent` (`isStorageReconnected()` sagt, ob der Speicherwechsel geklappt hat)

## Bauen

`./mvnw` lädt Maven 3.9.9 und prüft die Distributions-Prüfsumme. Maven älter als 3.9 ignoriert vertrauenswürdige Prüfsummen und bricht den Build nicht ab, deshalb lehnt der Enforcer alles unter 3.9.0 ab.

Das Compiler-Ziel ist Java 17. Ein JDK 17 oder neuer kann bauen.

```bash
./mvnw verify
```

Das Plugin-Jar ist `target/BFFA01-<version>.jar`. Die Version steht in `pom.xml` (aktuell 3.6). `target/` ist gitignored. Adventure wird geshadet und verschoben. SQLite-JDBC liegt neben den Plugin-Klassen. PlaceholderAPI-Stubs werden nicht ins Jar gepackt.

Die Paper-API-Abhängigkeit ist der feste Build `1.20.4-R0.1-20241030.192207-176`. Vertrauenswürdige Maven-Resolver-Prüfsummen in `.mvn/` decken jedes aufgelöste Artefakt ab, und der Build prüft die SHA-512 dieses Jars.

Zum Veröffentlichen ein GitHub-Release anlegen und Jar plus Prüfsumme anhängen:

```bash
( cd target && sha256sum BFFA01-*.jar ) | tee BFFA01-<version>.jar.sha256
```

Aus `target/` hashen, damit die Prüfsummendatei nur den Basisnamen enthält (`BFFA01-<version>.jar`), nicht `target/...`. `sha256sum -c` klappt dann, wenn beide Dateien im selben Verzeichnis liegen. `target/BFFA01-<version>.jar` und `BFFA01-<version>.jar.sha256` als Release-Assets hochladen. Keine der beiden Dateien committen.

## Voraussetzungen

- Ein Spigot-, Paper-, Purpur- oder Folia-Server für Minecraft Java Edition **1.20** oder neuer.
- Java 17 oder neuer zum Bauen. Die Server-JVM muss zur Minecraft-Version passen.
