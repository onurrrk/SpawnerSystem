# 📎 Spawner System

An advanced **multi-mode spawner breaking and placing system** for Minecraft servers.  
Comes with detailed logging, customizable settings, and an advanced pickaxe system.


## 🚀 Features

- **2 Different System Modes**
  - **classic:** Only Silk Touch enchanted pickaxes can break spawners.
  - **advanced:** Only a special pickaxe given by administrators with limited uses can break spawners.

- **Special Spawner Pickaxe**
  - Given via admin command with a limited number of uses.
  - Automatically breaks when its usage count reaches zero.
  - Can only be used in **advanced** mode.
  - Can be set as unbreakable (no durability loss).

- **JSON Logging System**
  - Can be enabled or disabled through the config.
  - Logs all spawner breaking and placing actions in detail inside the `/plugins/SpawnerSystem/logs` folder.

- **Fully Customizable Config**
  - Prefix, system mode, special pickaxe name/lore, and all messages can be edited via the config file.
  - **Natural spawner breaking permission** can be toggled on or off.


## ⚙️ Supported Forks

| Fork / Build | Support Status  |
|--------------|-----------------|
| ✅ Paper     | Fully Supported |
| ✅ Purpur    | Fully Supported |
| ✅ Spigot    | Fully Supported |
| ✅ Folia     | Fully Supported |
| ⚠️ Bukkit    | Partially Supported |


## ⚙️ Configuration

```yaml
# -------------------------------------------------
#            Spawner System Settings
# -------------------------------------------------

prefix: "&8[&aSpawner&8] &r"

# Choose the plugin operation mode:
# system: "classic"
#   - Spawners can be broken only with Silk Touch enchanted pickaxes.
#   - Natural spawners can be broken if natural-spawner-break is true.
#
# system: "advanced"
#   - Spawners can be broken only with the special "SP Pickaxe" obtained via /spsystem pickaxegive.
#   - Natural spawners can be broken if natural-spawner-break is true.

system: "advanced"

natural-spawner-break: true

logs-enabled: true

# Special pickaxe settings used in "advanced" mode
spawner-pickaxe-item:
  name: "&b&lSpawner Pickaxe"
  lore:
    - "&7Use this pickaxe to break spawners."
    - "&e"
    - "&aUses left: &c%uses%"

messages:
  no-permission: "&cYou don't have permission to use this command."
  reload-success: "&aSpawner system configuration reloaded successfully."
  wrong-subcommand: "&aUsage: /spsystem <reload|pickaxegive>"
  wrong-pickaxe-usage: "&aUsage: /spsystem pickaxegive <player> <1-100>"
  command-disabled: "&cThe 'pickaxegive' command is only available in 'advanced' mode."
  player-not-found: "&cPlayer not found: %player%"
  not-a-number: "&cUsage amount must be a number."
  max-uses-limit: "&cMaximum usage allowed is 100."
  pickaxe-given-sender: "&aGiven %uses% uses SP Pickaxe to %player%."
  pickaxe-given-recipient: "&bYou have received a special spawner pickaxe!"
  spawner-collected: "&aSpawner successfully added to your inventory!"
  pickaxe-broken: "&aYour spawner pickaxe broke!"
  natural-spawner-break-denied: "&cNatural spawners are protected and cannot be broken."
  sp-pickaxe-only: "&cThis special pickaxe can only be used to break spawners!"
  inventory-full: "&aYour inventory was full, so the spawner was dropped on the ground!"
  classic-silk-required: "&cYou can only break spawners with a Silk Touch enchanted pickaxe!"
  advanced-pickaxe-required: "&cYou can only break spawners with the special SP Pickaxe!"
