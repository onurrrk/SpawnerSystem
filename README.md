# 📎 Spawner System

An advanced **multi-mode spawner breaking and placing system** for Minecraft servers.  
Includes detailed logging, customizable settings, and an advanced pickaxe system.

## 🚀 Features

- **2 Different System Modes**  
  - **classic:** Only Silk Touch enchanted pickaxes can break spawners.  
  - **advanced:** Only a special pickaxe given by administrators with limited uses can break spawners.

- **Special Spawner Pickaxe**  
  - Given via admin command with a limited number of uses.  
  - Automatically breaks when its usage count reaches zero.  
  - Can only be used in **advanced** mode.  

- **Spawner Giving System**  
  - Admins can give players spawners of any mob type.  
  - Spawner name and lore are fetched from the config, and all messages are read from the config.  
  - Players are notified when they receive a spawner, and if their inventory is full, the spawner is dropped on the ground.

- **JSON Logging System**  
  - Can be enabled or disabled via config.  
  - Logs all spawner breaking and placing actions in detail under `/plugins/SpawnerSystem/logs`.

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
