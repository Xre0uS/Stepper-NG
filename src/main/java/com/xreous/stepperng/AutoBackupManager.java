package com.xreous.stepperng;

import com.coreyd97.BurpExtenderUtilities.Preferences;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.xreous.stepperng.sequence.StepSequence;
import com.xreous.stepperng.sequencemanager.SequenceManager;
import com.xreous.stepperng.variable.DynamicGlobalVariable;
import com.xreous.stepperng.variable.DynamicGlobalVariableManager;
import com.xreous.stepperng.variable.StaticGlobalVariable;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Periodically exports all Stepper-NG sequences and global variables to a JSON file on disk.
 */
public class AutoBackupManager {

    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final String BACKUP_PREFIX = "stepper-ng-backup-";
    private static final String BACKUP_SUFFIX = ".json";

    private static String sanitizeForFilename(String name) {
        if (name == null || name.isBlank()) return "unnamed";
        String sanitized = name.replaceAll("[^a-zA-Z0-9._-]+", "-");
        sanitized = sanitized.replaceAll("^-+|-+$", "");
        if (sanitized.isEmpty()) return "unnamed";
        if (sanitized.length() > 64) sanitized = sanitized.substring(0, 64);
        return sanitized;
    }

    private String getProjectNameSlug() {
        try {
            String name = Stepper.montoya.project().name();
            return sanitizeForFilename(name);
        } catch (Exception e) {
            return "unnamed";
        }
    }

    private final SequenceManager sequenceManager;
    private final DynamicGlobalVariableManager dynamicGlobalVariableManager;
    private final Preferences preferences;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Stepper-NG-AutoBackup");
        t.setDaemon(true);
        return t;
    });
    private volatile ScheduledFuture<?> scheduledTask;

    public AutoBackupManager(SequenceManager sequenceManager,
                             DynamicGlobalVariableManager dynamicGlobalVariableManager,
                             Preferences preferences) {
        this.sequenceManager = sequenceManager;
        this.dynamicGlobalVariableManager = dynamicGlobalVariableManager;
        this.preferences = preferences;
    }

    public void start() {
        stop();

        boolean enabled = isEnabled();
        if (!enabled) {
            log("Auto-backup is disabled.");
            return;
        }

        int intervalMinutes = getIntervalMinutes();
        String dir = getBackupDir();

        if (dir == null || dir.isBlank()) {
            log("Auto-backup is enabled but no directory is configured.");
            return;
        }

        log("Auto-backup scheduled every " + intervalMinutes + " minute(s) to: " + dir);

        scheduledTask = scheduler.scheduleAtFixedRate(
                this::performBackup, intervalMinutes, intervalMinutes, TimeUnit.MINUTES);
    }

    public void stop() {
        ScheduledFuture<?> task = scheduledTask;
        if (task != null) {
            task.cancel(false);
            scheduledTask = null;
        }
    }

    public void shutdown() {
        stop();
        scheduler.shutdownNow();
    }

    public String performBackup() {
        String dir = getBackupDir();
        if (dir == null || dir.isBlank()) {
            logError("Auto-backup: no directory configured.");
            return null;
        }

        Path backupDir = Paths.get(dir);
        try {
            if (!Files.exists(backupDir)) {
                Files.createDirectories(backupDir);
            }
        } catch (IOException e) {
            logError("Auto-backup: cannot create directory '" + dir + "': " + e.getMessage());
            return null;
        }

        Gson gson = Stepper.getGsonProvider().getGson();
        JsonObject root = new JsonObject();

        List<StepSequence> seqs = sequenceManager.getSequences();
        if (seqs != null && !seqs.isEmpty()) {
            root.add("sequences", gson.toJsonTree(
                    new ArrayList<>(seqs),
                    new TypeToken<ArrayList<StepSequence>>(){}.getType()));
        }
        if (dynamicGlobalVariableManager != null) {
            List<DynamicGlobalVariable> dvars = new ArrayList<>(dynamicGlobalVariableManager.getVariables());
            List<StaticGlobalVariable> svars = new ArrayList<>(dynamicGlobalVariableManager.getStaticVariables());
            if (!dvars.isEmpty()) {
                root.add("dynamicVariables", gson.toJsonTree(dvars,
                        new TypeToken<ArrayList<DynamicGlobalVariable>>(){}.getType()));
            }
            if (!svars.isEmpty()) {
                root.add("staticVariables", gson.toJsonTree(svars,
                        new TypeToken<ArrayList<StaticGlobalVariable>>(){}.getType()));
            }
        }

        if (root.size() == 0) {
            return null;
        }

        String timestamp = TIMESTAMP_FMT.format(LocalDateTime.now());
        String projectSlug = getProjectNameSlug();
        String filename = BACKUP_PREFIX + projectSlug + "-" + timestamp + BACKUP_SUFFIX;
        Path target = backupDir.resolve(filename);

        try {
            Path tmp = backupDir.resolve(filename + ".tmp");
            Files.writeString(tmp, gson.toJson(root));
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            try {
                Files.writeString(target, gson.toJson(root));
            } catch (IOException ex) {
                logError("Auto-backup: failed to write '" + target + "': " + ex.getMessage());
                return null;
            }
        } catch (IOException e) {
            logError("Auto-backup: failed to write '" + target + "': " + e.getMessage());
            return null;
        }

        log("Auto-backup saved: " + target);
        pruneOldBackups(backupDir);
        return target.toString();
    }

    private void pruneOldBackups(Path backupDir) {
        int maxFiles = getMaxFiles();
        if (maxFiles <= 0) return; // unlimited

        String projectPrefix = BACKUP_PREFIX + getProjectNameSlug() + "-";

        try (Stream<Path> files = Files.list(backupDir)) {
            List<Path> backups = files
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.startsWith(projectPrefix) && name.endsWith(BACKUP_SUFFIX)
                                && !name.endsWith(".tmp");
                    })
                    .sorted(Comparator.comparing(p -> p.getFileName().toString(), Comparator.reverseOrder()))
                    .collect(Collectors.toList());

            if (backups.size() > maxFiles) {
                for (int i = maxFiles; i < backups.size(); i++) {
                    try {
                        Files.deleteIfExists(backups.get(i));
                    } catch (IOException e) {
                        logError("Auto-backup: failed to delete old backup '" + backups.get(i) + "': " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            logError("Auto-backup: failed to list backup directory: " + e.getMessage());
        }
    }

    public boolean isEnabled() {
        if (preferences == null) return false;
        try {
            Boolean v = preferences.getSetting(Globals.PREF_AUTO_BACKUP_ENABLED);
            return v != null && v;
        } catch (Exception e) { return false; }
    }

    public int getIntervalMinutes() {
        if (preferences == null) return Globals.DEFAULT_AUTO_BACKUP_INTERVAL_MINUTES;
        try {
            Integer v = preferences.getSetting(Globals.PREF_AUTO_BACKUP_INTERVAL_MINUTES);
            return (v != null && v > 0) ? v : Globals.DEFAULT_AUTO_BACKUP_INTERVAL_MINUTES;
        } catch (Exception e) { return Globals.DEFAULT_AUTO_BACKUP_INTERVAL_MINUTES; }
    }

    public String getBackupDir() {
        if (preferences == null) return null;
        try {
            return preferences.getSetting(Globals.PREF_AUTO_BACKUP_DIR);
        } catch (Exception e) { return null; }
    }

    public int getMaxFiles() {
        if (preferences == null) return Globals.DEFAULT_AUTO_BACKUP_MAX_FILES;
        try {
            Integer v = preferences.getSetting(Globals.PREF_AUTO_BACKUP_MAX_FILES);
            return (v != null && v > 0) ? v : Globals.DEFAULT_AUTO_BACKUP_MAX_FILES;
        } catch (Exception e) { return Globals.DEFAULT_AUTO_BACKUP_MAX_FILES; }
    }

    private static void log(String msg) {
        try { Stepper.montoya.logging().logToOutput("Stepper-NG: " + msg); } catch (Exception ignored) {}
    }

    private static void logError(String msg) {
        try { Stepper.montoya.logging().logToError("Stepper-NG: " + msg); } catch (Exception ignored) {}
    }
}

