package cz.xoam24.multishuffle;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

public class GameListener implements Listener {

    private final MultiShuffle plugin;

    public GameListener(MultiShuffle plugin) {
        this.plugin = plugin;
    }

    // ── ItemShuffle ───────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        GameSession s = plugin.getGameManager().getCurrentSession();
        if (s == null || s.getType() != GameSession.Type.ITEM) return;
        plugin.getGameManager().handleSuccess(player, event.getItem().getItemStack().getType());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        GameSession s = plugin.getGameManager().getCurrentSession();
        if (s == null || s.getType() != GameSession.Type.ITEM) return;
        ItemStack result = event.getCurrentItem();
        if (result != null) plugin.getGameManager().handleSuccess(player, result.getType());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        GameSession s = plugin.getGameManager().getCurrentSession();
        if (s == null || s.getType() != GameSession.Type.ITEM) return;
        ItemStack current = event.getCurrentItem();
        if (current != null) plugin.getGameManager().handleSuccess(player, current.getType());
    }

    // ── BlockShuffle ──────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        GameSession s = plugin.getGameManager().getCurrentSession();
        if (s == null || s.getType() != GameSession.Type.BLOCK) return;

        // Optimalizace: ignoruj otočení hlavy bez posunu bloku
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;

        Block blockUnder = event.getTo().getBlock().getRelative(BlockFace.DOWN);
        plugin.getGameManager().handleSuccess(event.getPlayer(), blockUnder.getType());
    }

    // ── join / quit ───────────────────────────────────────────────────────────

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player      player  = event.getPlayer();
        GameSession session = plugin.getGameManager().getCurrentSession();
        if (session == null) return;

        plugin.getGameManager().assignTargetForPlayer(player);
        plugin.getScoreboardManager().createBoardForPlayer(player, session);
        plugin.getScoreboardManager().updateScoreboards();
        plugin.getSoundManager().play("player_join_game", player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        GameSession session = plugin.getGameManager().getCurrentSession();
        if (session != null) session.removePlayer(event.getPlayer().getUniqueId());
    }
}