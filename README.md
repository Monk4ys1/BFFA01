# BFFA01 (BuildFFA)

BuildFFA für Minecraft Java Edition. Ein Jar läuft auf Spigot, Paper, Purpur und Folia von **1.20** bis zur aktuellen Version.

A BuildFFA minigame for Minecraft Java Edition. One jar runs on Spigot, Paper, Purpur, and Folia from **1.20** through the current release.

Version **3.8**. Das Plugin ist teilweise KI-unterstützt entstanden. Fehler bitte melden.

**`BFFA01-4.1.jar` nicht aus der Git-Historie oder von einer rohen GitHub-URL laden.** Diese Datei ist ein altes Binary und passt nicht zu diesem Quelltext. Bauen mit `./mvnw verify` aus dem aktuellen Baum (oder ein Release-Asset, das daraus gebaut wurde) und die Prüfsumme prüfen, bevor das Jar auf einen Server kommt.

Release 4.1 hatte `/build` und eine eigene `maps.yml`. Dieser Baum hat beides nicht. Beim Wechsel von 4.1 die Arena-Spawns in den Abschnitt `maps:` von `config.yml` übernehmen.

## Funktionen

- **Karten:** Arenen in `config.yml`, Wechsel über einen Timer. `/bffa reload` lädt die Kartenliste neu, ohne eine laufende Runde zu beenden oder den Timer doppelt zu starten.
- **Ein Menümodell:** `/menu`, `/shop`, `/kit`, `/maps`, `/stats` und `/settings` bauen dieselbe Seite. Java-Spieler bekommen eine Kiste. Floodgate-Bedrock-Spieler bekommen dasselbe Menü als Cumulus-Formular, wenn `integrations.floodgate.forms` an ist.
- **Kit:** Hotbar in `/kit` anordnen oder im Inventar sortieren und mit `/savekit` speichern.
- **Shop:** Dauerhafte Upgrades fragen einmal nach, bevor Münzen weg sind (`shop-confirm-permanent` in `menus.yml`). Einmal-Items bleiben ein Klick. Angezeigte Münzen sind das Guthaben, das auch abgebucht wird: interne Münzen oder, bei `integrations.vault.shop: vault`, das formatierte Vault-Guthaben.
- **Killstreaks und Stats:** Kills, Tode, K/D, Streaks, Schaden verursacht und erhalten (Lebenszeit und aktuelle Serie), Assists. `/stats` zeigt den eigenen Stand, die Kill-Rangliste und die Schadensrangliste.
- **Schaden im Kampf:** Schwebende Trefferzahlen am Treffer (TextDisplay, sonst ein Rüstungsständer), Actionbar mit Treffer, Combo und restlicher Gesundheit, Kritik- und Sweep-Farben, Zusammenfassung nach Kill oder Tod. Global über `damage.enabled` und `presentation.yml`, je Spieler unter `/settings`. Schnelle Treffer teilen sich eine Zahl und verschwinden von allein.
- **Mehrere Server:** Optional und für automatisches Skalieren gedacht. Gleiches Jar, gleiche `config.yml`. `network.enabled: auto` schaltet den Cluster ein, sobald der Speicher MySQL oder MariaDB ist. Jede Instanz holt sich eine eigene Id (Umgebungsvariable, Pod-Name oder Zufall) und meldet sich per Heartbeat. YAML bleibt ein einzelner Server ohne weitere Schlüssel.
- **Aussehen:** Palette und Icons in `theme.yml`. Scoreboard, Tab, Bossbar, Actionbar, Titel, Kill-Effekte, Chat und Hologramme nutzen dieselben Tokens (`%color_primary%`, `%icon_map%`).
- **Scoreboard:** Zeilen aus `scoreboard.yml`. Lokale Tokens, `%bffa_*%`, PlaceholderAPI und MiniPlaceholders werden vor MiniMessage eingesetzt. Lange Verläufe bleiben auf Paper erhalten und werden auf Spigot auf das 64-Zeichen-Limit der Teams gekürzt.
- **Sprache:** MiniMessage in `lang/en.yml` und `lang/de.yml`. `language` in `config.yml`.
- **Integrationen:** Alle optional. Das Jar startet auch ohne sie. Siehe Tabelle unten.
- **Speicher:** YAML als Standard. SQLite ist enthalten. MySQL oder MariaDB über einen Treiber in `plugins/BFFA01/drivers/`.

## Befehle und Rechte

| Befehl | Beschreibung | Permission |
| :--- | :--- | :--- |
| `/bffa` | Admin: `setmap`, `swapmap`, `addcoins`, `removecoins`, `resetstats`, `reload`, `network`. | `bffa.admin` (oder der passende Unterknoten) |
| `/menu` | Hub für Shop, Kit, Karten, Stats und Einstellungen. | `bffa.menu` |
| `/kit` | Kit-Editor. | `bffa.kit` |
| `/savekit` | Aktuelle Hotbar speichern. | `bffa.savekit` |
| `/stats` | Eigene Stats und Rangliste. | `bffa.stats` |
| `/shop` | Münz-Shop. | `bffa.shop` |
| `/maps` | Nächste Karte wählen. | `bffa.maps` |
| `/settings` | Sidebar, Bossbar, Actionbar, Trefferzahlen und Kill-Effekt. | `bffa.settings` |

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
| `config.yml` | Sprache, Speicher, Schaden, Netzwerk, Hologramme, Integrationen, Kartenwechsel, Kit, Spawns. `config-version` bleibt `2`. |
| `theme.yml` | Palette (`primary`, `secondary`, `accent`, `success`, `danger`, `muted`, `soft`, `warm`) und Icons. |
| `menus.yml` | Slots, Materialien und Aktionen der Kistenmenüs. |
| `presentation.yml` | Tabliste, Bossbar, Actionbar, Titel, Sounds, Kill-Effekte, Trefferzahlen, Willkommen beim ersten Join. |
| `scoreboard.yml` | Titel und Zeilen der Sidebar. |
| `lang/en.yml`, `lang/de.yml` | MiniMessage oder `&`-Farbcodes. Platzhalter wie `%player%`. |
| `preferences.yml` | Sidebar, Bossbar, Actionbar, Trefferzahlen und Kill-Effekt je Spieler. Wird zur Laufzeit geschrieben, nicht im Jar mitgeliefert. Im Netzwerk gewinnt die SQL-Zeile beim Join. |
| `data.yml` | Standard-Statistik. Wird nicht geladen, wenn sie nicht lesbar ist, damit die Datei nicht überschrieben wird. |
| `plugins/BFFA01/drivers/` | Optionaler MySQL- (`com.mysql.cj.jdbc.Driver`) oder MariaDB-Treiber (`org.mariadb.jdbc.Driver`). SQLite braucht keinen. |

`/bffa reload` lädt Config, Sprache, Theme, Menüs, Präsentation, Scoreboard, Kartenliste, Integrationen und den Speicher neu. Der aktuelle Speicher wird zuerst geschrieben, danach öffnet das Plugin das in `storage` genannte Ziel. Schlägt das Öffnen fehl, bleibt der laufende Speicher. Ein Wechsel von `storage.type` kopiert keine Zeilen. Offene BuildFFA-Menüs werden geschlossen. Die Kartenrotation läuft weiter; nur das Intervall wird begrenzt (mindestens 10 Sekunden), und eine noch nie gestartete Rotation startet, wenn nach dem Reload Karten vorhanden sind.

### Schaden

`damage.enabled` schaltet die live Anzeige ein (Standard an). Die Statistik wird trotzdem geschrieben. `damage.assist-seconds` (Standard 12) ist das Fenster für Assists und die Todesaufstellung. `damage.assist-minimum` (Standard 2.0 Lebenspunkte) ist die Untergrenze für einen Assist. Gezählt wird der tatsächlich abgezogene Lebenspunkt plus verbrauchte Absorption. Schaden über die restliche Gesundheit hinaus zählt nicht. Fallschaden bleibt abgebrochen. Void unter der Karten-Todeshöhe geht an den letzten Angreifer im Fenster.

`presentation.yml` unter `damage` schaltet Indikator, Actionbar und Chat-Zusammenfassung einzeln. `ticks` ist die Lebensdauer, `merge-millis` fasst Treffer zusammen. `%icon_hit%` kommt aus `theme.yml`.

### Mehrere Server und Autoscaling

Ein Server mit YAML oder SQLite bleibt lokal. `network.enabled` hat drei Stellungen:

- `false` bleibt lokal, auch mit MySQL.
- `true` ist immer ein Cluster (MySQL sollte dann an sein).
- `auto` (Standard in der mitgelieferten Datei) ist an, wenn `storage.type` `mysql` oder `mariadb` ist.

Eine schon vorhandene `config.yml` mit `enabled: false` bleibt lokal, bis der Wert oder die Umgebung sie umstellt.

Identität kommt nicht aus der Config und nicht aus `server-id.txt`. Eine kopierte Datei würde sonst jede Replica auf dieselbe Id setzen. Die Reihenfolge ist `BFFA_SERVER_ID`, dann `POD_NAME` oder `HOSTNAME`, wenn der Name nicht allgemein ist (`paper`, `localhost`, `bffa`, …). Fehlen beide, gilt der Hostname des Betriebssystems, sonst eine Zufalls-Id pro Start. Ist die Wunsch-Id im Heartbeat noch lebendig, nimmt der Prozess eine neue. Ein hart beendeter Prozess (`SIGKILL`) fällt nach 15 Sekunden aus der Liste, seine Spieler-Leases auch.

Umgebungsvariablen liegen nur im Speicher über der Datei, das Image bleibt gleich:

| Variable | Wirkung |
| :--- | :--- |
| `BFFA_STORAGE_TYPE` | `yaml`, `sqlite`, `mysql` oder `mariadb` |
| `BFFA_MYSQL_HOST`, `BFFA_MYSQL_PORT`, `BFFA_MYSQL_DATABASE`, `BFFA_MYSQL_USERNAME`, `BFFA_MYSQL_PASSWORD`, `BFFA_MYSQL_PARAMETERS` | JDBC-Ziel |
| `BFFA_NETWORK` | `auto`, `true` oder `false` |
| `BFFA_SERVER_ID` | Feste Id dieser Replica |
| `BFFA_REDIS_HOST`, `BFFA_REDIS_PORT`, `BFFA_REDIS_PASSWORD`, `BFFA_REDIS_CHANNEL` | Optionales Redis |
| `BFFA_NETWORK_SECRET` | Gemeinsames HMAC-Geheimnis, nicht ins Image schreiben |
| `BFFA_LANGUAGE` | `en` oder `de` |

Schema-Anlage nimmt auf MySQL `GET_LOCK('bffa_schema')`. `CREATE TABLE IF NOT EXISTS` und `ALTER` sind wiederholbar, auch wenn viele Pods gleichzeitig starten. Kills, Tode, Münzen, Schaden und Assists werden addiert (`spalte = spalte + änderung`). Kit, Name, Streak und Anzeige-Einstellungen schreibt nur die Instanz mit der Lease. Die Lease läuft ab, wenn der Heartbeat der haltenden Instanz älter als 15 Sekunden ist, auch ohne sauberes Stoppen. Beim Join wird die Zeile neu gelesen, beim Quit geschrieben und freigegeben.

Discord, Kartenwechsel, Hologramme, Scoreboard und bStats bleiben an der Instanz, auf der das Ereignis passiert. Ein Kill erzeugt einen Webhook, nicht einen pro Replica. Streak-Nachrichten über Redis und den Plugin-Kanal `bffa:net` werden pro Prozess einmal angezeigt. bStats zählt jeden Prozess, weil jeder Prozess ein Server ist. Die Rangliste im Speicher wird lokal neu geladen.

Fällt MySQL aus, beim Start oder während der Prozess schon läuft, hält das Plugin die noch nicht geschriebenen Stats in `mysql-buffer.yml`. Die Datei bekommt eine `buffer-id`. Dieselbe Id wird in einer Transaktion mit den Zählern eingefügt. Ein Neustart zwischen Schreiben und Löschen addiert die Datei nicht ein zweites Mal. Sobald die Datenbank wieder antwortet, wird die Datei einmal übernommen.

Nachrichten zwischen Instanzen sind signiert. Ist `network.secret` oder `BFFA_NETWORK_SECRET` gesetzt, gilt dieser Wert auf diesem Prozess und wird in die gemeinsame Tabelle geschrieben, solange dort noch keiner steht. Sonst erzeugt die erste MySQL-Instanz ein Geheimnis unter `GET_LOCK('bffa_secret')` und die anderen lesen dieselbe Zeile. Unsignierte Frames auf Redis und `bffa:net` werden verworfen. `/bffa network` zeigt `signed` oder `open`, nie das Geheimnis, das Redis-Passwort oder eine Webhook-URL.

`/bffa network` zeigt die eigene Id, den Speicher, Redis und die lebenden Instanzen. Platzhalter: `%bffa_network_servers%`, `%bffa_network_players%`, `%bffa_network_id%`.

Der Proxy registriert die Backends selbst (Velocity oder BungeeCord, dynamisch). Dieses Plugin meldet sich nicht am Proxy an.

Beispiel, drei gleiche Container gegen eine MariaDB. `docker compose up --scale bffa=3` startet weitere Kopien.

```yaml
services:
  mariadb:
    image: mariadb:11
    environment:
      MARIADB_DATABASE: bffa
      MARIADB_USER: bffa
      MARIADB_PASSWORD: bffa
      MARIADB_ROOT_PASSWORD: root
  redis:
    image: redis:7
  bffa:
    image: itzg/minecraft-server
    environment:
      EULA: "TRUE"
      TYPE: PAPER
      MEMORY: 2G
      BFFA_STORAGE_TYPE: mysql
      BFFA_MYSQL_HOST: mariadb
      BFFA_MYSQL_DATABASE: bffa
      BFFA_MYSQL_USERNAME: bffa
      BFFA_MYSQL_PASSWORD: bffa
      BFFA_NETWORK: auto
      BFFA_REDIS_HOST: redis
    volumes:
      - ./BFFA01.jar:/plugins/BFFA01.jar:ro
```

Kubernetes: dasselbe Image, `replicas: 3`, dieselben Env-Einträge, `HOSTNAME` ist der Pod-Name. Eine TCP-Readiness auf Port 25565 wartet, bis der Server gebunden hat. Die Datenbank sollte vorher stehen (`connectTimeout` ist 3 Sekunden, der Start versucht es fünfmal). Der MariaDB-Treiber liegt im Image unter `plugins/BFFA01/drivers/`.

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

`%map%`, `%time%`, `%kills%`, `%deaths%`, `%kd%`, `%streak%`, `%coins%`, `%players%`, `%damage%`, `%damage_taken%`, `%assists%`.

Theme, überall dort, wo eine Zeile durch die Anzeige läuft, inklusive Sprachdatei und Hologramm:

`%color_primary%`, `%color_secondary%`, `%color_accent%`, `%color_success%`, `%color_danger%`, `%color_muted%`, `%color_soft%`, `%color_warm%`, sowie `%icon_map%`, `%icon_kills%`, `%icon_deaths%`, `%icon_coins%`, `%icon_streak%`, `%icon_time%`, `%icon_players%`, `%icon_shop%`, `%icon_kit%`, `%icon_stats%`, `%icon_settings%`, `%icon_vote%`, `%icon_hit%`.

PlaceholderAPI hängt die `%` um `bffa_<parameter>`:

`kills`, `deaths`, `kd`, `coins`, `streak`, `rank`, `map`, `kit`, `prefix`, `bedrock`, `protocol`, `client`, `vault`, `online`, `damage`, `damage_taken`, `life_damage`, `life_taken`, `assists`, `network_servers`, `network_players`, `network_id`, `kills_<name>`, `deaths_<name>`, `kd_<name>`, `coins_<name>`, `streak_<name>`, `rank_<name>`, `damage_<name>`, `damage_taken_<name>`, `top_name_<n>`, `top_kills_<n>`, `top_kd_<n>`, `top_damage_<n>`, `top_damage_name_<n>`.

MiniPlaceholders nutzt dieselben Namen: `<bffa_kills>`, `<bffa_map>`, `<bffa_top_name:1>`.

Eingefügte Werte (Namen, Karte, Kit, Client) können keine MiniMessage-Tags öffnen. Die Rangliste wird zwischengespeichert und erst bei Kill, Reset, Namensänderung oder erfolgreichem Speicher-Reconnect neu sortiert.

## Entwickler-API

Andere Plugins holen `infinode.org.example.bFFA01.api.BuildFFA` über `BuildFFAProvider.get()` oder den Bukkit-Service-Manager. Das Interface liefert aktuelle Karte und Spawn, Kills, Tode, Münzen, K/D, Streak, verursachten und erhaltenen Schaden, Assists, Rang, Kit-Name, Bedrock-Erkennung, ViaVersion-Protokoll, Prefix und die aktiven Integrations-IDs.

Diese Events sind nicht abbrechbar. Die Stats sind dann schon geschrieben:

- `BuildFFAKillEvent` (`getVictim()` ist null, wenn das Opfer schon offline ist; Name, Schaden und Assists sind Schnappschüsse)
- `BuildFFADeathEvent` (Killer-Name und Schadensaufstellung, ohne den Killer-Thread zu brauchen)
- `BuildFFAStreakEvent` (auch eine gebrochene Streak)
- `BuildFFAMapChangeEvent`
- `BuildFFAPurchaseEvent`
- `BuildFFAReloadEvent` (`isStorageReconnected()` sagt, ob der Speicherwechsel geklappt hat)

## Damage and autoscaling (English)

Damage dealt and taken is stored in hundredths of a health point, for the whole lifetime and for the current life. A hit counts health actually removed plus absorption consumed. Overkill is dropped. Void below the map death height is credited to the last attacker inside `damage.assist-seconds` (default 12). Assists are other players in that window who dealt at least `damage.assist-minimum` (default 2.0).

Permanent shop upgrades ask for a second click before coins are spent. Consumables stay one click. Set `shop-confirm-permanent: false` in `menus.yml` to buy them in one click. Live numbers use a TextDisplay when the server has one, otherwise a marker armor stand tagged `bffa_damage`. They expire, merge hits inside `damage.merge-millis`, and stay capped at four per victim. The action bar shows the hit, the running total on that target, and remaining health. Crits and sweep attacks use their own theme lines. A short summary is sent after a kill or death. Turn the whole display off with `damage.enabled: false`, individual channels under `presentation.yml` `damage:`, or per player in `/settings`.

One YAML or SQLite server needs no network settings. `network.enabled: auto` (the shipped default) turns the cluster on when `storage.type` is `mysql` or `mariadb`. An existing file with `enabled: false` stays local. Copies of one image share the file; environment variables override it in memory:

`BFFA_STORAGE_TYPE`, `BFFA_MYSQL_HOST`, `BFFA_MYSQL_PORT`, `BFFA_MYSQL_DATABASE`, `BFFA_MYSQL_USERNAME`, `BFFA_MYSQL_PASSWORD`, `BFFA_MYSQL_PARAMETERS`, `BFFA_NETWORK` (`auto`, `true`, `false`), `BFFA_SERVER_ID`, `BFFA_NETWORK_SECRET`, `BFFA_REDIS_HOST`, `BFFA_REDIS_PORT`, `BFFA_REDIS_PASSWORD`, `BFFA_REDIS_CHANNEL`, `BFFA_LANGUAGE`.

Instance ids are not read from `network.server-id` or `server-id.txt`. The process uses `BFFA_SERVER_ID`, then `POD_NAME` or `HOSTNAME` when that name is specific. If neither variable is set, it uses the operating-system hostname. Otherwise it uses a random id for this boot. If that id is already heartbeating, the process takes a new one. A killed process drops out of `bffa_instance` after 15 seconds, and its player leases can be taken. Schema setup uses `GET_LOCK('bffa_schema')` and idempotent DDL. Kills, deaths, coins, damage, and assists are added with `column = column + delta`. Kit, name, streak, and display settings are written only by the lease holder.

Discord, map rotation, holograms, the sidebar, and bStats stay on the instance where the event happened, so one kill does not fan out to one webhook per replica. bStats still reports every process, because each process is a server. Streak lines published on Redis or plugin channel `bffa:net` are shown once per process. The proxy registers backends itself; this plugin does not talk to Velocity or BungeeCord.

Frames carry an HMAC. `network.secret` or `BFFA_NETWORK_SECRET` overrides the shared secret on that process and is stored when the shared row is still empty. Otherwise the first MySQL instance generates one secret under `GET_LOCK('bffa_secret')` and the others read that row. Unsigned Redis and `bffa:net` frames are ignored. `/bffa network` shows `signed` or `open`. It does not print the secret, the Redis password, or a webhook URL.

If MySQL is down at startup, or dies while the process is already running, stats that are not in the database yet are kept in `mysql-buffer.yml`. The file stores a `buffer-id`, and that id is inserted in the same transaction as the counters. A restart after the commit and before the file is deleted does not add the counters again. When the database accepts connections, the file is applied once. `/bffa network` lists live instances. Placeholders: `%bffa_network_servers%`, `%bffa_network_players%`, `%bffa_network_id%`.

Scale the same container with `docker compose up --scale bffa=3` or a Deployment of `replicas: 3`. Point every replica at one MariaDB (and optional Redis) with the variables above. Use a TCP readiness probe on `25565`. Put the MariaDB driver in `plugins/BFFA01/drivers/`. The database should be ready first; the plugin retries the first connection five times.

## Bauen

`./mvnw` lädt Maven 3.9.9 und prüft die Distributions-Prüfsumme. Maven älter als 3.9 ignoriert vertrauenswürdige Prüfsummen und bricht den Build nicht ab, deshalb lehnt der Enforcer alles unter 3.9.0 ab.

Das Compiler-Ziel ist Java 17. Ein JDK 17 oder neuer kann bauen.

```bash
./mvnw verify
```

Das Plugin-Jar ist `target/BFFA01-<version>.jar`. Die Version steht in `pom.xml` (aktuell 3.8). `target/` ist gitignored. Adventure wird geshadet und verschoben. SQLite-JDBC liegt neben den Plugin-Klassen. PlaceholderAPI-Stubs werden nicht ins Jar gepackt.

Die Paper-API-Abhängigkeit ist der feste Build `1.20.4-R0.1-20241030.192207-176`. Vertrauenswürdige Maven-Resolver-Prüfsummen in `.mvn/` decken jedes aufgelöste Artefakt ab, und der Build prüft die SHA-512 dieses Jars.

Zum Veröffentlichen ein GitHub-Release anlegen und Jar plus Prüfsumme anhängen:

```bash
( cd target && sha256sum BFFA01-*.jar ) | tee BFFA01-<version>.jar.sha256
```

Aus `target/` hashen, damit die Prüfsummendatei nur den Basisnamen enthält (`BFFA01-<version>.jar`), nicht `target/...`. `sha256sum -c` klappt dann, wenn beide Dateien im selben Verzeichnis liegen. `target/BFFA01-<version>.jar` und `BFFA01-<version>.jar.sha256` als Release-Assets hochladen. Keine der beiden Dateien committen.

## Voraussetzungen

- Ein Spigot-, Paper-, Purpur- oder Folia-Server für Minecraft Java Edition **1.20** oder neuer.
- Java 17 oder neuer zum Bauen. Die Server-JVM muss zur Minecraft-Version passen.
