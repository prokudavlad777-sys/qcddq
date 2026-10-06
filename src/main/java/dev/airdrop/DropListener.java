package dev.airdrop;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Map;

public final class DropListener implements Listener {
    private final AirDropPlugin plugin;

    public DropListener(AirDropPlugin plugin) {
        this.plugin = plugin;
    }

    private Drop dropAt(Block b) {
        for (Drop d : plugin.drops()) {
            if (d.world.equals(b.getWorld()) && d.isChestBlock(b.getX(), b.getY(), b.getZ())) return d;
        }
        return null;
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        if (dropAt(e.getBlock()) == null) return;
        e.setCancelled(true);
        e.getPlayer().sendActionBar(Component.text("Сундук аирдропа нельзя сломать — забери лут из него", NamedTextColor.RED));
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(b -> dropAt(b) != null);
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(b -> dropAt(b) != null);
    }

    /** Когда сундук полностью опустел — убираем его. */
    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        Inventory inv = e.getInventory();
        Location loc = inv.getLocation();
        if (loc == null || loc.getWorld() == null) return;

        for (Drop d : new ArrayList<>(plugin.drops())) {
            if (!d.landed || !d.world.equals(loc.getWorld())) continue;
            Location center = new Location(d.world, d.x + 1.0, d.y + 0.5, d.z + 0.5);
            if (center.distanceSquared(loc) > 4.0) continue;

            boolean empty = true;
            for (ItemStack it : inv.getContents()) {
                if (it != null && !it.getType().isAir()) {
                    empty = false;
                    break;
                }
            }
            if (empty) {
                plugin.removeDrop(d);
                Bukkit.broadcast(plugin.msg("looted", Map.of("x", String.valueOf(d.x), "z", String.valueOf(d.z))));
            }
            return;
        }
    }
}
