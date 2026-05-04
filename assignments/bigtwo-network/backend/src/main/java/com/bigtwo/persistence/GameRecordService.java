package com.bigtwo.persistence;

import com.bigtwo.model.GamePlayer;
import com.bigtwo.model.GameRoom;
import com.google.gson.Gson;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GameRecordService {
    private static final Logger logger = LoggerFactory.getLogger(GameRecordService.class);
    private static final Object TABLE_INIT_LOCK = new Object();

    private final JdbcTemplate jdbcTemplate;
    private final Gson gson;

    public GameRecordService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.gson = new Gson();
    }

    @PostConstruct
    public void initTable() {
        synchronized (TABLE_INIT_LOCK) {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS game_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        room_id TEXT NOT NULL,
                        room_name TEXT NOT NULL,
                        winner_id TEXT NOT NULL,
                        winner_name TEXT NOT NULL,
                        players_json TEXT NOT NULL,
                        table_cards_json TEXT NOT NULL,
                        ended_at INTEGER NOT NULL
                    )
                    """);
        }
    }

    public void recordGameResult(GameRoom room, GamePlayer winner) {
        try {
            String playersJson = gson.toJson(buildPlayersSummary(room.getPlayers()));
            String tableCardsJson = gson.toJson(room.getTableCards().stream().map(card -> card.getPower()).toList());

            jdbcTemplate.update(
                    """
                    INSERT INTO game_records (
                        room_id,
                        room_name,
                        winner_id,
                        winner_name,
                        players_json,
                        table_cards_json,
                        ended_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?)
                    """,
                    room.getRoomId(),
                    room.getRoomName(),
                    winner.getPlayerId(),
                    winner.getPlayerName(),
                    playersJson,
                    tableCardsJson,
                    System.currentTimeMillis()
            );
        } catch (Exception e) {
            logger.error("写入 SQLite 对局记录失败: room={}", room.getRoomId(), e);
        }
    }

    private List<Map<String, Object>> buildPlayersSummary(List<GamePlayer> players) {
        return players.stream().map(player -> {
            Map<String, Object> map = new HashMap<>();
            map.put("playerId", player.getPlayerId());
            map.put("name", player.getPlayerName());
            map.put("seatIndex", player.getSeatIndex());
            map.put("remainingCards", player.getHandSize());
            map.put("score", player.getScore());
            return map;
        }).toList();
    }
}