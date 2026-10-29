# 📎 Spawner System
An advanced **multi-mode spawner breaking and placing system** for Minecraft servers.
Includes spawner storage, stacking, item spawners, boosters, detailed logging, customizable settings and an advanced pickaxe system.
## 🌐 Multi-Language Support
Spawner System supports **28 different languages** to make it easier for servers around the world to use and configure:
**🇺🇸 English (en) | 🇹🇷 Turkish (tr) | 🇩🇪 German (de) | 🇪🇸 Spanish (es) | 🇷🇺 Russian (ru) | 🇨🇳 Chinese (zh) | 🇯🇵 Japanese (ja) | 🇦🇿 Azerbaijani (az) | 🇫🇷 French (fr) | 🇸🇦 Arabic (ar) | 🇳🇱 Dutch (nl) | 🇮🇩 Indonesian (id) | 🇦🇲 Armenian (hy) | 🇮🇹 Italian (it) | 🏴󠁧󠁢󠁳󠁣󠁴󠁿 Scottish Gaelic (gd) | 🇸🇪 Swedish (sv) | 🇰🇬 Kyrgyz (ky) | 🇰🇷 Korean (ko) | 🇭🇺 Hungarian (hu) | 🇨🇿 Czech (cs) | 🇬🇷 Greek (el) | 🇮🇷 Persian (fa) | 🇵🇱 Polish (pl) | 🇷🇴 Romanian (ro) | 🇻🇳 Vietnamese (vi) | 🇵🇹 Portuguese (pt) | 🇹🇭 Thai (th) | 🇺🇦 Ukrainian (uk)**
The language can be easily selected from the **config file**, and all translation files are located in: **/plugins/SpawnerSystem/languages**
## 🚀 Features
- **2 Different System Modes**
  - **classic:** Only Silk Touch enchanted pickaxes can break spawners.
  - **advanced:** Only a special pickaxe given by administrators with limited uses can break spawners.
- **Special Spawner Pickaxe**
  - Given via admin command with a limited number of uses.
  - Automatically breaks when its usage count reaches zero.
  - Can only be used in **advanced** mode.



![pickaxe](https://cdn.modrinth.com/data/cached_images/9ce47519a296eb7022fb8b55a9f3a418f80309b7.jpeg)


- **Spawner Giving System**
  - Admins can give players mob spawners of any mob type, and **item spawners** of any configured item.
  - Spawner name and lore are fetched from the config, and all messages are read from the language files.
  - Players are notified when they receive a spawner, and if their inventory is full, the spawner is dropped on the ground.



![spawner](https://cdn.modrinth.com/data/cached_images/33928c1ee18f444cac92b7f477ba18c657df399e.jpeg)


- **Spawner Menu System**
  - Can be enabled or disabled with `spawner-menu` (**enabled by default**). When disabled, the whole menu system is off and spawners work like normal spawners.
  - Opens the **Spawner Control Panel** with **Shift + Right-Click** on any spawner.
  - **Two menu types** (`spawner-menu-type`):
    - **V1 (default):** Permission based. Players switch Auto Slayer on and off themselves (`spawner.menu` permission, LuckPerms supported).
    - **V2:** Fully automatic. Auto Slayer, Storage and XP Vacuum are always on, and no permissions are needed.
  - **Auto Slayer:** The spawner does not spawn real mobs. It kills them instantly, gives the loot from `loot.yml` and ignores spawn conditions (light, time of day, free space).
  - **Spawner Storage:** Loot is collected inside the spawner instead of dropping on the ground. Paged storage (up to 100 pages, 45 slots per page).
  - **XP Vacuum:** XP from the kills is stored in the spawner and can be withdrawn from the menu.
  - Only one player can use a spawner's menu or storage at a time.
  - Optionally drop the stored items and XP when the spawner is broken.



![menu](https://cdn.modrinth.com/data/cached_images/a84dc9ee7e4cec24c5d1880d8705d9679f96b98f.jpeg)




![menu](https://cdn.modrinth.com/data/cached_images/8bcdc744aa82ce7d8c4a219b6adc1d6e4d98705c.jpeg)


- **Spawner Stacking**
  - Merge spawners of the same type into a single block and get the loot and XP of every spawner inside it.
  - Configurable maximum stack size (or unlimited).
  - Breaking gives 1 spawner, or all except the last one while sneaking.
  - Merged spawners count with their stack size for the chunk spawner limit.
- **Item Spawners**
  - Spawners that produce items instead of mobs.
  - Items, amounts and XP are fully configurable in `spawner_items.yml`.
- **Spawner Boosters**
  - Booster tickets increase the activation range, reduce the spawn delay and add extra XP.
  - Optional booster stacking, a per-spawner booster menu for merged spawners, a minimum delay limit and tickets that return when the spawner is broken.
- **Spawner Break Chance**
  - Control the chance to get the spawner back with permissions: `spawnersystem.chance.<number>` (for example `spawnersystem.chance.50`).
- **Fully Configurable Loot**
  - All mob drops and XP rewards are configured in `loot.yml`.
- **JSON Logging System**
  - Can be enabled or disabled via config.
  - Logs all spawner breaking and placing actions in detail under `/plugins/SpawnerSystem/logs`
  - Choose the time format of the logs (`auto`, `12h`, `24h`).



![logs](https://cdn.modrinth.com/data/cached_images/d9970ba9dbb7f14b1a80c55b2f96284fe8ed0b03_0.webp)




![logs](https://cdn.modrinth.com/data/cached_images/b4fbdde64c81563336bfb4018c9930a1c1a119a4.jpeg)


- **Fully Customizable Config**
  - System mode, special pickaxe name/lore, and all messages can be edited in the config and language files.
  - **Natural spawner breaking** can be toggled on or off, and can require the `spawner.break.natural` permission.
  - **Empty spawner breaking system** can be enabled or disabled.
  - Update checker and bStats can be turned off.
- **Hologram Mode**
  - Can be enabled or disabled via the config.
  - Displays visual indicators above each spawner (mob and item spawners) and shows its active status.



![spholo](https://cdn.modrinth.com/data/cached_images/4235cddea73f7ef543139e7dee6f52e140e736c6.jpeg)


- **Chunk-Based Limits**
  - Fully configurable via the config file.
  - Limit the number of spawners per chunk, with separate limits for mob spawners and item spawners, an optional limit per mob type, and `-1` for unlimited.
  - Limit the number of mobs per chunk, counted per chunk or per spawner (`limit-mode`).
  - **Chunk Mob Culling:** Mobs above the limit are warned and then removed. Leashed, named and tamed mobs can be protected, and `"ALL"` applies the system to every mob type.
- **Nerf Spawner Mobs**
  - When **nerf-spawner-mobs: true**, mobs spawned from spawners cannot move, target, deal damage, pick up items, and Creepers cannot explode.



![mobholo](https://cdn.modrinth.com/data/cached_images/ed1fa22c5caaa6e4e15abacb8547fd3ea5280081.jpeg)


- **Spawner Protection**
  - Spawners are protected from entity explosions, block explosions (beds, TNT) and the Wither.
- **Automatic Backups & Config Updates**
  - A zip backup of the plugin folder is created when the plugin version changes.
  - New config, loot and language entries are added automatically without touching your values.
- **Discord Webhook Integration**
  - **Detailed Logging:** Automatically tracks all spawner breaking and placing events and sends them to your configured Discord channel.
  - **Platform Detection:** Automatically detects and reports whether an action was performed by a **Java** player or a **Bedrock** (Geyser/Floodgate) user.
## ⚡ Commands

| Command | Permission | Description | Aliases Usage |
|---------|------------|-------------|---------------|
| `/spsystem reload` | spawner.admin | Reloads the config file | `/sps reload` |
| `/spsystem pickaxegive <player> <uses>` | spawner.admin | Gives a special spawner pickaxe to a player (only in advanced mode) | `/sps pickaxegive <player> <uses>` |
| `/spsystem givespawner <player> <mob\|item> <type> [amount]` | spawner.admin | Gives a mob spawner or an item spawner to a player | `/sps givespawner <player> <mob\|item> <type> [amount]` |
| `/spsystem givebooster <player> <range> <delay-seconds> <xp-percent> <minutes\|unlimited> [amount]` | spawner.admin | Gives spawner booster tickets to a player | `/sps givebooster <player> <range> <delay-seconds> <xp-percent> <minutes\|unlimited> [amount]` |
## 🔑 Permissions

| Permission | Default | Description |
|------------|---------|-------------|
| `spawner.admin` | op | All `/spsystem` subcommands, the spawner menu, and always get the spawner back when breaking it |
| `spawner.menu` | op | Allows switching Auto Slayer in the menu (menu type V1 only) |
| `spawner.break.natural` | op | Allows breaking natural spawners when `natural-spawner-break-permission-required` is true |
| `spawnersystem.chance.<number>` | false | Chance (0-100) to get the spawner back when breaking it. `spawnersystem.chance.*` = 100% |
## 🔌 Optional Dependencies
- **LuckPerms:** Permission check of Auto Slayer managers in menu type V1.
- **Floodgate / Geyser-Spigot:** Bedrock player detection in logs and webhooks.
## ⚙️ Supported Forks

| Fork / Build | Support Status  |
|--------------|----------------|
| ✅ Paper     | Fully Supported |
| ✅ Purpur    | Fully Supported |
| ✅ Folia     | Fully Supported |
| ✅ Spigot    | Fully Supported |
| ✅ Bukkit    | Fully Supported |
## Statistics

[![bStats](https://bstats.org/signatures/bukkit/spawnersystem.svg)
](https://bstats.org/plugin/bukkit/spawnersystem)

## Discord

[![Discord](https://cdn.modrinth.com/data/cached_images/4de86371cc7bcf3818924b198f31baacc304700f.png)](https://discord.gg/kj5grZuxyQ)
