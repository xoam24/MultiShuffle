package cz.xoam24.multishuffle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class GameSession {

    public enum Type { ITEM, BLOCK }
    public enum Mode { RANDOM, SAME }

    private final Type type;
    private final Mode mode;
    private final int maxRounds;
    private int currentRound;
    private int remainingSeconds;

    private final Map<UUID, Integer> playerPoints        = new HashMap<>();
    private final Map<UUID, String>  currentTargets      = new HashMap<>();
    private final Set<UUID>          finishedThisRound   = new HashSet<>();

    // Per-player history — každý target se hráči přiřadí max jednou za hru
    private final Map<UUID, List<String>> usedTargets = new HashMap<>();

    // Sdílený pool pro SAME mode
    private List<String> sharedPool  = null;
    private int          sharedIndex = 0;

    // ── konstruktor ──────────────────────────────────────────────────────────

    public GameSession(Type type, Mode mode, int maxRounds, int initialSeconds) {
        this.type             = type;
        this.mode             = mode;
        this.maxRounds        = maxRounds;
        this.remainingSeconds = initialSeconds;
        this.currentRound     = 1;
    }

    // ── základní gettery/settery ─────────────────────────────────────────────

    public Type getType()                          { return type; }
    public Mode getMode()                          { return mode; }
    public int  getCurrentRound()                  { return currentRound; }
    public void incrementRound()                   { currentRound++; }
    public int  getMaxRounds()                     { return maxRounds; }
    public int  getRemainingSeconds()              { return remainingSeconds; }
    public void setRemainingSeconds(int seconds)   { remainingSeconds = seconds; }
    public void decrementTime()                    { remainingSeconds--; }

    // ── body ─────────────────────────────────────────────────────────────────

    public int getPoints(UUID uuid)                { return playerPoints.getOrDefault(uuid, 0); }
    public void addPoint(UUID uuid)                { playerPoints.merge(uuid, 1, Integer::sum); }
    public Map<UUID, Integer> getAllPoints()        { return Collections.unmodifiableMap(playerPoints); }

    // ── aktuální targety ─────────────────────────────────────────────────────

    public String getTarget(UUID uuid)                    { return currentTargets.get(uuid); }
    public void   setTarget(UUID uuid, String materialKey){ currentTargets.put(uuid, materialKey); }

    // ── stav kola ────────────────────────────────────────────────────────────

    public void    markFinished(UUID uuid)       { finishedThisRound.add(uuid); }
    public boolean hasFinished(UUID uuid)        { return finishedThisRound.contains(uuid); }
    public void    resetFinishedThisRound()      { finishedThisRound.clear(); }

    // ── unique target picking ─────────────────────────────────────────────────

    /**
     * RANDOM mode: vrátí target, který daný hráč v této hře ještě nedostal.
     * Pokud jsou všechny vyčerpané, pool se resetuje a znovu zamíchá.
     */
    public String pickUniqueTargetForPlayer(UUID uuid, List<String> fullPool) {
        if (fullPool == null || fullPool.isEmpty()) return null;

        List<String> used      = usedTargets.computeIfAbsent(uuid, k -> new ArrayList<>());
        List<String> available = new ArrayList<>(fullPool);
        available.removeAll(used);

        if (available.isEmpty()) {
            used.clear();
            available.addAll(fullPool);
        }

        Collections.shuffle(available);
        String chosen = available.get(0);
        used.add(chosen);
        return chosen;
    }

    /**
     * SAME mode: inicializuje sdílený zamíchaný pool (volat jednou při startu hry).
     */
    public void initSharedPool(List<String> fullPool) {
        sharedPool  = new ArrayList<>(fullPool);
        Collections.shuffle(sharedPool);
        sharedIndex = 0;
    }

    /**
     * SAME mode: vrátí next target ze sdíleného poolu (bez opakování).
     * Po vyčerpání se pool znovu zamíchá.
     */
    public String pickNextSharedTarget() {
        if (sharedPool == null || sharedPool.isEmpty()) return null;
        if (sharedIndex >= sharedPool.size()) {
            Collections.shuffle(sharedPool);
            sharedIndex = 0;
        }
        return sharedPool.get(sharedIndex++);
    }

    // ── lifecycle ─────────────────────────────────────────────────────────────

    /** Uklidí veškerá data hráče (disconnect, atd.). */
    public void removePlayer(UUID uuid) {
        usedTargets.remove(uuid);
        currentTargets.remove(uuid);
        playerPoints.remove(uuid);
        finishedThisRound.remove(uuid);
    }
}