package cz.xoam24.multishuffle;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

public class GameListener implements Listener {

    private final MultiShuffle plugin;

    public GameListener(MultiShuffle plugin) {
        this.plugin = plugin;
    }

    // ── ItemShuffle — detekce úspěchu ─────────────────────────────────────────

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

    // ── Ochrana cílového itemu/bloku po splnění ───────────────────────────────
    //
    // Pravidlo: pokud hráč splnil cíl v tomto kole (hasFinished = true),
    // nesmí svůj cílový item/block vyhodit z inventáře ani přesunout
    // do jiného inventáře. Toto platí pro oba módy (ITEM i BLOCK).
    // Ochrana se automaticky zruší na začátku nového kola (resetRound()).

    /**
     * Blokuje vyhazování cílového itemu klávesou Q nebo drag&drop mimo inventář.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(PlayerDropItemEvent event) {
        if (!isProtectionEnabled()) return;
        GameSession s = plugin.getGameManager().getCurrentSession();
        if (s == null) return;

        Player player = event.getPlayer();
        if (!s.hasFinished(player.getUniqueId())) return;

        Material dropped = event.getItemDrop().getItemStack().getType();
        if (isTargetMaterial(s, player, dropped)) {
            event.setCancelled(true);
            sendProtectionWarning(player);
        }
    }

    /**
     * Blokuje přesouvání cílového itemu do jiného inventáře (chest, hopper, atd.)
     * a vyhazování přes shift-click ven z inventáře.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClickProtect(InventoryClickEvent event) {
        if (!isProtectionEnabled()) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        GameSession s = plugin.getGameManager().getCurrentSession();
        if (s == null) return;
        if (!s.hasFinished(player.getUniqueId())) return;

        // Kontrolujeme jen situace kdy item OPOUŠTÍ hráčův inventář
        ItemStack moved = resolveMovedItem(event);
        if (moved == null || moved.getType().isAir()) return;

        if (isTargetMaterial(s, player, moved.getType())) {
            // Pohyb do externího inventáře (chest, crafting, hopper…)
            boolean movingToExternal = event.getClickedInventory() != null
                    && event.getClickedInventory().getType() == InventoryType.PLAYER
                    && event.getView().getTopInventory().getType() != InventoryType.PLAYER
                    && event.getView().getTopInventory().getType() != InventoryType.CRAFTING;

            // Shift-click z hráčova inventáře do externího
            boolean shiftToExternal = event.isShiftClick()
                    && event.getClickedInventory() != null
                    && event.getClickedInventory().getType() == InventoryType.PLAYER
                    && event.getView().getTopInventory().getType() != InventoryType.PLAYER
                    && event.getView().getTopInventory().getType() != InventoryType.CRAFTING;

            if (movingToExternal || shiftToExternal) {
                event.setCancelled(true);
                sendProtectionWarning(player);
            }
        }
    }

    /**
     * Blokuje drag přes cílový item do externího inventáře.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryDragProtect(InventoryDragEvent event) {
        if (!isProtectionEnabled()) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        GameSession s = plugin.getGameManager().getCurrentSession();
        if (s == null) return;
        if (!s.hasFinished(player.getUniqueId())) return;

        ItemStack dragged = event.getOldCursor();
        if (dragged == null || dragged.getType().isAir()) return;

        if (isTargetMaterial(s, player, dragged.getType())) {
            // Kontroluj jestli se drag táhne do externího inventáře
            boolean hasExternalSlot = event.getRawSlots().stream()
                    .anyMatch(slot -> slot < event.getView().getTopInventory().getSize()
                            && event.getView().getTopInventory().getType() != InventoryType.PLAYER
                            && event.getView().getTopInventory().getType() != InventoryType.CRAFTING);
            if (hasExternalSlot) {
                event.setCancelled(true);
                sendProtectionWarning(player);
            }
        }
    }

    // ── BlockShuffle — detekce úspěchu ────────────────────────────────────────

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

    // ── helpers ───────────────────────────────────────────────────────────────

    /** Vrátí true pokud je ochrana zapnuta v configu. */
    private boolean isProtectionEnabled() {
        return plugin.getConfigManager().getConfig()
                .getBoolean("item_protection.enabled", true);
    }

    /**
     * Vrátí true pokud daný material odpovídá cílovému itemu/bloku hráče
     * v aktuálním kole.
     */
    private boolean isTargetMaterial(GameSession s, Player player, Material material) {
        String targetKey = s.getTarget(player.getUniqueId());
        if (targetKey == null) return false;
        Material targetMat = Material.matchMaterial(targetKey);
        return targetMat != null && targetMat == material;
    }

    /**
     * Určí který item se pohybuje při InventoryClickEvent.
     * Pokrývá: normální klik (currentItem), number key swap, shift-click.
     */
    private ItemStack resolveMovedItem(InventoryClickEvent event) {
        return switch (event.getAction()) {
            case MOVE_TO_OTHER_INVENTORY -> event.getCurrentItem();
            case PLACE_ALL, PLACE_ONE, PLACE_SOME -> event.getCursor();
            case SWAP_WITH_CURSOR -> event.getCurrentItem();
            case HOTBAR_SWAP, HOTBAR_MOVE_AND_READD -> {
                if (event.getHotbarButton() >= 0 && event.getWhoClicked() instanceof Player p) {
                    yield p.getInventory().getItem(event.getHotbarButton());
                }
                yield null;
            }
            default -> event.getCurrentItem();
        };
    }

    /** Pošle hráči varování o ochraně itemu. */
    private void sendProtectionWarning(Player player) {
        player.sendMessage(plugin.getConfigManager().msg("item_protection_blocked"));
    }
}