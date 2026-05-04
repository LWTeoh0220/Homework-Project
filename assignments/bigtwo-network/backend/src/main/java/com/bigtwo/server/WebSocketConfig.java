package com.bigtwo.server;

import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import com.bigtwo.server.handler.GameWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private GameWebSocketHandler gameWebSocketHandler;

    @Value("${app.websocket.allowed-origins:http://localhost:*,http://127.0.0.1:*,http://[::1]:*}")
    private String allowedOrigins;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(gameWebSocketHandler, "/ws/game")
                .setAllowedOriginPatterns(parseAllowedOrigins());
    }

    private String[] parseAllowedOrigins() {
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .collect(Collectors.toList());
        return origins.toArray(String[]::new);
    }
}
