package com.bigtwo.model;

import java.util.*;

public class GameRoom {
    private String roomId;
    private String roomName;
    private List<GamePlayer> players;
    private GameState gameState;
    private long createdAt;
    private long updatedAt;
    private int maxPlayers = 4;
    private List<Card> tableCards;
    private int currentPlayer;
    private int dealerIndex;
    private boolean isFirstTurn;
    private int passCount;
    private String lastAction;
    private Map<String, Object> lastRoundSummary;
    
    public GameRoom(String roomId, String roomName) {
        this.roomId = roomId;
        this.roomName = roomName;
        this.players = new ArrayList<>();
        this.gameState = GameState.WAITING;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
        this.tableCards = new ArrayList<>();
        this.currentPlayer = -1;
        this.dealerIndex = -1;
        this.isFirstTurn = true;
        this.passCount = 0;
        this.lastAction = "等待开始";
        this.lastRoundSummary = new HashMap<>();
    }

    public boolean isFull() {
        return players.size() >= maxPlayers;
    }

    public boolean canStart() {
        return players.size() == maxPlayers && gameState == GameState.WAITING;
    }

    public void addPlayer(GamePlayer player) {
        if (players.size() < maxPlayers) {
            players.add(player);
            updatedAt = System.currentTimeMillis();
        }
    }

    public void removePlayer(String playerId) {
        players.removeIf(p -> p.getPlayerId().equals(playerId));
        updatedAt = System.currentTimeMillis();
    }

    public GamePlayer findPlayer(String playerId) {
        return players.stream()
            .filter(p -> p.getPlayerId().equals(playerId))
            .findFirst()
            .orElse(null);
    }

    public List<GamePlayer> getPlayersExcept(String playerId) {
        return players.stream()
            .filter(p -> !p.getPlayerId().equals(playerId))
            .toList();
    }

    // Getters
    public String getRoomId() { return roomId; }
    public String getRoomName() { return roomName; }
    public List<GamePlayer> getPlayers() { return new ArrayList<>(players); }
    public GameState getGameState() { return gameState; }
    public void setGameState(GameState state) { this.gameState = state; updatedAt = System.currentTimeMillis(); }
    public long getCreatedAt() { return createdAt; }
    public long getUpdatedAt() { return updatedAt; }
    public List<Card> getTableCards() { return new ArrayList<>(tableCards); }
    public void setTableCards(List<Card> cards) { this.tableCards = new ArrayList<>(cards); }
    public int getCurrentPlayer() { return currentPlayer; }
    public void setCurrentPlayer(int index) { this.currentPlayer = index; }
    public int getDealerIndex() { return dealerIndex; }
    public void setDealerIndex(int index) { this.dealerIndex = index; }
    public boolean isFirstTurn() { return isFirstTurn; }
    public void setFirstTurn(boolean first) { this.isFirstTurn = first; }
    public int getPassCount() { return passCount; }
    public void setPassCount(int count) { this.passCount = count; }
    public String getLastAction() { return lastAction; }
    public void setLastAction(String lastAction) { this.lastAction = lastAction; }
    public Map<String, Object> getLastRoundSummary() { return new HashMap<>(lastRoundSummary); }
    public void setLastRoundSummary(Map<String, Object> summary) { this.lastRoundSummary = new HashMap<>(summary); }
}
