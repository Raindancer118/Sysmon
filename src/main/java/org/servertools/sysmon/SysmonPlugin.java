
package org.servertools.sysmon;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class SysmonPlugin extends JavaPlugin implements TabExecutor {

    private File csvFile;
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @Override
    public void onEnable() {
        getLogger().info("Sysmon gestartet.");
        csvFile = new File(getDataFolder(), "sysmon.csv");

        if (!csvFile.exists()) {
            try {
                getDataFolder().mkdirs();
                try (FileWriter writer = new FileWriter(csvFile, true)) {
                    writer.write("Zeitpunkt,RAM Gesamt (MB),RAM Frei (MB),RAM Belegt (MB),TPS,Spieler Online,Chunks,Entities,Welten,Redstone\n");
                }
            } catch (IOException e) {
                getLogger().warning("Fehler beim Erstellen der CSV-Datei: " + e.getMessage());
            }
        }

        getCommand("sysmon").setExecutor(this);

        new BukkitRunnable() {
            @Override
            public void run() {
                writeStatsToCSV();
            }
        }.runTaskTimerAsynchronously(this, 0L, 20L * 10);
    }

    private void writeStatsToCSV() {
        Runtime runtime = Runtime.getRuntime();
        long total = runtime.totalMemory() / 1024 / 1024;
        long free = runtime.freeMemory() / 1024 / 1024;
        long used = total - free;
        double tps = Bukkit.getTPS()[0];
        int players = Bukkit.getOnlinePlayers().size();
        String timestamp = sdf.format(new Date());

        int chunks = 0;
        int entities = 0;
        int redstone = 0;
        int worlds = Bukkit.getWorlds().size();

        for (World world : Bukkit.getWorlds()) {
            var loadedChunks = world.getLoadedChunks();
            chunks += loadedChunks.length;

            for (var chunk : loadedChunks) {
                entities += chunk.getEntities().length;

                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int y = 0; y < world.getMaxHeight(); y++) {
                            Block b = chunk.getBlock(x, y, z);
                            Material type = b.getType();
                            if (type == Material.REDSTONE_WIRE || type == Material.REPEATER || type.name().contains("COMPARATOR")) {
                                redstone++;
                            }
                        }
                    }
                }
            }
        }

        String row = String.format("%s,%.2f,%.2f,%.2f,%.2f,%d,%d,%d,%d,%d\n",
                timestamp, (double) total, (double) free, (double) used, tps, players, chunks, entities, worlds, redstone);

        try (FileWriter writer = new FileWriter(csvFile, true)) {
            writer.write(row);
        } catch (IOException e) {
            getLogger().warning("Fehler beim Schreiben in CSV: " + e.getMessage());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        int minutes = args.length > 1 && args[0].equalsIgnoreCase("stats") ? Integer.parseInt(args[1]) : -1;
        List<String[]> lines = new ArrayList<>();
        long now = System.currentTimeMillis();

        try (BufferedReader reader = new BufferedReader(new FileReader(csvFile))) {
            String header = reader.readLine();
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                Date parsed = sdf.parse(parts[0]);
                if (minutes < 0 || now - parsed.getTime() <= minutes * 60 * 1000) {
                    lines.add(parts);
                }
            }
        } catch (Exception e) {
            sender.sendMessage("§cFehler beim Lesen der CSV: " + e.getMessage());
            return true;
        }

        if (lines.isEmpty()) {
            sender.sendMessage("§cKeine Daten für diesen Zeitraum vorhanden.");
            return true;
        }

        int count = lines.size();
        double ramUsed = 0, tps = 0, chunks = 0, entities = 0, players = 0, redstone = 0;

        for (String[] data : lines) {
            ramUsed += Double.parseDouble(data[3]);
            tps += Double.parseDouble(data[4]);
            players += Integer.parseInt(data[5]);
            chunks += Integer.parseInt(data[6]);
            entities += Integer.parseInt(data[7]);
            redstone += Integer.parseInt(data[9]);
        }

        logToBoth(sender, "§6[Sysmon Stats] Zeitraum: " + (minutes > 0 ? minutes + " Minuten" : "Letzter Lauf"));
        logToBoth(sender, "§7• RAM Belegt (Ø): §f" + String.format("%.2f", ramUsed / count) + " MB");
        logToBoth(sender, "§7• TPS (Ø): §f" + String.format("%.2f", tps / count));
        logToBoth(sender, "§7• Spieler (Ø): §f" + String.format("%.1f", players / count));
        logToBoth(sender, "§7• Chunks (Ø): §f" + String.format("%.1f", chunks / count));
        logToBoth(sender, "§7• Entities (Ø): §f" + String.format("%.1f", entities / count));
        logToBoth(sender, "§7• Redstone (Ø): §f" + String.format("%.1f", redstone / count));
        return true;
    }

    private void logToBoth(CommandSender sender, String msg) {
        sender.sendMessage(msg);
        getLogger().info(" " + msg.replaceAll("§.", ""));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return Collections.emptyList();
    }
}
