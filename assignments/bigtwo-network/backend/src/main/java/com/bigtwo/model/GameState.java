package com.bigtwo.model;

public enum GameState {
    WAITING("等待人数"),
    READY("准备就绪"),
    IN_PROGRESS("游戏中"),
    FINISHED("已结束");

    public final String displayName;

    GameState(String displayName) {
        this.displayName = displayName;
    }
}
