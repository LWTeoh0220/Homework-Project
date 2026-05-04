package com.bigtwo.server.handler;

import com.bigtwo.game.GameEngine;
import com.bigtwo.game.RoomManager;
import com.bigtwo.model.Card;
import com.bigtwo.model.GameRoom;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class GameWebSocketHandler extends AbstractWebSocketHandler {
    private static final Logger logger = LoggerFactory.getLogger(GameWebSocketHandler.class);
    private static final Gson gson = new Gson();
    private static final long AI_DELAY_MIN_MS = 700;
    private static final long AI_DELAY_MAX_MS = 1600;

    @Autowired
    private RoomManager roomManager;

    @Autowired
    private GameEngine gameEngine;

    // WebSocket 會話容器：playerId -> WebSocketSession
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    // 玩家身份映射：sessionId -> playerId
    private final Map<String, String> sessionPlayerMap = new ConcurrentHashMap<>();

    // 每個房間同一時間只允許一個 AI 排程任務
    private final Set<String> aiScheduledRooms = ConcurrentHashMap.newKeySet();
        private final ScheduledExecutorService aiExecutor = Executors.newScheduledThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors() / 2)
        );

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        logger.info("WebSocket connected: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        logger.debug("Message received from {}: {}", session.getId(), message.getPayload());
        try {
            JsonObject json = gson.fromJson(message.getPayload(), JsonObject.class);
            String type = json.get("type").getAsString();

            switch (type) {
                case "CREATE_ROOM" -> handleCreateRoom(session, json);
                case "JOIN_ROOM" -> handleJoinRoom(session, json);
                case "READY" -> handleReady(session, json);
                case "LEAVE_ROOM" -> handleLeaveRoom(session, json);
                case "GET_ROOMS" -> handleGetRooms(session);
                case "PLAY_CARDS" -> handlePlayCards(session, json);
                case "PASS" -> handlePass(session);
                case "SUGGEST" -> handleSuggest(session);
                default -> {
                    logger.warn("Unknown message type: {}", type);
                    sendError(session, "未知訊息類型: " + type);
                }
            }
        } catch (Exception e) {
            logger.error("Error handling message", e);
            sendError(session, e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        logger.info("WebSocket disconnected: {}", session.getId());

        String playerId = sessionPlayerMap.remove(session.getId());
        if (playerId != null) {
            sessions.remove(playerId);
            String roomId = roomManager.getPlayerRoom(playerId);
            if (roomId != null) {
                roomManager.leaveRoom(roomId, playerId);
                broadcastRoomUpdate(roomId);
                processAiTurns(roomId);
            }
        }
    }

    private void handleCreateRoom(WebSocketSession session, JsonObject json) throws IOException {
        String roomName = json.has("roomName") ? json.get("roomName").getAsString() : "房間";
        String playerName = json.get("playerName").getAsString();
        String playerId = UUID.randomUUID().toString();

        GameRoom room = roomManager.createRoom(roomName, playerId, playerName);
        sessions.put(playerId, session);
        sessionPlayerMap.put(session.getId(), playerId);

        JsonObject response = new JsonObject();
        response.addProperty("type", "ROOM_CREATED");
        response.addProperty("roomId", room.getRoomId());
        response.addProperty("roomName", room.getRoomName());
        response.addProperty("playerId", playerId);
        response.add("players", gson.toJsonTree(formatPlayers(room)));

        session.sendMessage(new TextMessage(gson.toJson(response)));
        logger.info("房間已建立: {} by {}", room.getRoomId(), playerId);
    }

    private void handleJoinRoom(WebSocketSession session, JsonObject json) throws IOException {
        String roomId = json.get("roomId").getAsString();
        String playerName = json.get("playerName").getAsString();
        String playerId = UUID.randomUUID().toString();

        if (!roomManager.roomExists(roomId)) {
            sendError(session, "房間不存在");
            return;
        }

        if (!roomManager.joinRoom(roomId, playerId, playerName)) {
            sendError(session, "無法加入房間（可能已滿或遊戲已開始）");
            return;
        }

        sessions.put(playerId, session);
        sessionPlayerMap.put(session.getId(), playerId);

        GameRoom room = roomManager.getRoom(roomId);
        JsonObject response = new JsonObject();
        response.addProperty("type", "ROOM_JOINED");
        response.addProperty("roomId", room.getRoomId());
        response.addProperty("roomName", room.getRoomName());
        response.addProperty("playerId", playerId);
        response.add("players", gson.toJsonTree(formatPlayers(room)));

        session.sendMessage(new TextMessage(gson.toJson(response)));
        broadcastRoomUpdate(roomId, playerId);
        logger.info("玩家已加入: {} -> {}", playerId, roomId);
    }

    private void handleReady(WebSocketSession session, JsonObject json) throws IOException {
        String playerId = sessionPlayerMap.get(session.getId());
        if (playerId == null) {
            sendError(session, "未找到玩家身份");
            return;
        }

        String roomId = roomManager.getPlayerRoom(playerId);
        if (roomId == null) {
            sendError(session, "玩家不在任何房間中");
            return;
        }

        roomManager.playerReady(roomId, playerId);
        roomManager.fillWithAiPlayers(roomId);
        broadcastRoomUpdate(roomId);

        if (roomManager.areAllPlayersReady(roomId)) {
            handleGameStart(roomId);
        }

        logger.debug("玩家已準備: {} in {}", playerId, roomId);
    }

    private void handleLeaveRoom(WebSocketSession session, JsonObject json) throws IOException {
        String playerId = sessionPlayerMap.get(session.getId());
        if (playerId == null) {
            return;
        }

        String roomId = roomManager.getPlayerRoom(playerId);
        if (roomId != null) {
            roomManager.leaveRoom(roomId, playerId);
            broadcastRoomUpdate(roomId);
            processAiTurns(roomId);
        }

        sessions.remove(playerId);
        logger.info("玩家已離開: {} from {}", playerId, roomId);
    }

    private void handleGetRooms(WebSocketSession session) throws IOException {
        List<GameRoom> availableRooms = roomManager.getAvailableRooms();

        JsonObject response = new JsonObject();
        response.addProperty("type", "ROOMS_LIST");
        response.add("rooms", gson.toJsonTree(
                availableRooms.stream()
                        .map(r -> {
                            JsonObject room = new JsonObject();
                            room.addProperty("roomId", r.getRoomId());
                            room.addProperty("roomName", r.getRoomName());
                            room.addProperty("playerCount", r.getPlayers().size());
                            room.addProperty("maxPlayers", 4);
                            return room;
                        })
                        .toList()
        ));

        session.sendMessage(new TextMessage(gson.toJson(response)));
    }

    private void handleGameStart(String roomId) throws IOException {
        GameRoom room = roomManager.getRoom(roomId);
        if (room == null) {
            return;
        }

        gameEngine.startGame(roomId);
        room = roomManager.getRoom(roomId);

        for (String playerId : sessions.keySet()) {
            String playerRoom = roomManager.getPlayerRoom(playerId);
            if (roomId.equals(playerRoom)) {
                WebSocketSession session = sessions.get(playerId);
                if (session != null && session.isOpen()) {
                    JsonObject msg = new JsonObject();
                    msg.addProperty("type", "GAME_STARTED");
                    msg.addProperty("roomId", roomId);
                    msg.add("gameState", gson.toJsonTree(gameEngine.getGameState(room)));

                    List<Card> hand = gameEngine.getPlayerHand(room, playerId);
                    msg.add("hand", gson.toJsonTree(toCardPayload(hand)));
                    session.sendMessage(new TextMessage(gson.toJson(msg)));
                }
            }
        }

        logger.info("遊戲已開始: {}", roomId);
        processAiTurns(roomId);
    }

    private void handlePlayCards(WebSocketSession session, JsonObject json) throws IOException {
        String playerId = sessionPlayerMap.get(session.getId());
        if (playerId == null) {
            sendError(session, "未找到玩家身份");
            return;
        }

        String roomId = roomManager.getPlayerRoom(playerId);
        if (roomId == null) {
            sendError(session, "玩家不在任何房間中");
            return;
        }

        List<Card> cards = new ArrayList<>();
        for (var element : json.getAsJsonArray("cards")) {
            JsonObject cardObj = element.getAsJsonObject();
            if (!cardObj.has("power")) {
                continue;
            }
            cards.add(new Card(cardObj.get("power").getAsInt()));
        }

        if (cards.isEmpty()) {
            sendError(session, "出牌資料無效");
            return;
        }

        if (gameEngine.playCards(roomId, playerId, cards)) {
            GameRoom room = roomManager.getRoom(roomId);
            broadcastGameStateWithHands(room);
            logger.info("玩家 {} 出牌成功", playerId);

            if (room != null && gameEngine.isGameFinished(room)) {
                sendFinished(roomId, room, playerId);
            } else {
                processAiTurns(roomId);
            }
        } else {
            sendError(session, "出牌失敗，請檢查牌型");
        }
    }

    private void handlePass(WebSocketSession session) throws IOException {
        String playerId = sessionPlayerMap.get(session.getId());
        if (playerId == null) {
            sendError(session, "未找到玩家身份");
            return;
        }

        String roomId = roomManager.getPlayerRoom(playerId);
        if (roomId == null) {
            sendError(session, "玩家不在任何房間中");
            return;
        }

        if (gameEngine.playerPass(roomId, playerId)) {
            GameRoom room = roomManager.getRoom(roomId);
            broadcastGameStateWithHands(room);
            logger.info("玩家 {} Pass", playerId);

            if (room != null && gameEngine.isGameFinished(room)) {
                sendFinished(roomId, room, playerId);
            } else {
                processAiTurns(roomId);
            }
        } else {
            sendError(session, "Pass 失敗");
        }
    }

    private void handleSuggest(WebSocketSession session) throws IOException {
        String playerId = sessionPlayerMap.get(session.getId());
        if (playerId == null) {
            sendError(session, "未找到玩家身份");
            return;
        }

        String roomId = roomManager.getPlayerRoom(playerId);
        if (roomId == null) {
            sendError(session, "玩家不在任何房間中");
            return;
        }

        Map<String, List<Card>> options = gameEngine.suggestCardsOptions(roomId, playerId);
        List<Card> suggestion = options.getOrDefault("conservative", List.of());

        JsonObject response = new JsonObject();
        response.addProperty("type", "SUGGESTION");
        response.add("cards", gson.toJsonTree(toCardPayload(suggestion)));

        JsonObject optionsJson = new JsonObject();
        options.forEach((mode, cards) -> optionsJson.add(mode, gson.toJsonTree(toCardPayload(cards))));
        response.add("options", optionsJson);

        session.sendMessage(new TextMessage(gson.toJson(response)));
    }

    private void broadcastRoomUpdate(String roomId) throws IOException {
        broadcastRoomUpdate(roomId, null);
    }

    private void broadcastRoomUpdate(String roomId, String excludePlayerId) throws IOException {
        GameRoom room = roomManager.getRoom(roomId);
        if (room == null) {
            return;
        }

        JsonObject msg = new JsonObject();
        msg.addProperty("type", "ROOM_UPDATE");
        msg.addProperty("roomId", roomId);
        msg.addProperty("roomName", room.getRoomName());
        msg.add("players", gson.toJsonTree(formatPlayers(room)));

        String message = gson.toJson(msg);
        for (String playerId : sessions.keySet()) {
            if (excludePlayerId != null && excludePlayerId.equals(playerId)) {
                continue;
            }
            String playerRoom = roomManager.getPlayerRoom(playerId);
            if (roomId.equals(playerRoom)) {
                WebSocketSession session = sessions.get(playerId);
                if (session != null && session.isOpen()) {
                    session.sendMessage(new TextMessage(message));
                }
            }
        }
    }

    private void broadcastToRoom(String roomId, String message) throws IOException {
        GameRoom room = roomManager.getRoom(roomId);
        if (room == null) {
            return;
        }

        for (String playerId : sessions.keySet()) {
            String playerRoom = roomManager.getPlayerRoom(playerId);
            if (roomId.equals(playerRoom)) {
                WebSocketSession session = sessions.get(playerId);
                if (session != null && session.isOpen()) {
                    session.sendMessage(new TextMessage(message));
                }
            }
        }
    }

    private void sendError(WebSocketSession session, String message) throws IOException {
        JsonObject error = new JsonObject();
        error.addProperty("type", "ERROR");
        error.addProperty("message", message);
        session.sendMessage(new TextMessage(gson.toJson(error)));
    }

    private List<Object> formatPlayers(GameRoom room) {
        return room.getPlayers().stream()
                .map(p -> {
                    JsonObject player = new JsonObject();
                    player.addProperty("playerId", p.getPlayerId());
                    player.addProperty("name", p.getPlayerName());
                    player.addProperty("seatIndex", p.getSeatIndex());
                    player.addProperty("ready", p.isReady());
                    player.addProperty("bot", p.isBot());
                    player.addProperty("wins", p.getWinCount());
                    player.addProperty("score", p.getScore());
                    return player;
                })
                .map(JsonObject::toString)
                .map(s -> gson.fromJson(s, Object.class))
                .toList();
    }

    private void broadcastGameStateWithHands(GameRoom room) throws IOException {
        if (room == null) {
            return;
        }

        for (String playerId : sessions.keySet()) {
            String playerRoom = roomManager.getPlayerRoom(playerId);
            if (room.getRoomId().equals(playerRoom)) {
                WebSocketSession session = sessions.get(playerId);
                if (session != null && session.isOpen()) {
                    JsonObject msg = new JsonObject();
                    msg.addProperty("type", "GAME_STATE_UPDATE");
                    msg.addProperty("roomId", room.getRoomId());
                    msg.add("gameState", gson.toJsonTree(gameEngine.getGameState(room)));
                    msg.add("hand", gson.toJsonTree(toCardPayload(gameEngine.getPlayerHand(room, playerId))));
                    session.sendMessage(new TextMessage(gson.toJson(msg)));
                }
            }
        }
    }

    private void processAiTurns(String roomId) {
        scheduleAiStep(roomId, randomAIDelay());
    }

    private void scheduleAiStep(String roomId, long delayMs) {
        if (!aiScheduledRooms.add(roomId)) {
            return;
        }

        aiExecutor.schedule(() -> {
            try {
                aiScheduledRooms.remove(roomId);
                runSingleAiStep(roomId);
            } catch (Exception e) {
                logger.error("AI 回合處理失敗: {}", roomId, e);
            } finally {
                aiScheduledRooms.remove(roomId);
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    private void runSingleAiStep(String roomId) throws IOException {
        GameRoom room = roomManager.getRoom(roomId);
        if (room == null || room.getGameState() != com.bigtwo.model.GameState.IN_PROGRESS) {
            return;
        }

        int currentIndex = room.getCurrentPlayer();
        if (currentIndex < 0 || currentIndex >= room.getPlayers().size()) {
            return;
        }

        var currentPlayer = room.getPlayers().get(currentIndex);
        if (!currentPlayer.isBot()) {
            return;
        }

        List<Card> suggestion = gameEngine.suggestCards(roomId, currentPlayer.getPlayerId());
        boolean ok;
        if (suggestion.isEmpty()) {
            ok = gameEngine.playerPass(roomId, currentPlayer.getPlayerId());
            logger.info("AI {} Pass", currentPlayer.getPlayerName());
        } else {
            ok = gameEngine.playCards(roomId, currentPlayer.getPlayerId(), suggestion);
            logger.info("AI {} 出牌: {}", currentPlayer.getPlayerName(), suggestion);
        }

        if (!ok) {
            logger.warn("AI 回合執行失敗，終止 AI 循環: {}", currentPlayer.getPlayerName());
            return;
        }

        GameRoom latestRoom = roomManager.getRoom(roomId);
        broadcastGameStateWithHands(latestRoom);

        if (latestRoom != null && gameEngine.isGameFinished(latestRoom)) {
            sendFinished(roomId, latestRoom, currentPlayer.getPlayerId());
            return;
        }

        scheduleAiStep(roomId, randomAIDelay());
    }

    private void sendFinished(String roomId, GameRoom room, String fallbackPlayerId) throws IOException {
        var winner = gameEngine.getWinner(room);
        String winnerName = winner != null ? winner.getPlayerName() : room.findPlayer(fallbackPlayerId).getPlayerName();

        JsonObject finishedMsg = new JsonObject();
        finishedMsg.addProperty("type", "GAME_FINISHED");
        finishedMsg.addProperty("roomId", roomId);
        finishedMsg.addProperty("winner", winnerName);
        finishedMsg.add("summary", gson.toJsonTree(gameEngine.getLastRoundSummary(roomId)));
        broadcastToRoom(roomId, gson.toJson(finishedMsg));
    }

    private long randomAIDelay() {
        return AI_DELAY_MIN_MS + (long) (Math.random() * (AI_DELAY_MAX_MS - AI_DELAY_MIN_MS + 1));
    }

    private List<Map<String, Integer>> toCardPayload(List<Card> cards) {
        return cards.stream()
                .map(c -> Map.of(
                        "suit", c.getSuit(),
                        "rank", c.getRank(),
                        "power", c.getPower()
                ))
                .toList();
    }
}
