package com.bigtwo.game;

import com.bigtwo.model.*;
import com.bigtwo.persistence.GameRecordService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class GameEngine {
    private static final Logger logger = LoggerFactory.getLogger(GameEngine.class);

    @Autowired
    private RoomManager roomManager;

    @Autowired
    private GameRecordService gameRecordService;

    /**
     * 初始化游戏：发牌并选择先手
     * 必须确保房间中有4个玩家
     */
    public void startGame(String roomId) {
        GameRoom room = roomManager.getRoom(roomId);
        if (room == null) {
            logger.warn("房间不存在: {}", roomId);
            return;
        }

        if (room.getPlayers().size() != 4) {
            logger.warn("玩家数不足4人: {}", roomId);
            return;
        }

        // 新局开始前清空场面状态
        room.setTableCards(new ArrayList<>());
        room.setPassCount(0);
        room.setLastRoundSummary(Map.of());

        // 避免旧局残留手牌
        for (GamePlayer player : room.getPlayers()) {
            player.setHand(new ArrayList<>());
        }

        // 1. 发牌
        dealCards(room);
        logger.info("已发牌 for room {}", roomId);

        // 2. 选择先手（梅花3 = 0*13 + 0 = 0）
        Card clubThree = new Card(0);
        GamePlayer startingPlayer = findPlayerWithCard(room, clubThree);
        
        if (startingPlayer != null) {
            room.setCurrentPlayer(startingPlayer.getSeatIndex());
            room.setFirstTurn(true);
            logger.info("先手玩家: {} (座位 {})", startingPlayer.getPlayerName(), startingPlayer.getSeatIndex());
        } else {
            // 异常情况，选择座位0的玩家
            room.setCurrentPlayer(0);
            room.setFirstTurn(true);
            logger.warn("找不到梅花3，默认选择座位0的玩家");
        }

        room.setGameState(GameState.IN_PROGRESS);
        room.setLastAction("遊戲開始，等待首家出牌");
        roomManager.persistRoomState(roomId);
        logger.info("游戏已开始: {}", roomId);
    }

    /**
     * 发牌：洗牌后分配给4个玩家各13张
     */
    private void dealCards(GameRoom room) {
        // 创建并洗牌
        List<Card> deck = createDeck();
        Collections.shuffle(deck);

        // 分配给4个玩家
        List<GamePlayer> players = room.getPlayers();
        int cardIndex = 0;
        for (int i = 0; i < 4; i++) {
            GamePlayer player = players.get(i);
            for (int j = 0; j < 13; j++) {
                player.addCard(deck.get(cardIndex++));
            }
            // 排序玩家手牌
            sortPlayerHand(player);
        }
    }

    /**
     * 创建标准52张牌组
     */
    private List<Card> createDeck() {
        List<Card> deck = new ArrayList<>();
        for (int suit = 0; suit < 4; suit++) {
            for (int rank = 0; rank < 13; rank++) {
                deck.add(new Card(suit * 13 + rank));
            }
        }
        return deck;
    }

    /**
     * 排序玩家手牌（按大小）
     */
    private void sortPlayerHand(GamePlayer player) {
        List<Card> hand = new ArrayList<>(player.getHand());
        hand.sort(Comparator.naturalOrder());
        player.setHand(hand);
    }

    /**
     * 查找拥有特定卡牌的玩家
     */
    private GamePlayer findPlayerWithCard(GameRoom room, Card targetCard) {
        return room.getPlayers().stream()
                .filter(p -> p.getHand().contains(targetCard))
                .findFirst()
                .orElse(null);
    }

    /**
     * 玩家出牌
     * 
     * @return true if play is valid, false otherwise
     */
    public boolean playCards(String roomId, String playerId, List<Card> playCards) {
        GameRoom room = roomManager.getRoom(roomId);
        if (room == null) {
            logger.warn("房间不存在: {}", roomId);
            return false;
        }

        if (room.getGameState() != GameState.IN_PROGRESS) {
            logger.warn("游戏不在进行中: {}", roomId);
            return false;
        }

        // 兜底：若已有0手牌玩家，立即结算，防止状态卡住
        if (finalizeIfWinnerExists(room)) {
            return true;
        }

        // 1. 验证是否是当前玩家
        GamePlayer currentPlayer = room.getPlayers().get(room.getCurrentPlayer());
        GamePlayer player = room.findPlayer(playerId);
        if (player == null || !currentPlayer.getPlayerId().equals(playerId)) {
            logger.warn("不是当前玩家的回合: {} (当前: {})", playerId, currentPlayer.getPlayerId());
            return false;
        }

        // 2. 验证玩家是否拥有这些牌
        if (!player.getHand().containsAll(playCards)) {
            logger.warn("玩家手中没有这些牌");
            return false;
        }

        // 3. 使用 GameLogic 验证出牌是否合法
            if (!GameLogic.canPlay(room.getTableCards(), playCards)) {
            logger.warn("出牌不合法");
            return false;
        }

        // 3.1 首轮首手必须包含梅花3
        if (room.isFirstTurn() && room.getTableCards().isEmpty()) {
            boolean containsClubThree = playCards.stream().anyMatch(c -> c.getPower() == 0);
            if (!containsClubThree) {
                logger.warn("首轮首手必须包含梅花3");
                return false;
            }
        }

        // 4. 将牌从玩家手中移除
        for (Card card : playCards) {
            player.removeCard(card);
        }

        // 5. 更新桌面牌
        room.setTableCards(new ArrayList<>(playCards));
        room.setFirstTurn(false); // 不再是首轮

        // 6. 重置连续Pass计数
        room.setPassCount(0);
        room.setLastAction(player.getPlayerName() + " 出牌 " + playCards.size() + " 張");

        logger.info("玩家 {} 出牌: {}", player.getPlayerName(), playCards);

        // 7. 检查胜利
        if (player.getHand().isEmpty()) {
            setGameFinished(room, player);
            return true;
        }

        // 8. 轮转到下一个玩家
        nextTurn(room);
        roomManager.persistRoomState(roomId);
        return true;
    }

    /**
     * 玩家Pass
     */
    public boolean playerPass(String roomId, String playerId) {
        GameRoom room = roomManager.getRoom(roomId);
        if (room == null) {
            logger.warn("房间不存在: {}", roomId);
            return false;
        }

        if (room.getGameState() != GameState.IN_PROGRESS) {
            logger.warn("游戏不在进行中: {}", roomId);
            return false;
        }

        // 兜底：若已有0手牌玩家，立即结算，防止胜者还能Pass
        if (finalizeIfWinnerExists(room)) {
            return true;
        }

        // 1. 验证是否是当前玩家
        GamePlayer currentPlayer = room.getPlayers().get(room.getCurrentPlayer());
        GamePlayer player = room.findPlayer(playerId);
        if (player == null || !currentPlayer.getPlayerId().equals(playerId)) {
            logger.warn("不是当前玩家的回合: {} (当前: {})", playerId, currentPlayer.getPlayerId());
            return false;
        }

        if (player.getHand().isEmpty()) {
            setGameFinished(room, player);
            return true;
        }

        // 桌面为空时不允许 pass
        if (room.getTableCards().isEmpty()) {
            logger.warn("空桌不能 Pass");
            return false;
        }

        // 2. 增加Pass计数
        room.setPassCount(room.getPassCount() + 1);
        room.setLastAction(player.getPlayerName() + " Pass");

        logger.info("玩家 {} Pass (Pass计数: {})", player.getPlayerName(), room.getPassCount());

        // 3. 如果所有其他玩家都Pass（即Pass计数 >= 3），清空桌面
        if (room.getPassCount() >= 3) {
            resetTable(room);
            // 出牌者成为下一个先手
            // 不轮转，让出牌者继续出牌
            roomManager.persistRoomState(roomId);
            logger.info("所有其他玩家都Pass，桌面已清空");
            return true;
        }

        // 4. 轮转到下一个玩家
        nextTurn(room);
        roomManager.persistRoomState(roomId);
        return true;
    }

    /**
     * 轮转到下一个玩家
     */
    private void nextTurn(GameRoom room) {
        int currentIndex = room.getCurrentPlayer();
        int nextIndex = (currentIndex + 1) % 4;
        room.setCurrentPlayer(nextIndex);
        logger.debug("回合轮转: {} -> {}", currentIndex, nextIndex);
    }

    /**
     * 清空桌面（当所有其他玩家都Pass时）
     * 下一个出牌者是当前玩家的下一个
     */
    private void resetTable(GameRoom room) {
        room.setTableCards(new ArrayList<>());
        room.setPassCount(0);
        room.setLastAction("本輪清檯，進入新一輪");
        // 轮转到下一个玩家（他将成为新的出牌者）
        nextTurn(room);
    }

    /**
     * 游戏结束处理
     */
    private void setGameFinished(GameRoom room, GamePlayer winner) {
        if (room.getGameState() == GameState.FINISHED) {
            return;
        }
        room.setGameState(GameState.FINISHED);
        applyRoundScoring(room, winner);
        room.setLastAction("遊戲結束，贏家: " + winner.getPlayerName());
        gameRecordService.recordGameResult(room, winner);
        roomManager.persistRoomState(room.getRoomId());
        logger.info("游戏结束: {} 赢了!", winner.getPlayerName());
        
        // TODO: 可以在这里添加积分计算、数据库记录等逻辑
        // 例如：calculateScores(room, winner);
    }

    /**
     * 检查游戏是否结束
     */
    public boolean isGameFinished(GameRoom room) {
        return room.getGameState() == GameState.FINISHED;
    }

    /**
     * 重新开始游戏（新一局）
     */
    public void resetGameForNewRound(String roomId) {
        GameRoom room = roomManager.getRoom(roomId);
        if (room == null) {
            logger.warn("房间不存在: {}", roomId);
            return;
        }

        // 清空所有玩家的手牌
        for (GamePlayer player : room.getPlayers()) {
            player.setHand(new ArrayList<>());
        }

        // 清空游戏状态
        room.setTableCards(new ArrayList<>());
        room.setPassCount(0);
        room.setFirstTurn(true);
        room.setLastAction("等待新一局開始");

        // 重置所有玩家的准备状态
        roomManager.resetReadyStates(roomId);

        // 恢复到准备状态
        room.setGameState(GameState.READY);
        roomManager.persistRoomState(roomId);

        logger.info("游戏已重置，准备新一局: {}", roomId);
    }

    /**
     * 获取游戏信息（用于发送给客户端）
     */
    public Map<String, Object> getGameState(GameRoom room) {
        Map<String, Object> state = new HashMap<>();
        
        state.put("roomId", room.getRoomId());
        state.put("gameState", room.getGameState());
        state.put("currentPlayer", room.getCurrentPlayer());
        String currentPlayerName = "-";
        if (room.getCurrentPlayer() >= 0 && room.getCurrentPlayer() < room.getPlayers().size()) {
            currentPlayerName = room.getPlayers().get(room.getCurrentPlayer()).getPlayerName();
        }
        state.put("currentPlayerName", currentPlayerName);
        state.put("firstTurn", room.isFirstTurn());
        state.put("passCount", room.getPassCount());
        state.put("lastAction", room.getLastAction());
        
        // 桌面牌信息
        state.put("tableCards", room.getTableCards().stream()
                .map(c -> Map.of(
                        "suit", c.getSuit(),
                        "rank", c.getRank(),
                        "power", c.getPower()
                ))
                .collect(Collectors.toList()));
        
        // 玩家信息（不包含其他玩家的手牌）
        List<Map<String, Object>> playersInfo = new ArrayList<>();
        for (GamePlayer player : room.getPlayers()) {
            Map<String, Object> playerInfo = new HashMap<>();
            playerInfo.put("playerId", player.getPlayerId());
            playerInfo.put("name", player.getPlayerName());
            playerInfo.put("seatIndex", player.getSeatIndex());
            playerInfo.put("handSize", player.getHand().size());
            playerInfo.put("score", player.getScore());
            playerInfo.put("wins", player.getWinCount());
            playerInfo.put("bot", player.isBot());
            playersInfo.add(playerInfo);
        }
        state.put("players", playersInfo);
        
        return state;
    }

    /**
     * 获取玩家的手牌（用于发送给该玩家）
     */
    public List<Card> getPlayerHand(GameRoom room, String playerId) {
        GamePlayer player = room.findPlayer(playerId);
        if (player == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(player.getHand());
    }

    /**
     * 获取建议出牌
     */
    public List<Card> suggestCards(String roomId, String playerId) {
        GameRoom room = roomManager.getRoom(roomId);
        if (room == null) {
            return List.of();
        }

        GamePlayer player = room.findPlayer(playerId);
        if (player == null) {
            return List.of();
        }

        return GameLogic.suggestPlay(player.getHand(), room.getTableCards(), room.isFirstTurn());
    }

    public Map<String, List<Card>> suggestCardsOptions(String roomId, String playerId) {
        GameRoom room = roomManager.getRoom(roomId);
        if (room == null) {
            return Map.of(
                    "conservative", List.of(),
                    "balanced", List.of(),
                    "aggressive", List.of()
            );
        }

        GamePlayer player = room.findPlayer(playerId);
        if (player == null) {
            return Map.of(
                    "conservative", List.of(),
                    "balanced", List.of(),
                    "aggressive", List.of()
            );
        }

        return GameLogic.suggestPlayOptions(player.getHand(), room.getTableCards(), room.isFirstTurn());
    }

    public Map<String, Object> getLastRoundSummary(String roomId) {
        GameRoom room = roomManager.getRoom(roomId);
        return room == null ? Map.of() : room.getLastRoundSummary();
    }

    public GamePlayer getWinner(GameRoom room) {
        return room.getPlayers().stream()
                .filter(p -> p.getHand().isEmpty())
                .findFirst()
                .orElse(null);
    }

    private boolean finalizeIfWinnerExists(GameRoom room) {
        GamePlayer winner = getWinner(room);
        if (winner != null) {
            setGameFinished(room, winner);
            return true;
        }
        return false;
    }

    private void applyRoundScoring(GameRoom room, GamePlayer winner) {
        long winnerGain = 0;
        Map<String, Integer> roundDelta = new HashMap<>();
        List<Map<String, Object>> playersSummary = new ArrayList<>();

        for (GamePlayer player : room.getPlayers()) {
            if (player.getPlayerId().equals(winner.getPlayerId())) {
                continue;
            }
            int penalty = calculatePenalty(player.getHandSize());
            player.addScore(-penalty);
            winnerGain += penalty;
            roundDelta.put(player.getPlayerId(), -penalty);
        }

        winner.addScore(winnerGain);
        winner.addWinCount(1);
        roundDelta.put(winner.getPlayerId(), (int) winnerGain);

        for (GamePlayer player : room.getPlayers()) {
            Map<String, Object> p = new HashMap<>();
            p.put("playerId", player.getPlayerId());
            p.put("name", player.getPlayerName());
            p.put("bot", player.isBot());
            p.put("remainingCards", player.getHandSize());
            p.put("roundDelta", roundDelta.getOrDefault(player.getPlayerId(), 0));
            p.put("totalScore", player.getScore());
            p.put("wins", player.getWinCount());
            playersSummary.add(p);
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("winnerId", winner.getPlayerId());
        summary.put("winnerName", winner.getPlayerName());
        summary.put("players", playersSummary);
        summary.put("timestamp", System.currentTimeMillis());
        room.setLastRoundSummary(summary);
    }

    private int calculatePenalty(int remainingCards) {
        if (remainingCards >= 13) {
            return remainingCards * 3;
        }
        if (remainingCards >= 10) {
            return remainingCards * 2;
        }
        return remainingCards;
    }
}
