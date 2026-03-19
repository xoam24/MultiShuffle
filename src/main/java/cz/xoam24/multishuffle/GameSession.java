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
    private int currentRound;
    private int maxRounds;
    private int remainingSeconds;

    private final Map<UUID, Integer> playerPoints = new HashMap<>();
    private final Map<UUID, String> currentTargets = new HashMap<>();
    private final Set<UUID> finishedThisRound = new HashSet<>();

    // Sledování již použitých targetů pro každého hráče (neopakují se dokud se pool nevyčerpá)
    private final Map<UUID, List<String>> usedTargets = new HashMap<>();

    // Pro SAME mode: sdílený zamíchaný pool pro celou hru
    private List<String> sharedShuffledPool = null;
    private int sharedPoolIndex = 0;

    public GameSession(Type type, Mode mode, int maxRounds, int initialTimeSeconds) {
        this.type = type;
        this.mode = mode;
        this.maxRounds = maxRounds;
        this.remainingSeconds = initialTimeSeconds;
        this.currentRound = 1;
    }

    public Type getType() { return type; }
    public Mode getMode() { return mode; }
    public int getCurrentRound() { return currentRound; }
    public void incrementRound() { this.currentRound++; }
    public int getMaxRounds() { return maxRounds; }
    public int getRemainingSeconds() { return remainingSeconds; }
    public void setRemainingSeconds(int remainingSeconds) { this.remainingSeconds = remainingSeconds; }
    public void decrementTime() { this.remainingSeconds--; }

    public int getPoints(UUID uuid) { return playerPoints.getOrDefault(uuid, 0); }
    public void addPoint(UUID uuid) { playerPoints.put(uuid, getPoints(uuid) + 1); }
    public Map<UUID, Integer> getAllPoints() { return playerPoints; }

    public String getTarget(UUID uuid) { return currentTargets.get(uuid); }
    public void setTarget(UUID uuid, String materialKey) { currentTargets.put(uuid, materialKey); }

    public void markFinished(UUID uuid) { finishedThisRound.add(uuid); }
    public boolean hasFinished(UUID uuid) { return finishedThisRound.contains(uuid); }
    public void resetFinishedThisRound() { finishedThisRound.clear(); }

    /**
     * Vrátí unikátní target pro hráče - každý target se přiřadí max jednou
     * dokud se pool nevyčerpá, pak se resetuje a znovu zamíchá.
     */
    public String pickUniqueTargetForPlayer(UUID uuid, List<String> fullPool) {
        if (fullPool == null || fullPool.isEmpty()) return null;

        List<String> used = usedTargets.computeIfAbsent(uuid, k -> new ArrayList<>());

        // Dostupné = celý pool mínus použité
        List<String> available = new ArrayList<>(fullPool);
        available.removeAll(used);

        // Pokud jsou všechny vyčerpané — reset
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
     * Pro SAME mode: inicializuje sdílený zamíchaný pool (zavolat jednou na začátku hry).
     */
    public void initSharedPool(List<String> fullPool) {
        sharedShuffledPool = new ArrayList<>(fullPool);
        Collections.shuffle(sharedShuffledPool);
        sharedPoolIndex = 0;
    }

    /**
     * Pro SAME mode: vrátí next target ze sdíleného poolu bez opakování.
     */
    public String pickNextSharedTarget() {
        if (sharedShuffledPool == null || sharedShuffledPool.isEmpty()) return null;
        if (sharedPoolIndex >= sharedShuffledPool.size()) {
            Collections.shuffle(sharedShuffledPool);
            sharedPoolIndex = 0;
        }
        return sharedShuffledPool.get(sharedPoolIndex++);
    }

    public void removePlayer(UUID uuid) {
        usedTargets.remove(uuid);
        currentTargets.remove(uuid);
        playerPoints.remove(uuid);
        finishedThisRound.remove(uuid);
    }
}