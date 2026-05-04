package com.bigtwo.model;

public enum HandType {
    SINGLE(1, "单牌"),
    PAIR(2, "对子"),
    STRAIGHT(3, "顺子"),
    FLUSH(4, "同花"),
    FULL_HOUSE(5, "葫芦"),
    FOUR_OF_A_KIND(6, "铁支"),
    STRAIGHT_FLUSH(7, "同花顺"),
    INVALID(0, "无效");

    public final int level;
    public final String name;

    HandType(int level, String name) {
        this.level = level;
        this.name = name;
    }
}
