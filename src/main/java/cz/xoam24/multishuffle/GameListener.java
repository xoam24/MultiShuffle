package cz.xoam24.multishuffle;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;

public class GameListener implements Listener {

    private final MultiShuffle plugin;

    public GameListener(MultiShuffle plugin) { this.plugin = plugin; }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        GameSession session = plugin.getGameManager().getCurrentSession();
        if (session != null && session.getType() == GameSession.Type.ITEM) {
            plugin.getGameManager().handleSuccess(player, event.getItem().getItemStack().getType());
        }
    }

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        GameSession session = plugin.getGameManager().getCurrentSession();
        if (session != null && session.getType() == GameSession.Type.ITEM) {
            ItemStack result = event.getCurrentItem();
            if (result != null) plugin.getGameManager().handleSuccess(player, result.getType());
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        GameSession session = plugin.getGameManager().getCurrentSession();
        if (session != null && session.getType() == GameSession.Type.ITEM) {
            ItemStack current = event.getCurrentItem();
            if (current != null) plugin.getGameManager().handleSuccess(player, current.getType());
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        GameSession session = plugin.getGameManager().getCurrentSession();
        if (session == null || session.getType() != GameSession.Type.BLOCK) return;

        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
                event.getFrom().getBlockY() == event.getTo().getBlockY() &&
                event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        Block blockUnderFeet = event.getTo().getBlock().getRelative(BlockFace.DOWN);

        // BlockShuffle úspěch se posílá přímo do GameManageru
        plugin.getGameManager().handleSuccess(player, blockUnderFeet.getType());
    }
}