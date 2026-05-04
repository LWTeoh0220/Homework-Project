package com.bigtwo.persistence;

import com.bigtwo.model.GamePlayer;
import com.bigtwo.model.GameRoom;
import com.google.gson.Gson;
import org.springframework.beans.factory.ObjectProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RoomStateRedisService {
    private static final Logger logger = LoggerFactory.getLogger(RoomStateRedisService.class);
    private static final String ROOM_KEY_PREFIX = "bigtwo:room:";
    private static final Duration ROOM_TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;
    private final Map<String, String> fallbackSnapshots = new ConcurrentHashMap<>();
    private final Gson gson;

    public RoomStateRedisService(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.redisTemplate = redisTemplateProvider.getIfAvailable();
        this.gson = new Gson();
    }

    public void saveRoomState(GameRoom room) {
        try {
            String key = ROOM_KEY_PREFIX + room.getRoomId();
            String value = gson.toJson(buildSnapshot(room));
            if (redisTemplate != null) {
                redisTemplate.opsForValue().set(key, value, ROOM_TTL);
            }
            fallbackSnapshots.put(key, value);
        } catch (Exception e) {
            logger.warn("保存房间状态到 Redis 失败: {}", room.getRoomId(), e);
            fallbackSnapshots.put(ROOM_KEY_PREFIX + room.getRoomId(), gson.toJson(buildSnapshot(room)));
        }
    }

    public void deleteRoomState(String roomId) {
        try {
            if (redisTemplate != null) {
                redisTemplate.delete(ROOM_KEY_PREFIX + roomId);
            }
            fallbackSnapshots.remove(ROOM_KEY_PREFIX + roomId);
        } catch (Exception e) {
            logger.warn("删除 Redis 房间状态失败: {}", roomId, e);
            fallbackSnapshots.remove(ROOM_KEY_PREFIX + roomId);
        }
    }

    private Map<String, Object> buildSnapshot(GameRoom room) {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("roomId", room.getRoomId());
        snapshot.put("roomName", room.getRoomName());
        snapshot.put("gameState", room.getGameState().name());
        snapshot.put("createdAt", room.getCreatedAt());
        snapshot.put("updatedAt", room.getUpdatedAt());
        snapshot.put("currentPlayer", room.getCurrentPlayer());
        snapshot.put("firstTurn", room.isFirstTurn());
        snapshot.put("passCount", room.getPassCount());
        snapshot.put("tableCards", room.getTableCards().stream().map(c -> c.getPower()).toList());
        snapshot.put("players", buildPlayersSnapshot(room.getPlayers()));
        return snapshot;
    }

    private List<Map<String, Object>> buildPlayersSnapshot(List<GamePlayer> players) {
        return players.stream().map(player -> {
            Map<String, Object> p = new HashMap<>();
            p.put("playerId", player.getPlayerId());
            p.put("name", player.getPlayerName());
            p.put("seatIndex", player.getSeatIndex());
            p.put("ready", player.isReady());
            p.put("score", player.getScore());
            p.put("hand", player.getHand().stream().map(c -> c.getPower()).toList());
            return p;
        }).toList();
    }
}