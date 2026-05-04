package com.bigtwo.model;

/**
 * 牌的表示
 * 使用 0-51 的 power 值编码：
 * power = suit * 13 + rank
 * suit: 0-3 (♣♦♥♠)
 * rank: 0-12 (3-A)
 */
public class Card implements Comparable<Card> {
    private int power;

    public Card(int power) {
        this.power = power;
    }

    public Card(String suit, String rank) {
        int suitValue = switch (suit) {
            case "C" -> 0; // Club ♣
            case "D" -> 1; // Diamond ♦
            case "H" -> 2; // Heart ♥
            case "S" -> 3; // Spade ♠
            default -> 0;
        };
        
        int rankValue = switch (rank.toUpperCase()) {
            case "3" -> 0;
            case "4" -> 1;
            case "5" -> 2;
            case "6" -> 3;
            case "7" -> 4;
            case "8" -> 5;
            case "9" -> 6;
            case "10" -> 7;
            case "J" -> 8;
            case "Q" -> 9;
            case "K" -> 10;
            case "A" -> 11;
            case "2" -> 12;
            default -> 0;
        };
        
        this.power = suitValue * 13 + rankValue;
    }

    public int getPower() {
        return power;
    }

    public int getSuit() {
        return power / 13;
    }

    public int getRank() {
        return power % 13;
    }

    public String getSuitSymbol() {
        return switch (getSuit()) {
            case 0 -> "♣";
            case 1 -> "♦";
            case 2 -> "♥";
            case 3 -> "♠";
            default -> "?";
        };
    }

    public String getRankName() {
        return switch (getRank()) {
            case 0 -> "3";
            case 1 -> "4";
            case 2 -> "5";
            case 3 -> "6";
            case 4 -> "7";
            case 5 -> "8";
            case 6 -> "9";
            case 7 -> "10";
            case 8 -> "J";
            case 9 -> "Q";
            case 10 -> "K";
            case 11 -> "A";
            case 12 -> "2";
            default -> "?";
        };
    }

    @Override
    public String toString() {
        return getSuitSymbol() + getRankName();
    }

    @Override
    public int compareTo(Card other) {
        int thisStrength = this.getRank() * 4 + this.getSuit();
        int otherStrength = other.getRank() * 4 + other.getSuit();
        return Integer.compare(thisStrength, otherStrength);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Card && this.power == ((Card) obj).power;
    }

    @Override
    public int hashCode() {
        return power;
    }
}
