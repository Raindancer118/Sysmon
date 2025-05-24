
package org.servertools.sysmon;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class SysmonPlugin extends JavaPlugin {

    private File csvFile;

    @Override
    public void onEnable() {
        getLogger().info("[Sysmon] Sysmon gestartet.");
        csvFile = new File(getDataFolder(), "sysmon.csv");

        if (!csvFile.exists()) {
            try {
                getDataFolder().mkdirs();
                try (FileWriter writer = new FileWriter(csvFile, true)) {
                    writer.write("Zeitpunkt,RAM Gesamt (MB),RAM Frei (MB),RAM Belegt (MB),TPS,Spieler Online,Chunks,Entities,Welten");
                }
            } catch (IOException e) {
                getLogger().warning("Fehler beim Erstellen der CSV-Datei: " + e.getMessage());
            }
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                writeStatsToCSV();
            }
        }.runTaskTimerAsynchronously(this, 0L, 20L * 10); // alle 10 Sekunden
    }

    private void writeStatsToCSV() {
        Runtime runtime = Runtime.getRuntime();
        long total = runtime.totalMemory() / 1024 / 1024;
        long free = runtime.freeMemory() / 1024 / 1024;
        long used = total - free;
        double tps = Bukkit.getTPS()[0];
        int players = Bukkit.getOnlinePlayers().size();
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());

        int chunks = 0;
        int entities = 0;
        int worlds = Bukkit.getWorlds().size();

        for (World world : Bukkit.getWorlds()) {
            chunks += world.getLoadedChunks().length;
            entities += world.getEntities().size();
        }

        String row = String.format("%s,%.2f,%.2f,%.2f,%.2f,%d,%d,%d,%d\n",
                timestamp, (double) total, (double) free, (double) used, tps, players, chunks, entities, worlds);

        try (FileWriter writer = new FileWriter(csvFile, true)) {
            writer.write(row);
        } catch (IOException e) {
            getLogger().warning("Fehler beim Schreiben in CSV: " + e.getMessage());
        }
    }
}
