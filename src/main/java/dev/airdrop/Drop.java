package dev.airdrop;

import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.scheduler.BukkitTask;

/** Один активный аирдроп. Большой сундук занимает блоки (x, y, z) и (x + 1, y, z). */
public final class Drop {
    public final World world;
    public final int x;
    public final int y;
    public final int z;

    public BlockDisplay display;
    public BukkitTask fallTask;
    public BukkitTask expireTask;
    public boolean landed;

    public Drop(World world, int x, int y, int z) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public boolean isChestBlock(int bx, int by, int bz) {
        return landed && by == y && bz == z && (bx == x || bx == x + 1);
    }
}
