package dev.airdrop;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Chest;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class AirDropPlugin extends JavaPlugin {
    private final List<Drop> drops = new ArrayList<>();
    private final Random random = new Random();
    private BukkitTask autoTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        DropCommand cmd = new DropCommand(this);
        getCommand("drop").setExecutor(cmd);
        getCommand("drop").setTabCompleter(cmd);
        getServer().getPluginManager().registerEvents(new DropListener(this), this);
        startTimer();
    }

    @Override
    public void onDisable() {
        for (Drop d : new ArrayList<>(drops)) {
            removeDrop(d);
        }
    }

    // ---------- сообщения ----------

    public Component msg(String key, Map<String, String> vars) {
        String raw = getConfig().getString("messages." + key, "");
        raw = raw.replace("{prefix}", getConfig().getString("messages.prefix", ""));
        for (Map.Entry<String, String> e : vars.entrySet()) {
            raw = raw.replace("{" + e.getKey() + "}", e.getValue());
        }
        return MiniMessage.miniMessage().deserialize(raw);
    }

    public Component msg(String key) {
        return msg(key, Map.of());
    }

    // ---------- автосброс ----------

    public void startTimer() {
        if (autoTask != null) autoTask.cancel();
        int minutes = getConfig().getInt("interval-minutes", 30);
        if (minutes <= 0) return;
        long ticks = minutes * 60L * 20L;
        autoTask = getServer().getScheduler().runTaskTimer(this, () -> {
            if (Bukkit.getOnlinePlayers().size() < getConfig().getInt("min-players", 1)) return;
            callDrop(null, null);
        }, ticks, ticks);
    }

    public List<Drop> drops() {
        return drops;
    }

    // ---------- вызов аирдропа ----------

    private World dropWorld() {
        World w = Bukkit.getWorld(getConfig().getString("world", "world"));
        return w != null ? w : Bukkit.getWorlds().get(0);
    }

    /**
     * Сбросить аирдроп. Если at == null — случайная точка в кольце вокруг центра,
     * иначе над точкой {x, z} в мире world.
     */
    public void callDrop(World forcedWorld, int[] at) {
        if (drops.size() >= getConfig().getInt("max-active", 3)) {
            Bukkit.broadcast(msg("too-many"));
            return;
        }
        World world = forcedWorld != null ? forcedWorld : dropWorld();
        findSpot(world, at, 0, spot -> startFall(world, spot[0], spot[1], spot[2]),
                () -> Bukkit.broadcast(msg("failed")));
    }

    private void findSpot(World w, int[] at, int attempt, Consumer<int[]> ok, Runnable fail) {
        if (attempt >= 15) {
            fail.run();
            return;
        }
        int x;
        int z;
        if (at != null) {
            x = at[0];
            z = at[1];
        } else {
            double cx = getConfig().getDouble("center-x", 0);
            double cz = getConfig().getDouble("center-z", 0);
            double min = getConfig().getDouble("min-radius", 300);
            double max = getConfig().getDouble("max-radius", 1500);
            double r = min + random.nextDouble() * Math.max(0, max - min);
            double a = random.nextDouble() * Math.PI * 2;
            x = (int) Math.round(cx + Math.cos(a) * r);
            z = (int) Math.round(cz + Math.sin(a) * r);
        }
        final int fx = x;
        final int fz = z;
        CompletableFuture<?> c1 = w.getChunkAtAsync(fx >> 4, fz >> 4);
        CompletableFuture<?> c2 = w.getChunkAtAsync((fx + 1) >> 4, fz >> 4);
        CompletableFuture.allOf(c1, c2).thenRun(() ->
                Bukkit.getScheduler().runTask(this, () -> {
                    int[] spot = validate(w, fx, fz);
                    if (spot != null) {
                        ok.accept(spot);
                    } else if (at != null) {
                        fail.run();
                    } else {
                        findSpot(w, null, attempt + 1, ok, fail);
                    }
                }));
    }

    /** Проверяет, что в (x,z) и (x+1,z) ровная твёрдая земля. Возвращает {x, y, z}, где y — уровень сундука. */
    private int[] validate(World w, int x, int z) {
        int h1 = w.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        int h2 = w.getHighestBlockYAt(x + 1, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        if (h1 != h2) return null;
        Block g1 = w.getBlockAt(x, h1, z);
        Block g2 = w.getBlockAt(x + 1, h1, z);
        if (!g1.getType().isSolid() || !g2.getType().isSolid()) return null;
        if (g1.isLiquid() || g2.isLiquid()) return null;
        Block a1 = g1.getRelative(BlockFace.UP);
        Block a2 = g2.getRelative(BlockFace.UP);
        if (!(a1.getType().isAir() || a1.isReplaceable())) return null;
        if (!(a2.getType().isAir() || a2.isReplaceable())) return null;
        return new int[]{x, h1 + 1, z};
    }

    // ---------- падение ----------

    private void startFall(World w, int x, int y, int z) {
        Drop drop = new Drop(w, x, y, z);
        drops.add(drop);

        double height = getConfig().getDouble("fall-height", 100);
        double speed = getConfig().getDouble("fall-speed", 0.3);
        double startY = Math.min(y + height, w.getMaxHeight() - 2);

        drop.display = w.spawn(new org.bukkit.Location(w, x + 0.5, startY, z + 0.5), BlockDisplay.class, d -> {
            d.setBlock(Material.CHEST.createBlockData());
            d.setGlowing(true);
        });

        Bukkit.broadcast(msg("incoming", Map.of("x", String.valueOf(x), "z", String.valueOf(z), "world", w.getName())));

        final double[] cur = {startY};
        drop.fallTask = getServer().getScheduler().runTaskTimer(this, () -> {
            cur[0] -= speed;
            if (cur[0] <= y) {
                land(drop);
                return;
            }
            org.bukkit.Location l = new org.bukkit.Location(w, x + 0.5, cur[0], z + 0.5);
            drop.display.teleport(l);
            w.spawnParticle(Particle.CLOUD, l.clone().add(0.5, 1.0, 0.5), 3, 0.2, 0.2, 0.2, 0.01);
            w.spawnParticle(Particle.FLAME, l.clone().add(0.5, 1.0, 0.5), 1, 0.1, 0.1, 0.1, 0.0);
        }, 0L, 1L);
    }

    private void land(Drop drop) {
        if (drop.fallTask != null) drop.fallTask.cancel();
        if (drop.display != null) drop.display.remove();

        World w = drop.world;
        Block right = w.getBlockAt(drop.x, drop.y, drop.z);
        Block left = w.getBlockAt(drop.x + 1, drop.y, drop.z);

        // Двойной сундук: смотрим на север — левая половина справа от зрителя на востоке (x+1).
        placeDouble(right, left, Chest.Type.RIGHT, Chest.Type.LEFT);
        if (!(right.getState() instanceof org.bukkit.block.Chest st) || st.getInventory().getSize() != 54) {
            placeDouble(right, left, Chest.Type.LEFT, Chest.Type.RIGHT);
        }

        if (right.getState() instanceof org.bukkit.block.Chest st) {
            Inventory inv = st.getInventory();
            inv.clear();
            ItemStack[] loot = LootBuilder.build(getConfig().getConfigurationSection("loot"), getLogger());
            for (int i = 0; i < loot.length && i < inv.getSize(); i++) {
                if (loot[i] != null) inv.setItem(i, loot[i]);
            }
        }

        drop.landed = true;
        org.bukkit.Location c = new org.bukkit.Location(w, drop.x + 1.0, drop.y + 0.5, drop.z + 0.5);
        w.playSound(c, Sound.BLOCK_ANVIL_LAND, SoundCategory.BLOCKS, 1.0f, 0.8f);
        w.playSound(c, Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, SoundCategory.BLOCKS, 1.0f, 1.0f);
        w.spawnParticle(Particle.CLOUD, c, 40, 0.8, 0.4, 0.8, 0.05);

        Bukkit.broadcast(msg("landed", Map.of("x", String.valueOf(drop.x), "y", String.valueOf(drop.y),
                "z", String.valueOf(drop.z))));

        long life = getConfig().getLong("lifetime-minutes", 15) * 60L * 20L;
        if (life > 0) {
            drop.expireTask = getServer().getScheduler().runTaskLater(this, () -> {
                if (!drops.contains(drop)) return;
                removeDrop(drop);
                Bukkit.broadcast(msg("expired", Map.of("x", String.valueOf(drop.x), "z", String.valueOf(drop.z))));
            }, life);
        }
    }

    private void placeDouble(Block a, Block b, Chest.Type typeA, Chest.Type typeB) {
        a.setType(Material.CHEST, false);
        b.setType(Material.CHEST, false);
        Chest da = (Chest) a.getBlockData();
        Chest db = (Chest) b.getBlockData();
        da.setFacing(BlockFace.NORTH);
        db.setFacing(BlockFace.NORTH);
        da.setType(typeA);
        db.setType(typeB);
        a.setBlockData(da, false);
        b.setBlockData(db, false);
    }

    // ---------- удаление ----------

    public void removeDrop(Drop drop) {
        drops.remove(drop);
        if (drop.fallTask != null) drop.fallTask.cancel();
        if (drop.expireTask != null) drop.expireTask.cancel();
        if (drop.display != null) drop.display.remove();
        if (drop.landed) {
            Block right = drop.world.getBlockAt(drop.x, drop.y, drop.z);
            Block left = drop.world.getBlockAt(drop.x + 1, drop.y, drop.z);
            if (right.getState() instanceof org.bukkit.block.Chest st) {
                st.getInventory().clear();
            }
            right.setType(Material.AIR, false);
            left.setType(Material.AIR, false);
        }
    }
}
