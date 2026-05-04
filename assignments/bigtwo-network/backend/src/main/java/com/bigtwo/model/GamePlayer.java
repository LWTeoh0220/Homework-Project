package com.bigtwo.model;

import java.util.*;

public class GamePlayer {
    private String playerId;
    private String playerName;
    private List<Card> hand;
    private int seatIndex;
    private boolean ready;
    private boolean bot;
    private long score;
    private int winCount;
    
    public GamePlayer(String playerId, String playerName, int seatIndex) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.seatIndex = seatIndex;
        this.hand = new ArrayList<>();
        this.ready = false;
        this.bot = false;
        this.score = 0;
        this.winCount = 0;
    }

    public GamePlayer(String playerId, String playerName, int seatIndex, boolean bot) {
        this(playerId, playerName, seatIndex);
        this.bot = bot;
    }

    public void addCard(Card card) {
        hand.add(card);
        Collections.sort(hand);
    }

    public void addCards(List<Card> cards) {
        hand.addAll(cards);
        Collections.sort(hand);
    }

    public void removeCard(Card card) {
        hand.remove(card);
    }

    public void removeCards(List<Card> cards) {
        hand.removeAll(cards);
    }

    public List<Card> getHand() {
        return new ArrayList<>(hand);
    }

    public void setHand(List<Card> newHand) {
        this.hand = new ArrayList<>(newHand);
        Collections.sort(this.hand);
    }

    public int getHandSize() {
        return hand.size();
    }

    // Getters & Setters
    public String getPlayerId() { return playerId; }
    public String getPlayerName() { return playerName; }
    public int getSeatIndex() { return seatIndex; }
    public boolean isReady() { return ready; }
    public void setReady(boolean ready) { this.ready = ready; }
    public boolean isBot() { return bot; }
    public void setBot(boolean bot) { this.bot = bot; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }
    public long getScore() { return score; }
    public void addScore(long points) { this.score += points; }
    public int getWinCount() { return winCount; }
    public void addWinCount(int count) { this.winCount += count; }
}
