# 📎 Spawner System

An advanced **multi-mode spawner breaking and placing system** for Minecraft servers.  
Includes detailed logging, customizable settings, and an advanced pickaxe system.

## 🌐 Multi-Language Support
Spawner System now supports **28 different languages** to make it easier for servers around the world to use and configure:  

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
  - Admins can give players spawners of any mob type.  
  - Spawner name and lore are fetched from the config, and all messages are read from the config.  
  - Players are notified when they receive a spawner, and if their inventory is full, the spawner is dropped on the ground.

![spawner](https://cdn.modrinth.com/data/cached_images/33928c1ee18f444cac92b7f477ba18c657df399e.jpeg)

- **JSON Logging System**  
  - Can be enabled or disabled via config.  
  - Logs all spawner breaking and placing actions in detail under `/plugins/SpawnerSystem/logs`.

![logs](https://cdn.modrinth.com/data/cached_images/d9970ba9dbb7f14b1a80c55b2f96284fe8ed0b03_0.webp)

![logs](https://cdn.modrinth.com/data/cached_images/b4fbdde64c81563336bfb4018c9930a1c1a119a4.jpeg)

- **Fully Customizable Config**  
  - Prefix, system mode, special pickaxe name/lore, and all messages can be edited in the config file.  
  - **Natural spawner breaking** can be toggled on or off.  
  - **Empty spawner breaking system** can be enabled or disabled, and the dropped spawner type is determined by the config.  
  - All messages are fetched from the config for easy translation or text change.
- **Hologram Mode**  
  - Can be enabled or disabled via the config (**ProtocolLib** required **V1.7.2˅**).  
  - Displays visual indicators above each spawner and shows its active status.  

![spholo](https://cdn.modrinth.com/data/cached_images/4235cddea73f7ef543139e7dee6f52e140e736c6.jpeg)

- **Chunk-Based Limits**  
  - Fully configurable via the config file.  
  - Controls the maximum number of spawners and mobs per chunk.
  - Customizable mob settings; any mobs exceeding the defined limits will be automatically removed.
- **Nerf Spawner Mobs**
  - When **nerf-spawner-mobs: true**, mobs spawned from spawners cannot move, target, deal damage, pick up items, and Creepers cannot explode.

![mobholo](https://cdn.modrinth.com/data/cached_images/ed1fa22c5caaa6e4e15abacb8547fd3ea5280081.jpeg)

- **Spawner Menu System**
  - Opens the advanced GUI by **Shift + Right-Clicking** on any spawner, allowing you to manage all settings from a single, intuitive interface.
  - **Auto-Kill:** Automatically eliminates mobs spawned by the spawner.
  - **XP Collection:** Automatically collects and stores experience points dropped by mobs within the spawner.
  - **Permissions:** Manages access to the menu via the `spawner.menu` permission node.

![menu](https://cdn.modrinth.com/data/cached_images/a84dc9ee7e4cec24c5d1880d8705d9679f96b98f.jpeg)

![menu](https://cdn.modrinth.com/data/cached_images/8bcdc744aa82ce7d8c4a219b6adc1d6e4d98705c.jpeg)

- **Discord Webhook Integration**
  - **Detailed Logging:** Automatically tracks all spawner breaking and placing events and sends them to your configured Discord channel.
  - **Platform Detection:** Automatically detects and reports in the logs whether an action was performed by a **Java** player or a **Bedrock** (Geyser/Floodgate) user.

## ⚡ Commands

| Command | Permission | Description | Aliases Usage |
|---------|------------|-------------|---------------|
| `/spsystem reload` | spawner.admin | Reloads the config file | `/sps reload` |
| `/spsystem pickaxegive <player> <uses>` | spawner.admin | Gives a special spawner pickaxe to a player (only in advanced mode) | `/sps pickaxegive <player> <uses>` |
| `/spsystem givespawner <player> <mob>` | spawner.admin | Gives a spawner of a specific mob type to a player | `/sps givespawner <player> <mob>` |

## ⚙️ Supported Forks

| Fork / Build | Support Status  |
|--------------|----------------|
| ✅ Paper     | Fully Supported |
| ✅ Purpur    | Fully Supported |
| ✅ Folia     | Fully Supported |
| ⚠️ Spigot    | Partial Support |
| ⚠️ Bukkit    | Partial Support |

[![Discord](https://cdn.modrinth.com/data/cached_images/4de86371cc7bcf3818924b198f31baacc304700f.png)
](https://discord.gg/H7RMcAMFeZ)
