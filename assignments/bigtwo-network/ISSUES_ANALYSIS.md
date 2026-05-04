# BigTwo Network Project - Issues Analysis

**Analysis Date:** May 4, 2026  
**Project Location:** `d:\30_Project_Workspace\Source_Code\Java_Course_S2\Assignments\bigtwo-network`

---

## Summary

The bigtwo-network project is a multiplayer Big Two card game built with a Java/Spring Boot backend and vanilla JavaScript frontend. The project structure appears mostly complete with no critical compilation errors detected. However, there are several configuration issues, potential runtime problems, and missing features that could prevent the application from running smoothly.

---

## Critical Issues (Will Prevent Application from Running)

### 1. **Redis Dependency Issue - Missing Configuration or Service**
**Location:** [backend/src/main/resources/application.yml](backend/src/main/resources/application.yml)  
**Severity:** CRITICAL

**Problem:**
- Application.yml configures Redis connection on `localhost:6379` without a fallback or optional configuration
- `RoomStateRedisService` (line 6-7) depends on `StringRedisTemplate` which requires Redis to be running
- If Redis is not available, the application will fail at startup with a connection error
- No Redis startup instructions or docker-compose file provided

**Evidence:**
```yaml
spring:
  redis:
    host: localhost
    port: 6379
```

**Solution Required:**
- Either provide Redis service or make it optional with graceful fallback
- Add Docker Compose configuration for Redis
- Document Redis setup requirements

---

### 2. **Database Initialization Race Condition**
**Location:** [backend/src/main/java/com/bigtwo/persistence/GameRecordService.java](backend/src/main/java/com/bigtwo/persistence/GameRecordService.java#L15-L25)  
**Severity:** CRITICAL

**Problem:**
- `@PostConstruct` method `initTable()` runs after the bean is constructed but timing is not guaranteed
- Multiple game records being written simultaneously could hit schema issues
- No error handling for concurrent table creation attempts

**Code:**
```java
@PostConstruct
public void initTable() {
    jdbcTemplate.execute("""
        CREATE TABLE IF NOT EXISTS game_records (...)
    """);
}
```

**Solution Required:**
- Add explicit exception handling or use Flyway/Liquibase for migrations
- Ensure thread-safe table initialization

---

### 3. **Missing REST Controller or Health Check Endpoint**
**Location:** Backend configuration files  
**Severity:** HIGH

**Problem:**
- No REST controller defined (only WebSocket endpoint exists)
- No health check endpoint (`/health` or `/actuator/health`)
- Frontend might fail to verify backend is running before connecting to WebSocket
- No CORS configuration for HTTP requests (only WebSocket CORS in [WebSocketConfig.java](backend/src/main/java/com/bigtwo/server/WebSocketConfig.java#L18))

**Solution Required:**
- Add a simple REST controller with health check endpoint
- Example: `GET /api/health` → returns 200 OK
- Add CORS filter for HTTP endpoints if needed

---

## High Priority Issues (Will Cause Runtime Failures)

### 4. **WebSocket CORS Configuration Too Permissive**
**Location:** [backend/src/main/java/com/bigtwo/server/WebSocketConfig.java](backend/src/main/java/com/bigtwo/server/WebSocketConfig.java#L18)  
**Severity:** HIGH (Security & Stability)

**Problem:**
```java
registry.addHandler(gameWebSocketHandler, "/ws/game")
        .setAllowedOrigins("*");
```

**Issues:**
- `"*"` allows connections from any origin - major security vulnerability
- Credentials (if used) won't work with wildcard origin
- Production deployment risk

**Solution Required:**
- Whitelist specific origins: `.setAllowedOrigins("http://localhost:3000", "https://yourdomain.com")`
- Move to external configuration

---

### 5. **Frontend WebSocket Connection - Hardcoded Port 8080**
**Location:** [frontend/public/ws.js](frontend/public/ws.js#L15-L23)  
**Severity:** HIGH

**Problem:**
```javascript
getWebSocketUrl() {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const rawHostname = window.location.hostname || 'localhost';
    const hostname = rawHostname.includes(':') && !rawHostname.startsWith('[')
        ? `[${rawHostname}]`
        : rawHostname;
    return `${protocol}//${hostname}:8080/api/ws/game`;
}
```

**Issues:**
- **Hardcoded port 8080** in client
- If backend runs on different port, frontend connection will fail with no clear error
- No fallback retry logic for failed connections
- Max reconnect attempts hardcoded to 5 with 2000ms delay

**Solution Required:**
- Make port configurable via environment variable or configuration file
- Add more detailed connection error logging
- Increase reconnect attempts or make configurable

---

### 6. **Missing Null Safety in UI Event Handlers**
**Location:** [frontend/public/ui.js](frontend/public/ui.js#L150-L160)  
**Severity:** MEDIUM-HIGH

**Problem:**
Several UI elements are referenced without null checks:
```javascript
handleGameFinished(msg) {
    this.showSettlement(msg);  // msg could be null/undefined
}

showSettlement(msg) {
    const summary = msg.summary || {};  // Returns undefined if msg is undefined
    const players = summary.players || [];
}
```

**Issues:**
- If WebSocket sends malformed GAME_FINISHED message, app crashes
- Settlement panel might not initialize properly if msg.summary is missing

**Solution Required:**
- Add validation: `if (!msg || !msg.summary) return;`
- Implement defensive programming for all message handlers

---

### 7. **AI Turn Processing - Potential Infinite Loop or Deadlock**
**Location:** [backend/src/main/java/com/bigtwo/server/handler/GameWebSocketHandler.java](backend/src/main/java/com/bigtwo/server/handler/GameWebSocketHandler.java#L434-L500)  
**Severity:** MEDIUM-HIGH

**Problem:**
```java
private void processAiTurns(String roomId) {
    scheduleAiStep(roomId, randomAIDelay());
}

private void scheduleAiStep(String roomId, long delayMs) {
    if (!aiScheduledRooms.add(roomId)) {
        return;  // Prevents duplicate scheduling, but...
    }
    // ...
}
```

**Issues:**
- AI executor is a single-threaded scheduler (`newSingleThreadScheduledExecutor()`)
- Multiple rooms' AI turns will be processed sequentially, potentially causing timeout
- No timeout mechanism for AI operations
- If `runSingleAiStep()` takes too long, subsequent rooms are blocked

**Solution Required:**
- Use thread pool instead of single-threaded executor
- Add timeout for AI operations (e.g., 10 seconds)
- Implement backpressure mechanism

---

### 8. **Database Path Relative to Working Directory**
**Location:** [backend/src/main/resources/application.yml](backend/src/main/resources/application.yml#L30)  
**Severity:** MEDIUM

**Problem:**
```yaml
app:
  sqlite:
    url: jdbc:sqlite:./data/bigtwo.db
```

**Issues:**
- Relative path `./data/` depends on working directory when app starts
- Path could be different in development vs. Docker vs. production
- If working directory is not as expected, database won't be found

**Evidence:**
- Database file exists at: `d:\30_Project_Workspace\Source_Code\Java_Course_S2\Assignments\bigtwo-network\backend\data\bigtwo.db`

**Solution Required:**
- Use absolute path or environment variable
- Example: `jdbc:sqlite:${app.data.dir}/bigtwo.db`

---

## Medium Priority Issues (May Cause Feature Failures)

### 9. **Missing Error Handling in Game Logic - IllegalStateException Risk**
**Location:** [backend/src/main/java/com/bigtwo/game/GameEngine.java](backend/src/main/java/com/bigtwo/game/GameEngine.java#L26-35)  
**Severity:** MEDIUM

**Problem:**
```java
public void startGame(String roomId) {
    GameRoom room = roomManager.getRoom(roomId);
    if (room == null) {
        logger.warn("房间不存在: {}", roomId);
        return;  // Silent return without error propagation
    }
    
    if (room.getPlayers().size() != 4) {
        logger.warn("玩家数不足4人: {}", roomId);
        return;  // Silent return, game won't start but client doesn't know
    }
```

**Issues:**
- Game won't start if players < 4, but WebSocket handler doesn't get error response
- Client continues waiting indefinitely
- No timeout or error notification mechanism

**Solution Required:**
- Return boolean or throw exception
- Send error message back to client

---

### 10. **No Validation for Card Power Encoding**
**Location:** [backend/src/main/java/com/bigtwo/server/handler/GameWebSocketHandler.java](backend/src/main/java/com/bigtwo/server/handler/GameWebSocketHandler.java#L243-260)  
**Severity:** MEDIUM

**Problem:**
```java
List<Card> cards = new ArrayList<>();
for (var element : json.getAsJsonArray("cards")) {
    JsonObject cardObj = element.getAsJsonObject();
    if (!cardObj.has("power")) {
        continue;  // Invalid card silently skipped
    }
    cards.add(new Card(cardObj.get("power").getAsInt()));
}
```

**Issues:**
- Power value could be outside range 0-51
- No validation that power value is valid
- Could create invalid cards and cause game logic errors

**Solution Required:**
- Add validation: `if (power < 0 || power > 51) throw new IllegalArgumentException(...)`

---

### 11. **Session Cleanup on Connection Loss**
**Location:** [backend/src/main/java/com/bigtwo/server/handler/GameWebSocketHandler.java](backend/src/main/java/com/bigtwo/server/handler/GameWebSocketHandler.java#L73-95)  
**Severity:** MEDIUM

**Problem:**
```java
@Override
public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
    logger.info("WebSocket disconnected: {}", session.getId());

    String playerId = sessionPlayerMap.remove(session.getId());
    if (playerId != null) {
        sessions.remove(playerId);
        // ...
    }
}
```

**Issues:**
- Assumes `sessionPlayerMap` is always in sync with `sessions`
- If maps get out of sync (due to threading), orphaned sessions won't be cleaned up
- Could lead to memory leaks

**Solution Required:**
- Add defensive checks: verify consistency before cleanup
- Consider using a combined data structure

---

### 12. **Frontend DOM Element Null Reference Risk**
**Location:** [frontend/public/ui.js](frontend/public/ui.js#L23-60)  
**Severity:** MEDIUM

**Problem:**
```javascript
initializeElements() {
    this.lobbyPanel = document.getElementById('lobbyPanel');
    this.playerNameInput = document.getElementById('playerNameInput');
    // ... 30+ elements referenced
    
    this.readyBtn = document.getElementById('readyBtn');
    this.leaveWaitingBtn = document.getElementById('leaveWaitingBtn');
    // ...
}
```

**Issues:**
- If any HTML element is missing, `getElementById` returns null
- Subsequent method calls will throw `TypeError: Cannot read property 'addEventListener' of null`
- No error checking for missing elements

**Solution Required:**
- Add validation after each `getElementById` call
- Or check all elements in a loop with error reporting

---

### 13. **Game State Not Reset Between Rounds**
**Location:** [backend/src/main/java/com/bigtwo/game/GameEngine.java](backend/src/main/java/com/bigtwo/game/GameEngine.java#L313-335)  
**Severity:** MEDIUM

**Problem:**
- `resetGameForNewRound()` method exists but may not be called after game finishes
- Next game might start with leftover table cards or pass count

**Solution Required:**
- Ensure `resetGameForNewRound()` is called or integrate reset into game start logic

---

## Low Priority Issues (Code Quality & Maintainability)

### 14. **TODO Comment Without Implementation**
**Location:** [backend/src/main/java/com/bigtwo/game/GameEngine.java](backend/src/main/java/com/bigtwo/game/GameEngine.java#L298-303)  
**Severity:** LOW

**Problem:**
```java
room.setLastAction("遊戲結束，贏家: " + winner.getPlayerName());
gameRecordService.recordGameResult(room, winner);
roomManager.persistRoomState(room.getRoomId());
logger.info("游戏结束: {} 赢了!", winner.getPlayerName());

// TODO: 可以在这里添加积分计算、数据库记录等逻辑
// 例如：calculateScores(room, winner);
```

**Issue:**
- TODO suggests features not yet implemented
- But `applyRoundScoring()` is already implemented elsewhere

**Solution Required:**
- Remove or update TODO comment

---

### 15. **Card Strength Calculation Used Inconsistently**
**Location:** Per repository memory  
**Severity:** LOW (But Previously Flagged as Bug Risk)

**Issue:**
- Card identity encoding: `suit*13 + rank` 
- Card strength comparison: `rank*4 + suit`
- These are different and could cause gameplay bugs if mixed up

**Evidence in Code:**
- Card.compareTo() uses: `this.getRank() * 4 + this.getSuit()`
- CardStrength() uses: `card.getRank() * 4 + card.getSuit()`
- Both correct, but identity encoding is different (suit*13 + rank)

**Note:** This appears to be correctly implemented based on repository memory.

---

### 16. **No Logging Configuration**
**Location:** Backend configuration  
**Severity:** LOW

**Problem:**
- `logging:` section in [application.yml](backend/src/main/resources/application.yml) only sets levels
- No appenders configured (file output, rotating logs, etc.)
- Production issues difficult to debug

**Solution Required:**
- Add logback configuration for file appenders

---

## Configuration Issues

### 17. **Missing Frontend Build/Deployment Setup**
**Location:** Frontend directory structure  
**Severity:** MEDIUM

**Problem:**
- Frontend is plain HTML/JS files in `public/` directory
- No build process (webpack, vite, etc.)
- No package.json or dependency management
- Difficult to add dependencies or use modern tooling
- No TypeScript support despite project name suggestion

**Solution Required:**
- Add build configuration if needed
- Or document that plain JS is intentional

---

### 18. **No Docker Support**
**Location:** Project root  
**Severity:** LOW (For Development) / MEDIUM (For Deployment)

**Problem:**
- No Dockerfile or docker-compose.yml
- Difficult to set up development environment
- Redis dependency not containerized

**Solution Required:**
- Create docker-compose with Java backend + Redis + SQLite volume mount
- Add Dockerfile for backend

---

## API/Protocol Issues

### 19. **Inconsistent Error Message Formats**
**Location:** WebSocket message handlers  
**Severity:** LOW

**Problem:**
- Some errors are Chinese, some are English mixed
- No standardized error response structure
- Client can't reliably parse error codes

**Solution Required:**
- Standardize error responses: `{type: "ERROR", code: "INVALID_ROOM", message: "房間不存在", params: {}}`

---

### 20. **No Message Version/Protocol Versioning**
**Location:** [frontend/public/ws.js](frontend/public/ws.js#L4)  
**Severity:** LOW

**Problem:**
- Hard-coded version `WS_CLIENT_VERSION = '2026-04-26-2'` has no corresponding backend version
- If protocol changes, old clients will break immediately
- No negotiation or backward compatibility

**Solution Required:**
- Add version exchange on connection handshake

---

## Summary Table

| ID | Issue | Severity | Category | Status |
|----|-------|----------|----------|--------|
| 1 | Redis Dependency Required | CRITICAL | Config | NOT MET |
| 2 | Database Initialization Race Condition | CRITICAL | Runtime | RISKY |
| 3 | Missing Health Check Endpoint | HIGH | API | NOT MET |
| 4 | WebSocket CORS Too Permissive | HIGH | Security | NEEDS FIX |
| 5 | Hardcoded Backend Port 8080 | HIGH | Config | FRAGILE |
| 6 | Null Safety in UI Handlers | MEDIUM-HIGH | Code Quality | RISKY |
| 7 | AI Single-Threaded Executor | MEDIUM-HIGH | Performance | BOTTLENECK |
| 8 | Relative Database Path | MEDIUM | Config | FRAGILE |
| 9 | Missing Game Start Error Handling | MEDIUM | Runtime | RISKY |
| 10 | No Card Power Validation | MEDIUM | Validation | RISKY |
| 11 | Session Cleanup Race Condition | MEDIUM | Threading | RISKY |
| 12 | Frontend DOM Element Null Refs | MEDIUM | Code Quality | RISKY |
| 13 | Game State Not Reset Between Rounds | MEDIUM | Logic | RISKY |
| 14 | TODO Comment Inconsistent | LOW | Documentation | MINOR |
| 15 | Card Strength Calculation Risk | LOW | Logic | OK (per memory) |
| 16 | No Logging Configuration | LOW | Operations | MINOR |
| 17 | No Frontend Build Setup | MEDIUM | DevOps | FRAGILE |
| 18 | No Docker Support | MEDIUM | DevOps | MISSING |
| 19 | Inconsistent Error Formats | LOW | API | MINOR |
| 20 | No Protocol Versioning | LOW | API | MINOR |

---

## Recommendations for Running the Application

### Prerequisites to Install
1. **Java 21** (specified in pom.xml)
2. **Maven** (for building backend)
3. **Redis** (running on localhost:6379 or configured elsewhere)
4. **SQLite** (automatic, handled by backend)

### Startup Steps
1. **Start Redis:**
   ```bash
   redis-server  # or docker run -d -p 6379:6379 redis
   ```

2. **Build and Run Backend:**
   ```bash
   cd backend
   mvn clean package
   java -jar target/bigtwo-server-1.0.0.jar
   ```

3. **Serve Frontend:**
   ```bash
   # Option 1: Simple HTTP server
   cd frontend
   python -m http.server 5000  # or similar
   
   # Option 2: Use VS Code Live Server extension
   # Right-click public/index.html → Open with Live Server
   ```

4. **Access Application:**
   - Frontend: `http://localhost:5000` (or configured port)
   - Backend WebSocket: `ws://localhost:8080/api/ws/game`

### Before Deployment
- [ ] Fix Redis dependency (make optional or document)
- [ ] Fix database path to use absolute path or environment variable
- [ ] Change WebSocket CORS to specific origins
- [ ] Add health check endpoint
- [ ] Add error handling for game start failures
- [ ] Test with multiple concurrent games
- [ ] Verify session cleanup under disconnection
- [ ] Test AI turn performance with multiple rooms

---

**Generated:** 2026-05-04 | **Analysis Tool:** GitHub Copilot
