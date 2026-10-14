package de.autorestart;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AutoRestart extends JavaPlugin implements TabExecutor {

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");
    private final MiniMessage mm = MiniMessage.miniMessage();

    private BukkitTask task;
    private ZoneId zone;
    private final List<LocalTime> times = new ArrayList<>();
    private final Set<Integer> warnings = new HashSet<>();
    private int countdownSeconds;

    /** Verbleibende Sekunden, -1 = kein Countdown aktiv */
    private int remaining = -1;
    private String lastTriggered = "";

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();

        var cmd = getCommand("restart");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }

        task = Bukkit.getScheduler().runTaskTimer(this, this::tick, 20L, 20L);
        getLogger().info("AutoRestart aktiv. Zeiten: " + times);
    }

    @Override
    public void onDisable() {
        if (task != null) task.cancel();
    }

    private void loadSettings() {
        reloadConfig();
        var cfg = getConfig();

        try {
            zone = ZoneId.of(cfg.getString("timezone", "Europe/Berlin"));
        } catch (Exception e) {
            getLogger().warning("Ungültige Zeitzone, nutze Systemzeit.");
            zone = ZoneId.systemDefault();
        }

        times.clear();
        for (String s : cfg.getStringList("restart-times")) {
            try {
                times.add(LocalTime.parse(s.trim(), HM));
            } catch (Exception e) {
                getLogger().warning("Ungültige Uhrzeit in restart-times: " + s);
            }
        }

        warnings.clear();
        warnings.addAll(cfg.getIntegerList("warnings"));
        countdownSeconds = Math.max(1, cfg.getInt("countdown-seconds", 300));
    }

    // ---------- Tick-Logik (1x pro Sekunde) ----------

    private void tick() {
        if (remaining >= 0) {
            remaining--;
            if (remaining <= 0) {
                performRestart();
            } else if (warnings.contains(remaining)) {
                announce(remaining);
            }
            return;
        }

        ZonedDateTime now = ZonedDateTime.now(zone);
        String key = now.toLocalDate() + " " + HM.format(now);
        LocalTime current = LocalTime.parse(HM.format(now), HM);

        if (times.contains(current) && !key.equals(lastTriggered)) {
            lastTriggered = key;
            startCountdown(countdownSeconds);
        }
    }

    private void startCountdown(int seconds) {
        remaining = seconds;
        announce(seconds);
    }

    private void cancelCountdown() {
        remaining = -1;
    }

    private void announce(int seconds) {
        String time = formatTime(seconds);
        Component msg = prefix().append(mm.deserialize(msg("countdown"),
                Placeholder.unparsed("time", time)));
        Bukkit.broadcast(msg);

        if (seconds <= 10) {
            Title title = Title.title(
                    mm.deserialize(msg("title"), Placeholder.unparsed("time", String.valueOf(seconds))),
                    mm.deserialize(msg("subtitle")),
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(100)));
            Bukkit.getServer().showTitle(title);
        }
    }

    private void performRestart() {
        remaining = -1;
        Component kick = mm.deserialize(msg("kick"));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.kick(kick);
        }

        String method = getConfig().getString("restart-method", "shutdown");
        // Eine Tick später, damit die Kicks sauber rausgehen
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (method.equalsIgnoreCase("spigot")) {
                Bukkit.spigot().restart();
            } else {
                Bukkit.shutdown();
            }
        }, 10L);
    }

    // ---------- Hilfsfunktionen ----------

    private String formatTime(int seconds) {
        if (seconds >= 60 && seconds % 60 == 0) {
            int m = seconds / 60;
            return m + " " + msg(m == 1 ? "unit-minute" : "unit-minutes");
        }
        return seconds + " " + msg(seconds == 1 ? "unit-second" : "unit-seconds");
    }

    private String msg(String key) {
        return getConfig().getString("messages." + key, key);
    }

    private Component prefix() {
        return mm.deserialize(msg("prefix"));
    }

    private void send(CommandSender s, String key) {
        s.sendMessage(prefix().append(mm.deserialize(msg(key))));
    }

    private String nextRestart() {
        if (times.isEmpty()) return "-";
        LocalTime now = LocalTime.now(zone);
        return times.stream().sorted()
                .filter(t -> t.isAfter(now)).findFirst()
                .orElse(times.stream().sorted().findFirst().get())
                .format(HM);
    }

    // ---------- Command ----------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("autorestart.admin")) {
            send(sender, "no-permission");
            return true;
        }

        if (args.length == 0) {
            startCountdown(countdownSeconds);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "cancel" -> {
                if (remaining < 0) {
                    send(sender, "no-countdown");
                } else {
                    cancelCountdown();
                    Bukkit.broadcast(prefix().append(mm.deserialize(msg("cancelled"))));
                }
            }
            case "status" -> {
                if (remaining >= 0) {
                    sender.sendMessage(prefix().append(mm.deserialize(msg("status-running"),
                            Placeholder.unparsed("time", formatTime(remaining)))));
                } else {
                    send(sender, "no-countdown");
                }
                sender.sendMessage(prefix().append(mm.deserialize(msg("status-next"),
                        Placeholder.unparsed("next", nextRestart()))));
            }
            case "reload" -> {
                loadSettings();
                send(sender, "reloaded");
            }
            default -> {
                try {
                    int sec = Integer.parseInt(args[0]);
                    if (sec < 1) throw new NumberFormatException();
                    startCountdown(sec);
                } catch (NumberFormatException e) {
                    send(sender, "invalid-number");
                }
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("autorestart.admin")) {
            return List.of("cancel", "status", "reload", "60", "300").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        return List.of();
    }
}
