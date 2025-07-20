# 📎 Spawner System

An advanced **multi-mode spawner breaking and placing system** for Minecraft servers. It comes with detailed logging, Towny integration, and a special pickaxe system.

## 🚀 Features

- **3 Different System Modes:**
  - **classic:** Only Silk Touch enchanted pickaxes can break spawners.
  - **advanced:** Only a special pickaxe given by admins with limited uses can break spawners.
  - **towny:** Spawners inside Towny towns can only be broken by authorized members; natural spawners are protected.

- **Special Spawner Pickaxe:**
  - Given by admin command with limited uses.
  - Breaks when usage count runs out.

- **JSON Logging:**
  - All spawner break and place actions are logged in detail at `/plugins/Spawner/sp.json`.

- **Fully Customizable Config:**
  - Prefix, system mode, special pickaxe name/lore, and all messages can be edited in the config file.

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
# ------------------------------------------------- #
#           Spawner System Ayarları                 #
# ------------------------------------------------- #

# Plugin mesajlarının başına eklenecek ön ek.
prefix: "&8[&aSpawner&8] &r"

# Pluginin çalışma sistemini buradan seçebilirsiniz.
# sistem: "klasik"
#   - Sadece İpeksi Dokunuş (Silk Touch) büyüsüne sahip kazmalarla spawnerlar kırılabilir.
#   - Doğal spawnerlar da bu modda kırılabilir.
#
# sistem: "gelismis"
#   - Sadece /spsystem kazmaver komutuyla alınan özel "SP Kazma" ile spawnerlar kırılabilir.
#   - Doğal spawnerlar da bu modda kırılabilir.
#
# sistem: "towny"
#   - "klasik" sistem gibi İpeksi Dokunuş ile kırılır.
#   - ÖNEMLİ: Bu modda, doğal olarak haritada oluşan spawnerlar KORUMA ALTINDADIR ve kırılamaz.
sistem: "gelismis"

# "gelismis" modda kullanılacak özel kazmanın ayarları
spawner-kazma-item:
  isim: "&b&lSpawner Kazma"
  aciklama:
    - "&7Bu kazma ile spawner kırabilirsin."
    - "&e"
    - "&aKalan Kullanım: &c%uses%"
mesajlar:
  yetki-yok: "&cBu komutu kullanmak için yetkin yok."
  reload-basarili: "&aSpawner Sistemi ayarları başarıyla yeniden yüklendi."
  yanlis-alt-komut: "&a/spsystem <reload|kazmaver>"
  yanlis-kullanim-kazma: "&aKullanım: /spsystem kazmaver <oyuncu> <1-100>"
  komut-devre-disi: "&c'kazmaver' komutu sadece 'gelismis' sistem modunda kullanılabilir."
  oyuncu-bulunamadi: "&cOyuncu bulunamadı: %player%"
  sayi-degil: "&cKullanım hakkı bir sayı olmalıdır."
  maksimum-can-siniri: "&cMaksimum kullanım hakkı 100 olabilir."
  kazma-verildi-gonderen: "&a%player% adlı oyuncuya %uses% kullanımlık SP Kazma verildi."
  kazma-verildi-alan: "&bSana özel bir spawmer kazması verildi!"
  spawner-alindi: "&aSpawner'ı başarıyla envanterine aldın!"
  kazma-kirildi: "&aSpawner kazman kırıldı!"
  towny-yetki-yok: "&cBu spawner &e%town% &ckasabasına ait. Kırmak için kasaba üyesi olmalısın!"
  gelismis-yanlis-kazma: "&cSpawnerları sadece &b&lSP Kazma &cile kırabilirsin!"
  klasik-ipeksi-gerekli: "&cSpawnerları sadece &bİpeksi Dokunuş&c büyülü bir kazma ile kırabilirsin!"
  dogal-spawner-kirilamaz: "&cDoğal olarak oluşmuş spawnerlar koruma altındadır ve kırılamaz."
  sp-kazma-sadece-spawner: "&cBu özel kazma sadece spawner kırmak için kullanılabilir!"
  envanter-dolu: "&aEnvanterin doluydu, bu yüzden spawner yere bırakıldı!"
