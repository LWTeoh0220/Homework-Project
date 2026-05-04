package com.bigtwo.model;

import java.util.*;

public class GameLogic {
    private static final Comparator<Card> CARD_STRENGTH_COMPARATOR =
            Comparator.comparingInt(GameLogic::cardStrength);

    public static int cardStrength(Card card) {
        return card.getRank() * 4 + card.getSuit();
    }

    public static HandType checkType(List<Card> cards) {
        int size = cards.size();
        if (size == 1) return HandType.SINGLE;
        if (size == 2 && isPair(cards)) return HandType.PAIR;
        if (size == 5) {
            List<Card> sorted = new ArrayList<>(cards);
            Collections.sort(sorted);
            
            boolean isStraight = isStraight(sorted);
            boolean isFlush = isFlush(sorted);
            
            if (isStraight && isFlush) return HandType.STRAIGHT_FLUSH;
            if (isFourOfAKind(sorted)) return HandType.FOUR_OF_A_KIND;
            if (isFullHouse(sorted)) return HandType.FULL_HOUSE;
            if (isFlush) return HandType.FLUSH;
            if (isStraight) return HandType.STRAIGHT;
        }
        return HandType.INVALID;
    }

    private static boolean isPair(List<Card> cards) {
        return cards.get(0).getRank() == cards.get(1).getRank();
    }

    public static boolean isFullHouse(List<Card> cards) {
        if (cards.size() != 5) return false;
        boolean case1 = (cards.get(0).getRank() == cards.get(2).getRank()) && 
                        (cards.get(3).getRank() == cards.get(4).getRank());
        boolean case2 = (cards.get(0).getRank() == cards.get(1).getRank()) && 
                        (cards.get(2).getRank() == cards.get(4).getRank());
        return case1 || case2;
    }

    public static boolean isFourOfAKind(List<Card> cards) {
        if (cards.size() != 5) return false;
        return (cards.get(0).getRank() == cards.get(3).getRank()) || 
               (cards.get(1).getRank() == cards.get(4).getRank());
    }

    public static boolean isStraight(List<Card> cards) {
        if (cards.size() != 5) return false;
        List<Card> sorted = sortByStrength(cards);
        
        int[] ranks = new int[5];
        for (int i = 0; i < 5; i++) {
            ranks[i] = sorted.get(i).getRank();
            if (i > 0 && ranks[i] == ranks[i - 1]) return false;
        }
        
        if (isA2345Straight(ranks)) return true;
        
        if (ranks[4] == 12) return false;
        for (int i = 0; i < 4; i++) {
            if (ranks[i + 1] != ranks[i] + 1) return false;
        }
        return true;
    }

    public static boolean isFlush(List<Card> cards) {
        if (cards.size() != 5) return false;
        int suit = cards.get(0).getSuit();
        for (Card c : cards) {
            if (c.getSuit() != suit) return false;
        }
        return true;
    }

    public static boolean canPlay(List<Card> tableCards, List<Card> playCards) {
        HandType tableType = checkType(tableCards);
        HandType playType = checkType(playCards);
        
        if (tableType == HandType.INVALID && playType != HandType.INVALID) return true;
        if (playType == HandType.INVALID) return false;
        if (tableCards.isEmpty()) return true;
        
        if (playType.level >= 5 && playType.level > tableType.level) return true;
        
        if (tableCards.size() == playCards.size() && tableType == playType) {
            return getLeadingStrength(playCards, playType) > getLeadingStrength(tableCards, tableType);
        }
        
        return false;
    }

    public static int getLeadingStrength(List<Card> cards, HandType type) {
        List<Card> sorted = sortByStrength(cards);
        return switch (type) {
            case SINGLE, PAIR -> cardStrength(sorted.get(sorted.size() - 1));
            case FLUSH -> getFlushLeadingStrength(sorted);
            case STRAIGHT, STRAIGHT_FLUSH -> getStraightLeadingStrength(sorted);
            case FULL_HOUSE -> getFullHouseLeadingStrength(sorted);
            case FOUR_OF_A_KIND -> getFourOfAKindLeadingStrength(sorted);
            default -> -1;
        };
    }

    private static int getStraightLeadingStrength(List<Card> sorted) {
        int[] ranks = new int[5];
        for (int i = 0; i < 5; i++) {
            ranks[i] = sorted.get(i).getRank();
        }
        
        if (isA2345Straight(ranks)) {
            for (Card c : sorted) {
                if (c.getRank() == 12) return cardStrength(c);
            }
        }
        
        return cardStrength(sorted.get(sorted.size() - 1));
    }

    private static int getFlushLeadingStrength(List<Card> sorted) {
        for (int i = sorted.size() - 1; i >= 0; i--) {
            int strength = cardStrength(sorted.get(i));
            if (strength >= 0) {
                return strength;
            }
        }
        return -1;
    }

    private static int getFullHouseLeadingStrength(List<Card> sorted) {
        Map<Integer, List<Card>> byRank = groupByRank(sorted);
        return byRank.values().stream()
                .filter(group -> group.size() == 3)
                .flatMap(List::stream)
                .mapToInt(GameLogic::cardStrength)
                .max()
                .orElse(-1);
    }

    private static int getFourOfAKindLeadingStrength(List<Card> sorted) {
        Map<Integer, List<Card>> byRank = groupByRank(sorted);
        return byRank.values().stream()
                .filter(group -> group.size() == 4)
                .flatMap(List::stream)
                .mapToInt(GameLogic::cardStrength)
                .max()
                .orElse(-1);
    }

    public static List<Card> suggestPlay(List<Card> hand, List<Card> tableCards, boolean firstTurn) {
        return suggestPlayOptions(hand, tableCards, firstTurn).getOrDefault("conservative", List.of());
    }

    public static Map<String, List<Card>> suggestPlayOptions(List<Card> hand, List<Card> tableCards, boolean firstTurn) {
        List<List<Card>> candidates = buildCandidates(hand);
        List<List<Card>> valid = new ArrayList<>();
        boolean tableEmpty = tableCards == null || tableCards.isEmpty();

        for (List<Card> candidate : candidates) {
            if (tableEmpty && firstTurn && candidate.stream().noneMatch(c -> c.getPower() == 0)) {
                continue;
            }
            if (canPlay(tableCards == null ? List.of() : tableCards, candidate)) {
                valid.add(candidate);
            }
        }

        Map<String, List<Card>> options = new LinkedHashMap<>();
        if (valid.isEmpty()) {
            options.put("conservative", List.of());
            options.put("balanced", List.of());
            options.put("aggressive", List.of());
            return options;
        }

        valid = deduplicateCandidates(valid);

        valid.sort((a, b) -> compareSuggestion(a, b, tableEmpty));

        List<List<Card>> byStrengthDesc = new ArrayList<>(valid);
        byStrengthDesc.sort((a, b) -> -compareSuggestion(a, b, tableEmpty));

        List<Card> conservative = new ArrayList<>(valid.get(0));
        List<Card> aggressive = new ArrayList<>(byStrengthDesc.get(0));
        List<Card> balanced = new ArrayList<>(valid.get(valid.size() / 2));

        options.put("conservative", conservative);
        options.put("balanced", balanced);
        options.put("aggressive", aggressive);
        return options;
    }

    private static int compareSuggestion(List<Card> a, List<Card> b, boolean tableEmpty) {
        HandType typeA = checkType(a);
        HandType typeB = checkType(b);
        int leadingA = getLeadingStrength(a, typeA);
        int leadingB = getLeadingStrength(b, typeB);

        if (tableEmpty && a.size() != b.size()) {
            return Integer.compare(a.size(), b.size());
        }

        if (typeA.level != typeB.level) {
            return Integer.compare(typeA.level, typeB.level);
        }
        if (a.size() != b.size()) {
            return Integer.compare(a.size(), b.size());
        }
        return Integer.compare(leadingA, leadingB);
    }

    private static List<List<Card>> buildCandidates(List<Card> hand) {
        List<List<Card>> candidates = new ArrayList<>();
        List<Card> sorted = sortByStrength(hand);

        // singles
        for (Card card : sorted) {
            candidates.add(List.of(card));
        }

        // pairs
        Map<Integer, List<Card>> byRank = groupByRank(sorted);
        for (List<Card> group : byRank.values()) {
            if (group.size() >= 2) {
                for (int i = 0; i < group.size(); i++) {
                    for (int j = i + 1; j < group.size(); j++) {
                        candidates.add(List.of(group.get(i), group.get(j)));
                    }
                }
            }
        }

        // five-card combinations
        for (int i = 0; i < sorted.size(); i++) {
            for (int j = i + 1; j < sorted.size(); j++) {
                for (int k = j + 1; k < sorted.size(); k++) {
                    for (int m = k + 1; m < sorted.size(); m++) {
                        for (int n = m + 1; n < sorted.size(); n++) {
                            List<Card> combo = List.of(sorted.get(i), sorted.get(j), sorted.get(k), sorted.get(m), sorted.get(n));
                            if (checkType(combo) != HandType.INVALID) {
                                candidates.add(combo);
                            }
                        }
                    }
                }
            }
        }

        return candidates;
    }

    private static Map<Integer, List<Card>> groupByRank(List<Card> cards) {
        Map<Integer, List<Card>> byRank = new HashMap<>();
        for (Card card : cards) {
            byRank.computeIfAbsent(card.getRank(), key -> new ArrayList<>()).add(card);
        }
        return byRank;
    }

    private static List<Card> sortByStrength(List<Card> cards) {
        List<Card> sorted = new ArrayList<>(cards);
        sorted.sort(CARD_STRENGTH_COMPARATOR);
        return sorted;
    }

    private static List<List<Card>> deduplicateCandidates(List<List<Card>> candidates) {
        Map<String, List<Card>> unique = new LinkedHashMap<>();
        for (List<Card> cards : candidates) {
            List<Integer> powers = cards.stream().map(Card::getPower).sorted().toList();
            String key = powers.toString();
            unique.putIfAbsent(key, new ArrayList<>(cards));
        }
        return new ArrayList<>(unique.values());
    }

    private static boolean isA2345Straight(int[] ranks) {
        return ranks[0] == 0 && ranks[1] == 1 && ranks[2] == 2 && ranks[3] == 11 && ranks[4] == 12;
    }
}
