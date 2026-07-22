# Apollo SMP — Economy Core

A self-contained, heavily-branded economy plugin for **Purpur / Paper 1.21.x**, in the
style of DonutSMP. No Vault, no database, no external dependencies — drop the jar in and go.

Money · `/sell` server shop · player **auction house** · **buy orders** · homes with a GUI ·
random teleport · a limited world border · and a branded sidebar scoreboard.

---

## Building the jar

This project compiles to a single file: `target/ApolloSMP.jar`.

You need **JDK 21** and **Maven** installed. Then, from this folder:

```bash
mvn package
```

That's it. The finished plugin is at `target/ApolloSMP.jar`. Drop it in your server's
`plugins/` folder and restart.

> **Minecraft version:** the build targets Paper API `1.21.4-R0.1-SNAPSHOT` and uses **no
> NMS**, so the jar runs on any 1.21.x Purpur/Paper build. If you're on a different 1.21.x
> patch and want an exact match, edit the `<paper.version>` property near the top of
> `pom.xml` and rebuild.

No Maven installed? Install a JDK 21 and Maven from your OS package manager
(e.g. `apt install openjdk-21-jdk maven`), or open the folder in IntelliJ IDEA, which will
build it for you (Maven tool window → Lifecycle → package).

---

## Features & commands

### Economy
| Command | Description |
|---|---|
| `/balance [player]` | Check your (or another player's) balance. Aliases: `/bal`, `/money`, `/wallet` |
| `/pay <player> <amount>` | Send money to another player (optional tax in config) |
| `/baltop` | View the richest players. Aliases: `/rich` |
| `/eco <give\|take\|set> <player> <amount>` | Admin balance management |

Amounts accept suffixes: `1k`, `1.5m`, `2b`.

### Sell to server (`/sell`)
- `/sell` — opens a menu listing every sellable item and its price.
- `/sell hand` — sells the stack you're holding.
- `/sell all` — sells every sellable item in your inventory (named/enchanted gear is skipped).

Prices are fully configurable under `sell.prices` in `config.yml`.

### Auction house (`/ah`)
- `/ah` — browse and buy other players' listings.
- `/ah sell <price>` — list the item in your hand for sale.
- `/ah mine` — manage your own listings (cancel to reclaim).

Configurable listing limit, duration, listing tax, and price bounds. Expired or cancelled
listings return to your **Collection Box** (open `/menu`).

### Buy orders (`/orders`)
- `/orders` — browse open buy orders and sell items you have to fill them.
- `/orders create <price-per-item> [amount]` — hold an example item, request that many at
  your price. The cost is **escrowed up front**, so orders fill even while you're offline.
- `/orders mine` — cancel your orders (unfilled funds are refunded).

Filled items land in the buyer's Collection Box.

### Homes
| Command | Description |
|---|---|
| `/sethome [name]` | Set a home (defaults to `home`) |
| `/home [name]` | Teleport home (opens the menu if you have several) |
| `/homes` | Open the homes GUI |
| `/delhome <name>` | Delete a home |

Home limits are permission-based (see below). Teleports have a configurable warmup that
cancels if you move, and an optional cost.

### Getting around
| Command | Description |
|---|---|
| `/rtp` | Random teleport into the wild, inside the border. Aliases: `/wild` |
| `/spawn` | Teleport to spawn |
| `/setspawn` | Set the server spawn (admin) |
| `/menu` | Open the branded main hub |

New players get a random spawn on first join (toggle in config).

### Admin
- `/apollo reload` — reload `config.yml`, sell prices, world borders and scoreboards live.
- `/apollo version` — show the plugin version.

---

## Permissions

| Permission | Default | Grants |
|---|---|---|
| `apollo.admin` | op | `/eco`, `/setspawn`, `/apollo` |
| `apollo.homes.5` | — | 5 homes |
| `apollo.homes.10` | — | 10 homes |
| `apollo.homes.unlimited` | — | effectively unlimited homes |

Home limits: the plugin grants the **highest** limit a player qualifies for, falling back
to `homes.default-limit`. Add your own tiers under `homes.limits` in `config.yml` and grant
the matching permission node through your rank/LuckPerms setup.

---

## Configuration highlights (`config.yml`)

- **branding** — server name, chat prefix, and the gold→red accent colours reused everywhere.
  All text uses [MiniMessage](https://docs.advntr.dev/minimessage/format.html) formatting.
- **economy** — currency symbol, starting balance, pay tax.
- **sell.prices** — the server buy-back price list.
- **auction-house / orders** — limits, taxes, and price bounds.
- **homes** — default limit, per-rank limits, warmup, teleport cost.
- **rtp** — world, radius, blocks to avoid, cooldown, cost, first-join spawn.
- **world-border** — per-world diameters and warning distance.
- **scoreboard** — the sidebar title and lines, with placeholders like `%balance%`,
  `%homes%`, `%listings%`, `%online%`.

Data is stored as flat YAML files in `plugins/ApolloSMP/` (`economy.yml`, `homes.yml`,
`auctions.yml`, `orders.yml`, `mailbox.yml`, `spawn.yml`) and auto-saves on a timer plus on
shutdown.

---

## Notes & tips

- The plugin is intentionally dependency-free. If you'd like it to also register as a Vault
  economy provider so other plugins can read balances, that's a small addition — just ask.
- Price input for `/ah` and `/orders` is command-driven (rather than chat/anvil prompts) for
  reliability. If you'd prefer a click-to-type flow, it can be added.
- Everything branded ("Apollo SMP", colours, sidebar) lives in `config.yml` — rebrand without
  touching code.
