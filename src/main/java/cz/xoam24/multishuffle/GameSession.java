package cz.xoam24.multishuffle;

import java.util.HashMap;
import java.util.HashSet;
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
}