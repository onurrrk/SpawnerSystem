# 📎 Spawner System

An advanced **multi-mode spawner breaking and placing system** for Minecraft servers.  
Includes detailed logging, customizable settings, and an advanced pickaxe system.

## 🌐 Multi-Language Support (v1.6^)
Spawner System now supports **21 different languages** to make it easier for servers around the world to use and configure:  

🇺🇸 **English (en)** | 🇹🇷 **Turkish (tr)** | 🇩🇪 **German (de)** | 🇪🇸 **Spanish (es)** | 🇷🇺 **Russian (ru)** | 🇨🇳 **Chinese (zh)** | 🇯🇵 **Japanese (ja)** | 🇦🇿 **Azerbaijani (az)** | 🇫🇷 **French (fr)** | 🇸🇦 **Arabic (ar)** | 🇳🇱 **Dutch (nl)** | 🇮🇩 **Indonesian (id)** | 🇦🇲 **Armenian (hy)** | 🇮🇹 **Italian (it)** | 🇨🇿 **Czech (cs)** | 🇬🇷 **Greek (el)** | 🏴󠁧󠁢󠁳󠁣󠁴󠁿 **Scottish Gaelic (gd)** | 🇭🇺 **Hungarian (hu)** | 🇰🇬 **Kyrgyz (ky)** | 🇰🇷 **Korean (ko)** | 🇸🇪 **Swedish (sv)**  

The language can be easily selected from the **config file**, and all translation files are located in: **/plugins/SpawnerSystem/languages**


## 🚀 Features

- **2 Different System Modes**  
  - **classic:** Only Silk Touch enchanted pickaxes can break spawners.  
  - **advanced:** Only a special pickaxe given by administrators with limited uses can break spawners.

- **Special Spawner Pickaxe**  
  - Given via admin command with a limited number of uses.  
  - Automatically breaks when its usage count reaches zero.  
  - Can only be used in **advanced** mode.  

![Advanced mode](https://cdn.modrinth.com/data/cached_images/5442b6efd1438ede26b5cf8ddbcb2eeaf1ac6852_0.webp)

- **Spawner Giving System**  
  - Admins can give players spawners of any mob type.  
  - Spawner name and lore are fetched from the config, and all messages are read from the config.  
  - Players are notified when they receive a spawner, and if their inventory is full, the spawner is dropped on the ground.

![Classic mode](https://cdn.modrinth.com/data/cached_images/cef657b0c807bd23c0ffe3eadb8cb572264d991d_0.webp)
![Advanced mode](https://cdn.modrinth.com/data/cached_images/ef14ae1a24e0559bc686519dfb16a16023a12e0b_0.webp)

- **JSON Logging System**  
  - Can be enabled or disabled via config.  
  - Logs all spawner breaking and placing actions in detail under `/plugins/SpawnerSystem/logs`.

![logs](https://cdn.modrinth.com/data/cached_images/d9970ba9dbb7f14b1a80c55b2f96284fe8ed0b03_0.webp)
![logs](https://cdn.modrinth.com/data/cached_images/a3d3881181aa67e157e6ffbb8d51bf06a769fd2b_0.webp)

- **Fully Customizable Config**  
  - Prefix, system mode, special pickaxe name/lore, and all messages can be edited in the config file.  
  - **Natural spawner breaking** can be toggled on or off.  
  - **Empty spawner breaking system** can be enabled or disabled, and the dropped spawner type is determined by the config.  
  - All messages are fetched from the config for easy translation or text change.

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
| ✅ Spigot    | Fully Supported |
| ✅ Folia     | Fully Supported |
| ⚠️ Bukkit    | Partial Support |

[![Discord](https://cdn.modrinth.com/data/cached_images/4de86371cc7bcf3818924b198f31baacc304700f.png)
](https://discord.gg/H7RMcAMFeZ)
