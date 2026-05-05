/**
 * 遊戲 UI 控制器
 */
class GameUI {
    constructor() {
        this.currentPlayerId = null;
        this.currentRoomId = null;
        this.selectedCards = new Set();
        this.gameState = null;
        this.playerHand = [];
        this.suggestionOptionsData = {};

        this.initializeElements();
        this.bindEvents();
        this.setupWebSocketHandlers();
    }

    initializeElements() {
        this.lobbyPanel = document.getElementById('lobbyPanel');
        this.playerNameInput = document.getElementById('playerNameInput');
        this.createRoomBtn = document.getElementById('createRoomBtn');
        this.refreshRoomsBtn = document.getElementById('refreshRoomsBtn');
        this.confirmCreateRoomBtn = document.getElementById('confirmCreateRoomBtn');
        this.loadRoomsBtn = document.getElementById('loadRoomsBtn');
        this.roomsList = document.getElementById('roomsList');
        this.lobbyError = document.getElementById('lobbyError');
        this.createRoomPanel = document.getElementById('createRoomPanel');
        this.roomsPanel = document.getElementById('roomsPanel');

        this.waitingPanel = document.getElementById('waitingPanel');
        this.roomNameDisplay = document.getElementById('roomNameDisplay');
        this.readyBtn = document.getElementById('readyBtn');
        this.leaveWaitingBtn = document.getElementById('leaveWaitingBtn');
        this.waitingError = document.getElementById('waitingError');

        this.gamePanel = document.getElementById('gamePanel');
        this.handCards = document.getElementById('handCards');
        this.handCount = document.getElementById('handCount');
        this.tableCards = document.getElementById('tableCards');
        this.turnIndicator = document.getElementById('turnIndicator');
        this.playBtn = document.getElementById('playBtn');
        this.passBtn = document.getElementById('passBtn');
        this.suggestBtn = document.getElementById('suggestBtn');
        this.leaveGameBtn = document.getElementById('leaveGameBtn');
        this.suggestionOptions = document.getElementById('suggestionOptions');
        this.lastAction = document.getElementById('lastAction');
        this.gameError = document.getElementById('gameError');

        this.settlementOverlay = document.getElementById('settlementOverlay');
        this.settlementWinner = document.getElementById('settlementWinner');
        this.settlementRows = document.getElementById('settlementRows');
        this.settlementCloseBtn = document.getElementById('settlementCloseBtn');
        this.settlementReadyBtn = document.getElementById('settlementReadyBtn');

        this.connectionStatus = document.getElementById('connectionStatus');
        this.statusDot = this.connectionStatus ? this.connectionStatus.querySelector('.status-dot') : null;
        this.statusText = this.connectionStatus ? this.connectionStatus.querySelector('.status-text') : null;
        this.heroConnectionState = document.getElementById('heroConnectionState');
        this.playerCountPill = document.getElementById('playerCountPill');
        this.turnHintPill = document.getElementById('turnHintPill');
    }

    bindEvents() {
        if (this.createRoomBtn) {
            this.createRoomBtn.addEventListener('click', () => this.openLobbyDrawer('createRoomPanel'));
        }
        if (this.refreshRoomsBtn) {
            this.refreshRoomsBtn.addEventListener('click', () => this.openLobbyDrawer('roomsPanel'));
        }

        if (this.confirmCreateRoomBtn) {
            this.confirmCreateRoomBtn.addEventListener('click', () => this.handleCreateRoom());
        }
        if (this.loadRoomsBtn) {
            this.loadRoomsBtn.addEventListener('click', () => this.handleGetRooms(true));
        }

        document.querySelectorAll('[data-close-drawer]').forEach(button => {
            button.addEventListener('click', () => {
                const drawerId = button.getAttribute('data-close-drawer');
                this.closeLobbyDrawer(drawerId);
            });
        });

        if (this.playerNameInput) {
            this.playerNameInput.addEventListener('keydown', (event) => {
                if (event.key === 'Enter') {
                    this.handleCreateRoom();
                }
            });
        }

        if (this.readyBtn) {
            this.readyBtn.addEventListener('click', () => this.handleReady());
        }
        if (this.leaveWaitingBtn) {
            this.leaveWaitingBtn.addEventListener('click', () => this.handleLeaveRoom());
        }

        if (this.suggestBtn) {
            this.suggestBtn.addEventListener('click', () => this.handleSuggest());
        }
        if (this.playBtn) {
            this.playBtn.addEventListener('click', () => this.handlePlayCards());
        }
        if (this.passBtn) {
            this.passBtn.addEventListener('click', () => this.handlePass());
        }
        if (this.leaveGameBtn) {
            this.leaveGameBtn.addEventListener('click', () => this.handleLeaveRoom());
        }

        if (this.settlementCloseBtn) {
            this.settlementCloseBtn.addEventListener('click', () => {
                this.hideSettlement();
                this.switchPanel('waiting');
            });
        }

        if (this.settlementReadyBtn) {
            this.settlementReadyBtn.addEventListener('click', () => {
                this.hideSettlement();
                this.switchPanel('waiting');
                this.handleReady();
            });
        }
    }

    setupWebSocketHandlers() {
        gameWs.on('connected', () => {
            this.updateConnectionStatus('connected', '已連接');
            this.showMessage('已連接至伺服器');
        });
        gameWs.on('disconnected', () => {
            this.updateConnectionStatus('disconnected', '已斷開');
        });
        gameWs.on('error', () => {
            this.updateConnectionStatus('error', '連線錯誤');
            this.showError('連線異常，請重試', 'lobby');
        });

        gameWs.on('ROOM_CREATED', (msg) => this.handleRoomCreated(msg));
        gameWs.on('ROOM_JOINED', (msg) => this.handleRoomJoined(msg));
        gameWs.on('ROOM_UPDATE', (msg) => this.updateWaitingRoom(msg));
        gameWs.on('ROOMS_LIST', (msg) => this.displayRoomsList(msg));

        gameWs.on('GAME_STARTED', (msg) => this.handleGameStarted(msg));
        gameWs.on('GAME_STATE_UPDATE', (msg) => this.handleGameStateUpdate(msg));
        gameWs.on('GAME_FINISHED', (msg) => this.handleGameFinished(msg));
        gameWs.on('SUGGESTION', (msg) => this.handleSuggestion(msg));

        gameWs.on('ERROR', (msg) => this.showError(msg.message, this.gamePanel.classList.contains('active') ? 'game' : 'lobby'));
    }

    handleCreateRoom() {
        const playerName = this.playerNameInput.value.trim();
        if (!playerName) {
            this.showError('請輸入暱稱', 'lobby');
            return;
        }

        this.setLoadingState(this.confirmCreateRoomBtn || this.createRoomBtn, true);
        gameWs.send({
            type: 'CREATE_ROOM',
            roomName: `${playerName} 的牌桌`,
            playerName
        });
    }

    handleRoomCreated(msg) {
        this.currentPlayerId = msg.playerId;
        this.currentRoomId = msg.roomId;
        this.switchPanel('waiting');
        this.updateWaitingRoom(msg);
    }

    handleGetRooms(fromDrawer = false) {
        if (fromDrawer) {
            this.setLoadingState(this.loadRoomsBtn, true);
        }
        gameWs.send({ type: 'GET_ROOMS' });
    }

    openLobbyDrawer(drawerId) {
        const drawers = [this.createRoomPanel, this.roomsPanel].filter(Boolean);
        drawers.forEach(drawer => {
            drawer.classList.toggle('open', drawer.id === drawerId);
        });

        if (drawerId === 'roomsPanel') {
            this.handleGetRooms(true);
        }
    }

    closeLobbyDrawer(drawerId) {
        const drawer = document.getElementById(drawerId);
        if (drawer) {
            drawer.classList.remove('open');
        }
    }

    displayRoomsList(msg) {
        const rooms = msg.rooms || [];

        if (rooms.length === 0) {
            this.roomsList.innerHTML = '<p class="empty">暫無房間</p>';
            return;
        }

        this.roomsList.innerHTML = rooms.map(room => `
            <div class="room-card">
                <div class="room-name">${room.roomName}</div>
                <div class="room-info">
                    <span>玩家: ${room.playerCount}/${room.maxPlayers}</span>
                </div>
                <button class="btn btn-primary" onclick="gameUI.joinRoom('${room.roomId}')">加入</button>
            </div>
        `).join('');
    }

    joinRoom(roomId) {
        const playerName = this.playerNameInput.value.trim();
        if (!playerName) {
            this.showError('請輸入暱稱', 'lobby');
            return;
        }

        gameWs.send({
            type: 'JOIN_ROOM',
            roomId,
            playerName
        });
    }

    handleRoomJoined(msg) {
        this.currentPlayerId = msg.playerId;
        this.currentRoomId = msg.roomId;
        this.switchPanel('waiting');
        this.updateWaitingRoom(msg);
    }

    updateWaitingRoom(msg) {
        if (!msg || !this.roomNameDisplay) {
            return;
        }

        this.roomNameDisplay.textContent = msg.roomName;
        const players = msg.players || [];
        this.updateLobbyMetrics(players);

        for (let i = 0; i < 4; i++) {
            const seat = document.getElementById(`seat${i}`);
            const player = players[i];

            if (seat && player) {
                const seatPlayer = seat.querySelector('.seat-player');
                const label = player.bot ? `${player.name} 🤖` : player.name;
                if (seatPlayer) {
                    seatPlayer.textContent = player.ready ? `${label} ✓` : label;
                }
                seat.classList.toggle('current', player.playerId === this.currentPlayerId);
            } else if (seat) {
                const seatPlayer = seat.querySelector('.seat-player');
                if (seatPlayer) {
                    seatPlayer.textContent = '--';
                }
                seat.classList.remove('current');
            }
        }

        if (this.readyBtn) {
            this.readyBtn.disabled = false;
        }
    }

    handleReady() {
        this.setLoadingState(this.readyBtn, true);
        gameWs.send({ type: 'READY' });
    }

    handleGameStarted(msg) {
        if (!msg || !msg.gameState) {
            this.showError('遊戲開始資料無效', 'game');
            return;
        }

        this.hideSettlement();
        this.switchPanel('game');
        this.gameState = msg.gameState;
        this.playerHand = msg.hand || [];
        this.selectedCards.clear();
        this.suggestionOptionsData = {};
        this.updateGameDisplay();
        this.updateHandDisplay();
    }

    handleGameStateUpdate(msg) {
        if (!msg || !msg.gameState) {
            return;
        }

        this.gameState = msg.gameState;
        this.playerHand = msg.hand || [];
        this.selectedCards.clear();
        this.updateGameDisplay();
        this.updateHandDisplay();
    }

    handlePlayCards() {
        if (this.selectedCards.size === 0) {
            this.showError('請先選擇要出的牌', 'game');
            return;
        }

        this.setLoadingState(this.playBtn, true);
        const cards = Array.from(this.selectedCards).map(power => ({ power: parseInt(power, 10) }));
        gameWs.send({ type: 'PLAY_CARDS', cards });
    }

    handlePass() {
        this.setLoadingState(this.passBtn, true);
        gameWs.send({ type: 'PASS' });
    }

    handleSuggest() {
        this.setLoadingState(this.suggestBtn, true);
        if (this.suggestionOptions) {
            this.suggestionOptions.classList.add('open');
        }
        gameWs.send({ type: 'SUGGEST' });
    }

    handleSuggestion(msg) {
        const cards = msg.cards || [];
        this.suggestionOptionsData = msg.options || {
            conservative: cards,
            balanced: cards,
            aggressive: cards
        };

        this.renderSuggestionOptions();
        this.applySuggestionMode('conservative');
    }

    applySuggestionMode(mode) {
        const cards = this.suggestionOptionsData[mode] || [];
        this.selectedCards.clear();

        cards.forEach(card => this.selectedCards.add(card.power.toString()));
        this.updateHandDisplay();

        if (cards.length === 0) {
            this.showError('當前建議: Pass', 'game');
        }
    }

    renderSuggestionOptions() {
        if (!this.suggestionOptions) {
            return;
        }

        const options = [
            { key: 'conservative', label: '方案 A' },
            { key: 'balanced', label: '方案 B' },
            { key: 'aggressive', label: '方案 C' }
        ];

        this.suggestionOptions.innerHTML = options.map(opt => {
            const cards = this.suggestionOptionsData[opt.key] || [];
            const text = cards.length > 0 ? cards.map(c => this.cardToText(c)).join(' ') : 'Pass';
            return `<button class="btn btn-secondary suggestion-item" onclick="gameUI.applySuggestionMode('${opt.key}')">${opt.label}: ${text}</button>`;
        }).join('');
    }

    handleGameFinished(msg) {
        if (!msg) {
            this.showError('結算資料無效', 'game');
            return;
        }

        this.showSettlement(msg);
    }

    showSettlement(msg) {
        if (!this.settlementOverlay || !this.settlementWinner || !this.settlementRows || !this.gamePanel) {
            return;
        }

        const summary = msg.summary || {};
        const players = summary.players || [];

        this.settlementWinner.textContent = `贏家: ${msg.winner || '-'}`;
        this.settlementRows.innerHTML = players.map(p => `
            <div class="settlement-row">
                <div class="name">${p.name}${p.bot ? ' 🤖' : ''}</div>
                <div>勝場 ${p.wins}</div>
                <div>本局 ${p.roundDelta > 0 ? '+' : ''}${p.roundDelta}</div>
                <div>總分 ${p.totalScore}</div>
                <div>${this.renderCardStrip(p.remainingCards)}</div>
            </div>
        `).join('');

        this.gamePanel.classList.add('settlement-open');
        this.settlementOverlay.classList.add('show');
    }

    hideSettlement() {
        this.gamePanel.classList.remove('settlement-open');
        this.settlementOverlay.classList.remove('show');
    }

    updateGameDisplay() {
        if (!this.gameState) {
            return;
        }

        this.updateTurnIndicator();
        this.updateTableDisplay();
        this.updateOpponentDisplay();
        this.updatePlayerButtons();
        this.updateLastAction();
        this.updateGameBanner();
    }

    updateTurnIndicator() {
        if (!this.gameState || !this.turnIndicator) {
            return;
        }

        const state = this.gameState;
        const current = state.players[state.currentPlayer];
        const currentPlayerName = current ? current.name : '-';
        this.turnIndicator.textContent = `輪到：${currentPlayerName}`;
        if (this.turnHintPill) {
            this.turnHintPill.textContent = current && current.playerId === this.currentPlayerId ? '輪到你出牌' : `輪到 ${currentPlayerName}`;
        }
    }

    updateTableDisplay() {
        if (!this.tableCards || !this.gameState) {
            return;
        }

        const cards = this.gameState.tableCards || [];
        if (cards.length === 0) {
            this.tableCards.innerHTML = '<span class="placeholder">等待出牌...</span>';
            return;
        }

        this.tableCards.innerHTML = cards.map(card => this.createCardElement(card, true)).join('');
    }

    updateOpponentDisplay() {
        if (!this.gameState) {
            return;
        }

        const players = this.gameState.players;
        let opponentIndex = 0;

        for (let i = 0; i < players.length; i++) {
            if (players[i].playerId !== this.currentPlayerId) {
                const opponent = document.getElementById(`opponent${opponentIndex}`);
                if (opponent) {
                    const name = players[i].bot ? `${players[i].name} 🤖` : players[i].name;
                    opponent.querySelector('.opponent-name').textContent = name;
                    opponent.querySelector('.opponent-hand-size').textContent = `${players[i].handSize}`;
                    opponent.querySelector('.opponent-cards').innerHTML = this.renderCardStrip(players[i].handSize, true);
                }
                opponentIndex++;
            }
        }
    }

    updatePlayerButtons() {
        if (!this.gameState) {
            return;
        }

        const current = this.gameState.players[this.gameState.currentPlayer];
        const isCurrentPlayer = current && current.playerId === this.currentPlayerId;
        const tableCards = this.gameState.tableCards || [];

        this.suggestBtn.disabled = !isCurrentPlayer;
        this.playBtn.disabled = !isCurrentPlayer;
        this.passBtn.disabled = !(isCurrentPlayer && tableCards.length > 0);

        if (this.playerCountPill) {
            this.playerCountPill.textContent = `玩家 ${this.gameState.players.length} / 4`;
        }
    }

    updateLastAction() {
        if (this.lastAction && this.gameState) {
            this.lastAction.textContent = `最近：${this.gameState.lastAction || '暫無'}`;
        }
    }

    updateHandDisplay() {
        if (!this.handCount || !this.handCards) {
            return;
        }

        this.handCount.textContent = `${this.playerHand.length}`;

        this.handCards.innerHTML = this.playerHand.map(card => {
            const isSelected = this.selectedCards.has(card.power.toString());
            return `<div class="card ${isSelected ? 'selected' : ''}" onclick="gameUI.toggleCardSelection(${card.power})">
                ${this.getCardDisplay(card)}
            </div>`;
        }).join('');
    }

    toggleCardSelection(power) {
        const powerStr = power.toString();
        if (this.selectedCards.has(powerStr)) {
            this.selectedCards.delete(powerStr);
        } else {
            this.selectedCards.add(powerStr);
        }
        this.updateHandDisplay();
    }

    createCardElement(card, animate = false) {
        return `<div class="card ${animate ? 'table-played' : ''}">${this.getCardDisplay(card)}</div>`;
    }

    getCardDisplay(card) {
        const suits = ['♣', '♦', '♥', '♠'];
        const ranks = ['3', '4', '5', '6', '7', '8', '9', '10', 'J', 'Q', 'K', 'A', '2'];
        const suit = suits[card.suit];
        const rank = ranks[card.rank];
        return `<div class="card-content"><div class="card-suit">${suit}</div><div class="card-rank">${rank}</div></div>`;
    }

    cardToText(card) {
        const suits = ['♣', '♦', '♥', '♠'];
        const ranks = ['3', '4', '5', '6', '7', '8', '9', '10', 'J', 'Q', 'K', 'A', '2'];
        return `${suits[card.suit]}${ranks[card.rank]}`;
    }

    renderCardStrip(count, compact = false) {
        const maxVisible = compact ? 5 : 8;
        const visible = Math.min(count, maxVisible);
        const cards = Array.from({ length: visible }, (_, index) => {
            const isMore = compact && index === visible - 1 && count > maxVisible;
            return `<span class="opponent-card-mini${isMore ? ' more' : ''}"></span>`;
        }).join('');

        return `<div class="settlement-card-strip">${cards}${count > maxVisible ? '<span class="card-stack-more">…</span>' : ''}</div>`;
    }

    handleLeaveRoom() {
        gameWs.send({ type: 'LEAVE_ROOM' });
        this.hideSettlement();
        this.switchPanel('lobby');
        this.currentRoomId = null;
        this.currentPlayerId = null;
        this.suggestionOptionsData = {};
        this.suggestionOptions.innerHTML = '';
        if (this.suggestionOptions) {
            this.suggestionOptions.classList.remove('open');
        }
    }

    switchPanel(panelName) {
        document.querySelectorAll('.panel').forEach(panel => panel.classList.remove('active'));
        const panel = document.getElementById(`${panelName}Panel`);
        if (panel) {
            panel.classList.add('active');
        }
    }

    showMessage(msg) {
        console.log(msg);
    }

    setLoadingState(button, isLoading) {
        if (!button) {
            return;
        }

        button.classList.toggle('loading', !!isLoading);
        if (isLoading) {
            setTimeout(() => button.classList.remove('loading'), 500);
        }
    }

    updateLobbyMetrics(players) {
        if (this.playerCountPill) {
            this.playerCountPill.textContent = `玩家 ${players.length} / 4`;
        }
    }

    updateGameBanner() {
        if (this.playerCountPill && this.gameState) {
            this.playerCountPill.textContent = `玩家 ${this.gameState.players.length} / 4`;
        }
    }

    showError(msg, panel = 'lobby') {
        const errorElement = document.getElementById(`${panel}Error`);
        if (errorElement) {
            errorElement.textContent = msg;
            errorElement.classList.add('show');
            setTimeout(() => errorElement.classList.remove('show'), 3000);
        }
    }

    updateConnectionStatus(status, text) {
        if (!this.statusDot || !this.statusText) {
            return;
        }

        this.statusDot.className = 'status-dot ' + status;
        this.statusText.textContent = text;
        if (this.heroConnectionState) {
            this.heroConnectionState.textContent = text;
        }
    }
}

window.gameUI = new GameUI();

window.addEventListener('load', async () => {
    try {
        window.gameUI.updateConnectionStatus('connecting', '正在連接');
        await gameWs.connect();
        window.gameUI.handleGetRooms();
    } catch (e) {
        console.error('Failed to connect:', e);
        window.gameUI.showError('連線失敗，請重新整理後再試', 'lobby');
    }
});
