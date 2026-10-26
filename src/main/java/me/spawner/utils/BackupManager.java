package me.spawner.utils;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class BackupManager {

    private static final String BACKUP_FOLDER = "backup";
    private static final String UNKNOWN_VERSION = "0.0.0.0";
    private static final int MAX_BACKUPS = 10;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private BackupManager() {
    }

    public static boolean backup(JavaPlugin plugin, String oldVersion) {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) return false;

        try {
            File zip = createBackup(dataFolder, oldVersion);
            plugin.getLogger().info("Version changed (" + oldVersion + " -> " + ConfigUpdater.bundledVersion(plugin) + "). Backup created: " + BACKUP_FOLDER + "/" + zip.getName());
            return true;
        } catch (IOException e) {
            plugin.getLogger().warning("Could not create backup before updating from " + oldVersion + ": " + e.getMessage());
            return false;
        }
    }

    private static File createBackup(File dataFolder, String oldVersion) throws IOException {
        File backupDir = new File(dataFolder, BACKUP_FOLDER);
        if (!backupDir.exists() && !backupDir.mkdirs()) {
            throw new IOException("Could not create backup folder");
        }

        String label = oldVersion == null || oldVersion.isBlank() || oldVersion.equals(UNKNOWN_VERSION) ? "unknown" : oldVersion;
        String safeVersion = label.replaceAll("[^A-Za-z0-9._-]", "_");
        String baseName = "v" + safeVersion + "_" + LocalDateTime.now().format(DATE_FORMAT);
        File target = new File(backupDir, baseName + ".zip");
        int counter = 1;
        while (target.exists()) {
            target = new File(backupDir, baseName + "_" + counter++ + ".zip");
        }

        Path root = dataFolder.toPath();
        Path backupPath = backupDir.toPath();
        File temp = new File(backupDir, target.getName() + ".tmp");

        List<Path> files;
        try (Stream<Path> walk = Files.walk(root)) {
            files = walk.filter(Files::isRegularFile)
                    .filter(p -> !p.startsWith(backupPath))
                    .collect(Collectors.toList());
        }

        try (OutputStream out = Files.newOutputStream(temp.toPath());
             ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            for (Path file : files) {
                String entryName = root.relativize(file).toString().replace(File.separatorChar, '/');
                zip.putNextEntry(new ZipEntry(entryName));
                Files.copy(file, zip);
                zip.closeEntry();
            }
        } catch (IOException e) {
            Files.deleteIfExists(temp.toPath());
            throw e;
        }

        Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        prune(backupDir);
        return target;
    }

    private static void prune(File backupDir) {
        File[] zips = backupDir.listFiles((dir, name) -> name.endsWith(".zip"));
        if (zips == null || zips.length <= MAX_BACKUPS) return;
        Arrays.sort(zips, Comparator.comparingLong(File::lastModified));
        for (int i = 0; i < zips.length - MAX_BACKUPS; i++) {
            zips[i].delete();
        }
    }
}
