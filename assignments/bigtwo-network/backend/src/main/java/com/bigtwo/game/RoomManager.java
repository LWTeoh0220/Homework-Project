package com.bigtwo.game;

import com.bigtwo.model.*;
import com.bigtwo.persistence.RoomStateRedisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 房间管理服务
 * 负责房间的创建、删除、玩家加入/离开、准备状态等
 */
@Service
public class RoomManager {
    private static final Logger logger = LoggerFactory.getLogger(RoomManager.class);
    private static final String AI_ID_PREFIX = "AI_";
    private static final String AI_NAME_PREFIX = "AI-";

    @Autowired
    private RoomStateRedisService roomStateRedisService;
    
    // 房间容器：roomId -> GameRoom
    private final Map<String, GameRoom> rooms = new ConcurrentHashMap<>();
    
    // 玩家所在房间映射：playerId -> roomId
    private final Map<String, String> playerRooms = new ConcurrentHashMap<>();

    /**
     * 创建新房间
     */
    public GameRoom createRoom(String roomName, String creatorId, String creatorName) {
        String roomId = generateRoomId();
        GameRoom room = new GameRoom(roomId, roomName);
        
        // 创建者作为玩家1
        GamePlayer creator = new GamePlayer(creatorId, creatorName, 0);
        room.addPlayer(creator);
        
        rooms.put(roomId, room);
        playerRooms.put(creatorId, roomId);
        persistRoomState(roomId);
        
        logger.info("房间创建: {} ({})", roomId, roomName);
        return room;
    }

    /**
     * 获取房间
     */
    public GameRoom getRoom(String roomId) {
        return rooms.get(roomId);
    }

    /**
     * 获取所有可用房间（未满、未开始）
     */
    public List<GameRoom> getAvailableRooms() {
        return rooms.values().stream()
            .filter(r -> !r.isFull() && r.getGameState() == GameState.WAITING)
            .toList();
    }

    /**
     * 获取所有房间
     */
    public List<GameRoom> getAllRooms() {
        return new ArrayList<>(rooms.values());
    }

    /**
     * 玩家加入房间
     */
    public boolean joinRoom(String roomId, String playerId, String playerName) {
        GameRoom room = getRoom(roomId);
        if (room == null) {
            logger.warn("房间不存在: {}", roomId);
            return false;
        }

        if (room.isFull()) {
            logger.warn("房间已满: {}", roomId);
            return false;
        }

        if (room.getGameState() != GameState.WAITING) {
            logger.warn("房间游戏已开始: {}", roomId);
            return false;
        }

        // 检查玩家是否已在其他房间
        if (playerRooms.containsKey(playerId)) {
            String oldRoomId = playerRooms.get(playerId);
            leaveRoom(oldRoomId, playerId);
        }

        // 添加玩家，座位号为当前人数
        int seatIndex = room.getPlayers().size();
        GamePlayer newPlayer = new GamePlayer(playerId, playerName, seatIndex);
        room.addPlayer(newPlayer);
        playerRooms.put(playerId, roomId);
        persistRoomState(roomId);

        logger.info("玩家加入房间: {} -> {} ({})", playerId, roomId, playerName);
        return true;
    }

    /**
     * 玩家离开房间
     */
    public boolean leaveRoom(String roomId, String playerId) {
        GameRoom room = getRoom(roomId);
        if (room == null) {
            return false;
        }

        GameState originalState = room.getGameState();

        GamePlayer player = room.findPlayer(playerId);
        if (player == null) {
            return false;
        }

        // 对局中真人离开时，改为 AI 接管该席位和手牌
        if (originalState == GameState.IN_PROGRESS && !player.isBot()) {
            playerRooms.remove(playerId);
            String aiId = generateAiId();
            player.setPlayerId(aiId);
            player.setPlayerName(AI_NAME_PREFIX + (player.getSeatIndex() + 1));
            player.setBot(true);
            player.setReady(true);
            playerRooms.put(aiId, roomId);
            persistRoomState(roomId);
            logger.info("玩家离开后已由 AI 接管: {} -> {}", playerId, aiId);
            return true;
        }

        room.removePlayer(playerId);
        playerRooms.remove(playerId);

        logger.info("玩家离开房间: {} <- {} ({})", roomId, playerId, player.getPlayerName());

        // 房间为空则删除房间
        if (room.getPlayers().isEmpty()) {
            deleteRoom(roomId);
            logger.info("房间已删除（无玩家）: {}", roomId);
            return true;
        }

        persistRoomState(roomId);

        return true;
    }

    /**
     * 删除房间
     */
    public void deleteRoom(String roomId) {
        GameRoom room = rooms.remove(roomId);
        if (room != null) {
            // 清理所有玩家映射
            room.getPlayers().forEach(p -> playerRooms.remove(p.getPlayerId()));
            roomStateRedisService.deleteRoomState(roomId);
            logger.info("房间已删除: {}", roomId);
        }
    }

    /**
     * 玩家标记为准备
     */
    public boolean playerReady(String roomId, String playerId) {
        GameRoom room = getRoom(roomId);
        if (room == null) return false;

        GamePlayer player = room.findPlayer(playerId);
        if (player == null) return false;

        player.setReady(true);
        persistRoomState(roomId);
        logger.debug("玩家已准备: {} in {}", playerId, roomId);
        return true;
    }

    /**
     * 玩家标记为未准备
     */
    public boolean playerNotReady(String roomId, String playerId) {
        GameRoom room = getRoom(roomId);
        if (room == null) return false;

        GamePlayer player = room.findPlayer(playerId);
        if (player == null) return false;

        player.setReady(false);
        persistRoomState(roomId);
        logger.debug("玩家未准备: {} in {}", playerId, roomId);
        return true;
    }

    /**
     * 检查所有玩家是否准备就绪
     */
    public boolean areAllPlayersReady(String roomId) {
        GameRoom room = getRoom(roomId);
        if (room == null) return false;

        return room.getPlayers().size() == 4 && 
               room.getPlayers().stream().allMatch(GamePlayer::isReady);
    }

    /**
     * 重置房间准备状态
     */
    public void resetReadyStates(String roomId) {
        GameRoom room = getRoom(roomId);
        if (room != null) {
            room.getPlayers().forEach(p -> p.setReady(false));
            persistRoomState(roomId);
            logger.debug("房间准备状态已重置: {}", roomId);
        }
    }

    /**
     * 持久化房间状态到 Redis
     */
    public void persistRoomState(String roomId) {
        GameRoom room = getRoom(roomId);
        if (room != null) {
            roomStateRedisService.saveRoomState(room);
        }
    }

    /**
     * 获取玩家所在的房间
     */
    public String getPlayerRoom(String playerId) {
        return playerRooms.get(playerId);
    }

    /**
     * 生成房间ID
     */
    private String generateRoomId() {
        return "room_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    /**
     * 检查房间是否存在
     */
    public boolean roomExists(String roomId) {
        return rooms.containsKey(roomId);
    }

    /**
     * 获取房间人数
     */
    public int getRoomPlayerCount(String roomId) {
        GameRoom room = getRoom(roomId);
        return room == null ? 0 : room.getPlayers().size();
    }

    /**
     * 人数不足时自动补齐 AI（用于开局前）
     */
    public void fillWithAiPlayers(String roomId) {
        GameRoom room = getRoom(roomId);
        if (room == null || room.getGameState() != GameState.WAITING) {
            return;
        }

        while (!room.isFull()) {
            int seatIndex = room.getPlayers().size();
            String aiId = generateAiId();
            GamePlayer ai = new GamePlayer(aiId, AI_NAME_PREFIX + (seatIndex + 1), seatIndex, true);
            ai.setReady(true);
            room.addPlayer(ai);
            playerRooms.put(aiId, roomId);
        }

        persistRoomState(roomId);
    }

    private String generateAiId() {
        return AI_ID_PREFIX + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
