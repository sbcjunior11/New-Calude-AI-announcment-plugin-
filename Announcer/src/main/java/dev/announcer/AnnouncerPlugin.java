package dev.announcer;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AnnouncerPlugin extends JavaPlugin implements TabCompleter {

    // Coconut / tropical shimmer: greens, golds, white
    private static final char[] PALETTE = {'a', '2', 'e', '6', 'f', '6', 'e', '2'};
    private static final List<String> EVENTS = Arrays.asList("sword", "mace", "spear", "crystal");

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getCommand("announce").setTabCompleter(this);
        getLogger().info("CoconutMC Announcer enabled.");
    }

    // ---------- helpers ----------

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }

    /** Bold, letter-by-letter coconut-colour shimmer. offset shifts colours for animation. */
    private String shimmer(String text, int offset) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (char c : text.toCharArray()) {
            if (c == ' ') { sb.append(' '); continue; }
            char code = PALETTE[(i + offset) % PALETTE.length];
            sb.append(ChatColor.COLOR_CHAR).append(code).append(ChatColor.COLOR_CHAR).append('l').append(c);
            i++;
        }
        return sb.toString();
    }

    private void sound(String name, float volume, float pitch) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), name, volume, pitch);
        }
    }

    // ---------- commands ----------

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        FileConfiguration cfg = getConfig();

        if (!sender.hasPermission("announcer.use")) {
            sender.sendMessage(color(cfg.getString("messages.no-permission")));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(color(cfg.getString("messages.usage")));
            return true;
        }

        String first = args[0].toLowerCase();

        if (args.length == 1 && first.equals("reload")) {
            if (!sender.hasPermission("announcer.reload")) {
                sender.sendMessage(color(cfg.getString("messages.no-permission")));
                return true;
            }
            reloadConfig();
            sender.sendMessage(color(getConfig().getString("messages.reloaded")));
            return true;
        }

        if (args.length == 1 && first.equals("keyall")) {
            keyall();
            return true;
        }

        if (args.length == 1 && EVENTS.contains(first)) {
            event(first);
            return true;
        }

        if (first.equals("darkness") || first.equals("dark")) {
            String msg = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : null;
            darkness(msg);
            return true;
        }

        // "/announce ......" (only dots) or "/announce surprise" = the surprise!
        if (args.length == 1 && (first.equals("surprise") || first.matches("\\.+"))) {
            surprise();
            return true;
        }

        broadcast(String.join(" ", args));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1 && sender.hasPermission("announcer.use")) {
            for (String s : new String[]{"sword", "mace", "spear", "crystal", "darkness", "keyall", "surprise", "reload"}) {
                if (s.startsWith(args[0].toLowerCase())) out.add(s);
            }
        }
        return out;
    }

    // ---------- normal announcement ----------

    private void broadcast(String rawMessage) {
        FileConfiguration cfg = getConfig();
        String message = color(rawMessage);
        String chatLine = color(cfg.getString("chat.prefix", "")) + message;
        boolean spacing = cfg.getBoolean("chat.blank-lines-around", true);

        boolean titleOn = cfg.getBoolean("title.enabled", true);
        String mainTitle = color(cfg.getString("title.main", ""));
        int fadeIn = cfg.getInt("title.fade-in", 10);
        int stay = cfg.getInt("title.stay", 80);
        int fadeOut = cfg.getInt("title.fade-out", 20);

        boolean soundOn = cfg.getBoolean("sound.enabled", true);
        String sound = cfg.getString("sound.name", "entity.player.levelup");
        float volume = (float) cfg.getDouble("sound.volume", 1.0);
        float pitch = (float) cfg.getDouble("sound.pitch", 1.0);

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (spacing) p.sendMessage(" ");
            p.sendMessage(chatLine);
            if (spacing) p.sendMessage(" ");
            if (titleOn) p.sendTitle(mainTitle, message, fadeIn, stay, fadeOut);
            if (soundOn) p.playSound(p.getLocation(), sound, volume, pitch);
        }
        Bukkit.getConsoleSender().sendMessage(chatLine);
    }

    // ---------- /announce sword | mace | spear | crystal ----------

    private void event(String key) {
        ConfigurationSection s = getConfig().getConfigurationSection("events." + key);
        if (s == null) return;

        final String name = s.getString("name", key.toUpperCase() + " EVENT");
        final String symbol = s.getString("symbol", "✿");
        final String subtitle = color(s.getString("subtitle", ""));
        final String soundName = s.getString("sound", "block.note_block.bell");
        List<String> lines = s.getStringList("lines");
        final int frames = 24;

        // Chat banner
        String header = color("&8&m        &r &6&l" + symbol + " ") + shimmer(name, 0) + color(" &6&l" + symbol + " &8&m        ");
        String footer = color("&8&m                                        ");
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(" ");
            p.sendMessage(header);
            for (String line : lines) p.sendMessage(color(line));
            p.sendMessage(footer);
            p.sendMessage(" ");
        }
        Bukkit.getConsoleSender().sendMessage(header);

        sound("block.note_block.bell", 1f, 1.2f);
        sound(soundName, 1f, 1f);

        // Shimmering title
        new BukkitRunnable() {
            int frame = 0;

            @Override
            public void run() {
                if (frame >= frames) {
                    cancel();
                    return;
                }
                String title = color("&6&l" + symbol + " ") + shimmer(name, frame) + color(" &6&l" + symbol);
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.sendTitle(title, subtitle, frame == 0 ? 5 : 0, 8, frame == frames - 1 ? 20 : 0);
                }
                if (frame == 8) sound("block.note_block.chime", 1f, 1.4f);
                frame++;
            }
        }.runTaskTimer(this, 0L, 4L);
    }

    // ---------- /announce darkness [message] ----------

    private void darkness(String customMessage) {
        FileConfiguration cfg = getConfig();
        final int seconds = Math.max(3, cfg.getInt("darkness.duration", 8));
        final int ticks = seconds * 20;
        final boolean freeze = cfg.getBoolean("darkness.freeze-movement", true);

        final String message = customMessage != null
                ? color(customMessage)
                : color(cfg.getString("darkness.default-message", "&f&lThe lights have gone out..."));
        final String subtitle = color(cfg.getString("darkness.subtitle", "&7&oThe coconut grove falls silent..."));
        final String endMessage = color(cfg.getString("darkness.end-message", "&e&lThe lights return..."));

        PotionEffectType slow = freeze ? PotionEffectType.getByName("SLOW") : null;

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, ticks + 20, 0, false, false, false));
            p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, ticks + 20, 0, false, false, false));
            if (slow != null) {
                p.addPotionEffect(new PotionEffect(slow, ticks + 20, 6, false, false, false));
            }
            p.sendTitle(message, subtitle, 20, Math.max(20, ticks - 40), 20);
            p.sendMessage(" ");
            p.sendMessage(message);
            p.sendMessage(" ");
        }
        sound("ambient.cave", 1f, 0.8f);

        new BukkitRunnable() {
            @Override
            public void run() {
                PotionEffectType slowType = PotionEffectType.getByName("SLOW");
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.removePotionEffect(PotionEffectType.BLINDNESS);
                    p.removePotionEffect(PotionEffectType.DARKNESS);
                    if (slowType != null) p.removePotionEffect(slowType);
                    p.sendMessage(endMessage);
                }
                sound("block.beacon.activate", 1f, 1.2f);
            }
        }.runTaskLater(this, ticks);
    }

    // ---------- /announce keyall ----------

    private void keyall() {
        final String text = getConfig().getString("keyall.text", "Are we ready for a keyall?");
        final String subtitle = color(getConfig().getString("keyall.subtitle", "&7Get ready..."));
        final int frames = 24;

        String chatLine = color("&8&m      &r &6&l✿ ") + shimmer(text, 0) + color(" &6&l✿ &8&m      ");
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(" ");
            p.sendMessage(chatLine);
            p.sendMessage(" ");
        }
        sound("block.note_block.bell", 1f, 1f);

        new BukkitRunnable() {
            int frame = 0;

            @Override
            public void run() {
                if (frame >= frames) {
                    cancel();
                    return;
                }
                String title = color("&6✿ ") + shimmer(text, frame) + color(" &6✿");
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.sendTitle(title, subtitle, frame == 0 ? 5 : 0, 8, frame == frames - 1 ? 20 : 0);
                }
                if (frame % 6 == 0) sound("block.note_block.chime", 1f, 1f + (frame / 6) * 0.15f);
                frame++;
            }
        }.runTaskTimer(this, 0L, 4L);
    }

    // ---------- /announce ...... (the surprise!) ----------

    private void surprise() {
        final String[] countdown = {"&c&l3", "&6&l2", "&e&l1"};

        new BukkitRunnable() {
            int step = 0;

            @Override
            public void run() {
                if (step < countdown.length) {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.sendTitle(color(countdown[step]), color("&7Something is coming..."), 0, 25, 0);
                    }
                    sound("block.note_block.pling", 1f, 0.6f + step * 0.3f);
                    step++;
                    return;
                }
                cancel();
                finale();
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    private void finale() {
        final String text = getConfig().getString("surprise.text", "SURPRISE!");
        final String subtitle = color(getConfig().getString("surprise.subtitle", "&aEnjoy the show!"));
        final String chat = color(getConfig().getString("surprise.chat", "&6&l✿ &a&lSURPRISE! &6&l✿"));

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(" ");
            p.sendMessage(chat);
            p.sendMessage(" ");
        }
        sound("ui.toast.challenge_complete", 1f, 1f);

        new BukkitRunnable() {
            int frame = 0;

            @Override
            public void run() {
                if (frame >= 20) {
                    cancel();
                    return;
                }
                String title = shimmer(text, frame);
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.sendTitle(title, subtitle, frame == 0 ? 3 : 0, 8, frame == 19 ? 20 : 0);
                }
                frame++;
            }
        }.runTaskTimer(this, 0L, 4L);

        new BukkitRunnable() {
            int burst = 0;

            @Override
            public void run() {
                if (burst >= 4) {
                    cancel();
                    return;
                }
                for (Player p : Bukkit.getOnlinePlayers()) {
                    launchFirework(p.getLocation().add(0, 2, 0));
                }
                sound("entity.firework_rocket.blast", 1f, 1f);
                burst++;
            }
        }.runTaskTimer(this, 0L, 12L);
    }

    private void launchFirework(Location loc) {
        Firework fw = loc.getWorld().spawn(loc, Firework.class);
        FireworkMeta meta = fw.getFireworkMeta();
        meta.addEffect(FireworkEffect.builder()
                .with(FireworkEffect.Type.BALL_LARGE)
                .withColor(Color.GREEN, Color.YELLOW, Color.WHITE, Color.ORANGE)
                .withFade(Color.fromRGB(139, 69, 19)) // coconut brown
                .flicker(true)
                .trail(true)
                .build());
        meta.setPower(0);
        fw.setFireworkMeta(meta);
        fw.detonate();
    }
}
