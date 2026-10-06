package dev.airdrop;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DropCommand implements TabExecutor {
    private final AirDropPlugin plugin;

    public DropCommand(AirDropPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            plugin.callDrop(null, null);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "here" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(plugin.msg("only-player"));
                    return true;
                }
                plugin.callDrop(p.getWorld(), new int[]{p.getLocation().getBlockX(), p.getLocation().getBlockZ()});
            }
            case "reload" -> {
                plugin.reloadConfig();
                plugin.startTimer();
                sender.sendMessage(plugin.msg("reloaded"));
            }
            default -> sender.sendMessage(plugin.msg("usage"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : List.of("here", "reload")) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
            }
        }
        return out;
    }
}
