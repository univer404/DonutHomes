package com.donuthomes;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class DonutHomes extends JavaPlugin implements Listener {
    private final MiniMessage mini = MiniMessage.miniMessage();
    private final Map<UUID, Map<Integer, Location>> homes = new HashMap<>();
    private final Map<UUID, Location> teleportOrigins = new HashMap<>();
    private final Map<UUID, BukkitTask> teleportTasks = new HashMap<>();
    private final Map<UUID, Integer> pendingDelete = new HashMap<>();
    private File homesFile;
    private YamlConfiguration homesYaml;

    @Override public void onEnable() {
        saveDefaultConfig();
        homesFile = new File(getDataFolder(), "homes.yml");
        homesYaml = YamlConfiguration.loadConfiguration(homesFile);
        loadHomes();
        Bukkit.getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("home")).setExecutor(this);
        Objects.requireNonNull(getCommand("homes")).setExecutor(this);
        Objects.requireNonNull(getCommand("sethome")).setExecutor(this);
        Objects.requireNonNull(getCommand("delhome")).setExecutor(this);
        Objects.requireNonNull(getCommand("donuthomes")).setExecutor(this);
        getLogger().info("DonutHomes enabled.");
    }

    @Override public void onDisable() {
        for (BukkitTask task : teleportTasks.values()) task.cancel();
        teleportTasks.clear();
        saveHomes();
    }

    private void loadHomes() {
        homes.clear();
        ConfigurationSection root = homesYaml.getConfigurationSection("players");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            UUID uuid;
            try { uuid = UUID.fromString(id); } catch (IllegalArgumentException ignored) { continue; }
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) continue;
            Map<Integer, Location> entries = new HashMap<>();
            for (String key : section.getKeys(false)) {
                try {
                    int slot = Integer.parseInt(key);
                    Location location = section.getLocation(key);
                    if (slot >= 1 && slot <= 5 && location != null && location.getWorld() != null)
                        entries.put(slot, location);
                } catch (NumberFormatException ignored) { }
            }
            homes.put(uuid, entries);
        }
    }

    private void saveHomes() {
        homesYaml = new YamlConfiguration();
        for (Map.Entry<UUID, Map<Integer, Location>> entry : homes.entrySet())
            for (Map.Entry<Integer, Location> home : entry.getValue().entrySet())
                homesYaml.set("players." + entry.getKey() + "." + home.getKey(), home.getValue());
        try { homesYaml.save(homesFile); }
        catch (IOException ex) { getLogger().severe("Could not save homes.yml: " + ex.getMessage()); }
    }

    private Map<Integer, Location> playerHomes(Player player) {
        return homes.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>());
    }

    private int limit(Player player) {
        int value = Math.max(1, Math.min(5, getConfig().getInt("homes.default-limit", 2)));
        if (player.hasPermission("homes.default")) value = Math.max(value, 2);
        if (player.hasPermission("homes.donutplus")) value = 5;
        for (int slot = 2; slot <= 5; slot++)
            if (player.hasPermission("donuthomes.limit." + slot)) value = Math.max(value, slot);
        return value;
    }

    private boolean blockedWorld(World world) {
        for (String name : getConfig().getStringList("blocked-worlds"))
            if (name.equalsIgnoreCase(world.getName())) return true;
        return false;
    }

    private void message(CommandSender sender, String key) {
        String text = getConfig().getString(key, "<red>Missing message: " + key);
        sender.sendMessage(mini.deserialize(text));
        if (sender instanceof Player player && getConfig().getBoolean("action-bar-messages", true))
            player.sendActionBar(mini.deserialize(text));
    }

    private ItemStack item(Material material, String name, String lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(mini.deserialize(name));
        meta.lore(List.of(mini.deserialize(lore)));
        stack.setItemMeta(meta);
        return stack;
    }

    private void openHomes(Player player) {
        Inventory inventory = Bukkit.createInventory(player, 36, mini.deserialize("<#3F3F3F>ʜᴏᴍᴇꜱ"));
        Map<Integer, Location> map = playerHomes(player);
        for (int slot = 1; slot <= 5; slot++) {
            int bedSlot = 11 + slot;
            int deleteSlot = 20 + slot;
            if (map.containsKey(slot)) {
                inventory.setItem(bedSlot, item(Material.LIGHT_BLUE_BED, "<#00A4FC>ʜᴏᴍᴇ " + slot, "<white>Click to teleport to your home"));
                inventory.setItem(deleteSlot, item(Material.BLUE_DYE, "<#00A4FC>ʜᴏᴍᴇ " + slot, "<white>Click to delete this home"));
            } else if (slot <= limit(player)) {
                inventory.setItem(bedSlot, item(Material.LIGHT_GRAY_BED, "<#CFCFCF>ɴᴏ ʜᴏᴍᴇ ꜱᴇᴛ", "<white>Click to create a home"));
                inventory.setItem(deleteSlot, item(Material.GRAY_DYE, "<#CFCFCF>ɴᴏ ʜᴏᴍᴇ ꜱᴇᴛ", "<white>Click to create a home"));
            } else {
                inventory.setItem(bedSlot, item(Material.RED_BED, "<#FC0000>ɴᴏ ᴘᴇʀᴍɪꜱꜱɪᴏɴ", "<white>You need a higher rank for this home"));
                inventory.setItem(deleteSlot, item(Material.RED_DYE, "<#FC0000>ɴᴏ ᴘᴇʀᴍɪꜱꜱɪᴏɴ", "<white>You need a higher rank for this home"));
            }
        }
        if (getConfig().getBoolean("team-home.enabled", true)) {
            inventory.setItem(10, item(Material.WHITE_BANNER, "<#00A4FC>ᴛᴇᴀᴍ ʜᴏᴍᴇ", "<white>Click to run the configured team-home command"));
            inventory.setItem(19, item(Material.BLUE_DYE, "<#00A4FC>ᴛᴇᴀᴍ ʜᴏᴍᴇ", "<white>Click to run the configured team-home command"));
        }
        player.openInventory(inventory);
    }

    private void openDelete(Player player, int slot) {
        if (slot < 1 || slot > 5 || !playerHomes(player).containsKey(slot)) {
            message(player, "messages.no-home");
            return;
        }
        pendingDelete.put(player.getUniqueId(), slot);
        Inventory inventory = Bukkit.createInventory(player, 27, mini.deserialize("<#3F3F3F>Confirm Delete " + slot));
        inventory.setItem(11, item(Material.RED_STAINED_GLASS_PANE, "<#FC0000>ᴄᴀɴᴄᴇʟ", "<white>Click to cancel"));
        inventory.setItem(13, item(Material.BLUE_DYE, "<#00A4FC>ʜᴏᴍᴇ " + slot, "<white>This home will be deleted"));
        inventory.setItem(15, item(Material.LIME_STAINED_GLASS_PANE, "<#00FC00>ᴄᴏɴꜰɪʀᴍ", "<white>Click to delete"));
        player.openInventory(inventory);
    }

    private void createHome(Player player, int slot) {
        if (slot < 1 || slot > limit(player) || slot > 5) { message(player, "messages.limit"); return; }
        if (blockedWorld(player.getWorld())) { message(player, "messages.blocked-world"); return; }
        Map<Integer, Location> map = playerHomes(player);
        if (map.containsKey(slot)) { openDelete(player, slot); return; }
        if (map.size() >= limit(player)) { message(player, "messages.limit"); return; }
        map.put(slot, player.getLocation().clone());
        saveHomes();
        message(player, "messages.home-set");
        player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 1f, 1f);
        openHomes(player);
    }

    private void deleteHome(Player player, int slot) {
        if (playerHomes(player).remove(slot) != null) {
            saveHomes();
            message(player, "messages.home-deleted");
        }
        pendingDelete.remove(player.getUniqueId());
        openHomes(player);
    }

    private void teleportHome(Player player, int slot) {
        Location target = playerHomes(player).get(slot);
        if (target == null || target.getWorld() == null) { message(player, "messages.no-home"); return; }
        if (teleportTasks.containsKey(player.getUniqueId())) return;
        teleportOrigins.put(player.getUniqueId(), player.getLocation().clone());
        player.closeInventory();
        int delay = Math.max(0, getConfig().getInt("teleport-delay-seconds", 5));
        message(player, "messages.teleport-start");
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            private int remaining = delay;
            @Override public void run() {
                if (!player.isOnline()) { cancelTeleport(player); return; }
                if (remaining <= 0) {
                    Location destination = playerHomes(player).get(slot);
                    if (destination != null && destination.getWorld() != null) {
                        player.teleport(destination);
                        message(player, "messages.teleport-success");
                    }
                    cancelTeleport(player);
                    return;
                }
                String text = getConfig().getString("messages.teleport-countdown", "<gray>Teleporting in <aqua>%seconds% <gray>seconds")
                        .replace("%seconds%", Integer.toString(remaining));
                player.sendMessage(mini.deserialize(text));
                player.sendActionBar(mini.deserialize(text));
                remaining--;
            }
        }, 0L, 20L);
        teleportTasks.put(player.getUniqueId(), task);
    }

    private void cancelTeleport(Player player) {
        BukkitTask task = teleportTasks.remove(player.getUniqueId());
        if (task != null) task.cancel();
        teleportOrigins.remove(player.getUniqueId());
    }

    private int parseSlot(String input) {
        try { return Integer.parseInt(input); } catch (NumberFormatException ignored) { return -1; }
    }

    private int firstFreeSlot(Player player) {
        for (int slot = 1; slot <= limit(player); slot++)
            if (!playerHomes(player).containsKey(slot)) return slot;
        return -1;
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("donuthomes")) {
            if (!sender.hasPermission("donuthomes.admin")) { message(sender, "messages.no-permission"); return true; }
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                reloadConfig();
                message(sender, "messages.reloaded");
            } else sender.sendMessage("Usage: /donuthomes reload");
            return true;
        }
        if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "home", "homes" -> {
                if (args.length == 0) openHomes(player);
                else if (args[0].equalsIgnoreCase("set")) {
                    int slot = firstFreeSlot(player);
                    if (slot < 1) message(player, "messages.limit"); else createHome(player, slot);
                } else if (args[0].equalsIgnoreCase("delete") && args.length > 1) {
                    int slot = parseSlot(args[1]);
                    if (slot < 1 || slot > 5) message(player, "messages.invalid-slot"); else openDelete(player, slot);
                } else {
                    int slot = parseSlot(args[0]);
                    if (slot < 1 || slot > 5) message(player, "messages.usage-home"); else teleportHome(player, slot);
                }
            }
            case "sethome" -> {
                int slot = args.length > 0 ? parseSlot(args[0]) : firstFreeSlot(player);
                if (slot < 1 || slot > 5) message(player, "messages.invalid-slot"); else createHome(player, slot);
            }
            case "delhome" -> {
                if (args.length != 1) message(player, "messages.usage-delhome");
                else {
                    int slot = parseSlot(args[0]);
                    if (slot < 1 || slot > 5) message(player, "messages.invalid-slot"); else openDelete(player, slot);
                }
            }
            default -> { return false; }
        }
        return true;
    }

    @EventHandler public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = mini.serialize(event.getView().title());
        if (title.equals("<#3F3F3F>ʜᴏᴍᴇꜱ")) {
            event.setCancelled(true);
            if (event.getClickedInventory() != event.getView().getTopInventory()) return;
            int slotIndex = event.getRawSlot();
            if (slotIndex == 10 || slotIndex == 19) {
                if (getConfig().getBoolean("team-home.enabled", true)) {
                    player.closeInventory();
                    player.performCommand(getConfig().getString("team-home.command", "team home"));
                }
                return;
            }
            if (slotIndex >= 12 && slotIndex <= 16) {
                int slot = slotIndex - 11;
                if (playerHomes(player).containsKey(slot)) teleportHome(player, slot); else createHome(player, slot);
            } else if (slotIndex >= 21 && slotIndex <= 25) {
                int slot = slotIndex - 20;
                if (playerHomes(player).containsKey(slot)) openDelete(player, slot); else createHome(player, slot);
            }
        } else if (title.startsWith("<#3F3F3F>Confirm Delete ")) {
            event.setCancelled(true);
            if (event.getClickedInventory() != event.getView().getTopInventory()) return;
            if (event.getRawSlot() == 15) {
                Integer slot = pendingDelete.get(player.getUniqueId());
                if (slot != null) deleteHome(player, slot);
            } else if (event.getRawSlot() == 11) {
                pendingDelete.remove(player.getUniqueId());
                openHomes(player);
            }
        }
    }

    @EventHandler public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (event.getTo() == null || !teleportTasks.containsKey(player.getUniqueId())) return;
        Location from = event.getFrom(), to = event.getTo();
        if (from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY() || from.getBlockZ() != to.getBlockZ()) {
            cancelTeleport(player);
            String text = getConfig().getString("messages.teleport-cancelled", "<red>Teleport cancelled because you moved.");
            player.sendMessage(mini.deserialize(text));
            player.sendActionBar(mini.deserialize(text));
        }
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) { cancelTeleport(event.getPlayer()); }
    @EventHandler public void onQuit(PlayerQuitEvent event) {
        cancelTeleport(event.getPlayer());
        pendingDelete.remove(event.getPlayer().getUniqueId());
    }
}
