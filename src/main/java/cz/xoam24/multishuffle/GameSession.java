package cz.xoam24.multishuffle;

import java.util.*;

/**
 * Datový kontejner pro jednu běžící hru.
 * Žádná logika — pouze data + čisté utility metody.
 */
public class GameSession {

    public enum Type { ITEM, BLOCK }
    public enum Mode { RANDOM, SAME }

    // ── identita hry ──────────────────────────────────────────────────────────

    private final Type type;
    private final Mode mode;
    private int  maxRounds;
    private int  currentRound     = 1;
    private int  remainingSeconds;

    /**
     * TRUE během doby kdy timer dojel na 0 a čeká se na spuštění nového kola.
     * Guard proti double-fire round-end logiky.
     */
    private volatile boolean transitioning = false;

    // ── herní data ────────────────────────────────────────────────────────────

    /** UUID → accumulated points (přetrvávají přes všechna kola až do konce hry). */
    private final Map<UUID, Integer>      points            = new HashMap<>();
    /** UUID → current target material key (např. "minecraft:oak_log"). */
    private final Map<UUID, String>       targets           = new HashMap<>();
    /** Hráči kteří splnili cíl v tomto kole. */
    private final Set<UUID>               finishedThisRound = new HashSet<>();
    /** UUID → seznam targetů použitých v TÉTO hře (pro unique picking). */
    private final Map<UUID, List<String>> usedTargets       = new HashMap<>();

    // Sdílený pool pro SAME mode
    private List<String> sharedPool  = null;
    private int          sharedIndex = 0;

    // ── konstruktor ──────────────────────────────────────────────────────────

    public GameSession(Type type, Mode mode, int maxRounds, int initialSeconds) {
        this.type             = type;
        this.mode             = mode;
        this.maxRounds        = maxRounds;
        this.remainingSeconds = initialSeconds;
    }

    // ── základní gettery / settery ────────────────────────────────────────────

    public Type    getType()                          { return type; }
    public Mode    getMode()                          { return mode; }
    public int     getCurrentRound()                  { return currentRound; }
    public void    incrementRound()                   { currentRound++; }
    public int     getMaxRounds()                     { return maxRounds; }
    public void    setMaxRounds(int v)                { maxRounds = Math.max(currentRound, v); }
    public int     getRemainingSeconds()              { return remainingSeconds; }
    public void    setRemainingSeconds(int s)         { remainingSeconds = s; }
    public void    decrementTime()                    { remainingSeconds--; }
    public boolean isTransitioning()                  { return transitioning; }
    public void    setTransitioning(boolean v)        { transitioning = v; }

    // ── body ─────────────────────────────────────────────────────────────────

    public int  getPoints(UUID uuid)       { return points.getOrDefault(uuid, 0); }
    public void addPoint(UUID uuid)        { points.merge(uuid, 1, Integer::sum); }
    public void setPoints(UUID uuid, int v){ points.put(uuid, Math.max(0, v)); }
    public Map<UUID, Integer> getAllPoints(){ return Collections.unmodifiableMap(points); }

    // ── targety ───────────────────────────────────────────────────────────────

    public String getTarget(UUID uuid)                     { return targets.get(uuid); }
    public void   setTarget(UUID uuid, String materialKey) { targets.put(uuid, materialKey); }
    public void   clearTarget(UUID uuid)                   { targets.remove(uuid); }

    // ── stav kola ─────────────────────────────────────────────────────────────

    public void    markFinished(UUID uuid) { finishedThisRound.add(uuid); }
    public boolean hasFinished(UUID uuid)  { return finishedThisRound.contains(uuid); }

    /** Reset stavu kola + uvolnění transition guardu. */
    public void resetRound() {
        finishedThisRound.clear();
        transitioning = false;
    }

    // ── unique target picking ─────────────────────────────────────────────────

    /**
     * RANDOM mode: vrátí target který hráč v TÉTO hře ještě nedostal.
     * Pokud jsou všechny vyčerpány, pool se resetuje a znovu zamíchá.
     */
    public String pickUniqueTarget(UUID uuid, List<String> pool) {
        if (pool == null || pool.isEmpty()) return null;

        List<String> used      = usedTargets.computeIfAbsent(uuid, k -> new ArrayList<>());
        List<String> available = new ArrayList<>(pool);
        available.removeAll(used);

        if (available.isEmpty()) {
            used.clear();
            available.addAll(pool);
        }

        Collections.shuffle(available);
        String chosen = available.get(0);
        used.add(chosen);
        return chosen;
    }

    /** SAME mode: inicializuje sdílený zamíchaný pool (volat jednou při startu hry). */
    public void initSharedPool(List<String> pool) {
        sharedPool  = new ArrayList<>(pool);
        Collections.shuffle(sharedPool);
        sharedIndex = 0;
    }

    /** SAME mode: vrátí další target ze sdíleného poolu. Po vyčerpání znovu zamíchá. */
    public String pickNextShared() {
        if (sharedPool == null || sharedPool.isEmpty()) return null;
        if (sharedIndex >= sharedPool.size()) {
            Collections.shuffle(sharedPool);
            sharedIndex = 0;
        }
        return sharedPool.get(sharedIndex++);
    }

    // ── lifecycle ─────────────────────────────────────────────────────────────

    /** Uklidí veškerá data hráče (disconnect). Body záměrně zůstávají. */
    public void removePlayer(UUID uuid) {
        targets.remove(uuid);
        finishedThisRound.remove(uuid);
        usedTargets.remove(uuid);
        // points záměrně NEmazat — hráč se může vrátit
    }
}