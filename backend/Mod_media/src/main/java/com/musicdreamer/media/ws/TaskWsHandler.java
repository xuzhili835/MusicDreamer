package com.musicdreamer.media.ws;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * WebSocket 任务进度通道（设计 12.4）：ws://host/ws/task/{taskId}。
 * 事件：CONNECTED / PROGRESS / SUCCESS / FAILED / CANCELLED。
 */
@Slf4j
@Configuration
@EnableWebSocket
public class TaskWsHandler extends TextWebSocketHandler implements WebSocketConfigurer {

    private final Map<Integer, List<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(this, "/ws/task/*").setAllowedOriginPatterns("*");
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Integer taskId = taskIdOf(session);
        if (taskId == null) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }
        sessions.computeIfAbsent(taskId, k -> new CopyOnWriteArrayList<>()).add(session);
        send(session, "CONNECTED", "{\"taskId\":" + taskId
                + ",\"connectTime\":" + System.currentTimeMillis() + "}");
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Integer taskId = taskIdOf(session);
        if (taskId != null) {
            List<WebSocketSession> list = sessions.get(taskId);
            if (list != null) {
                list.remove(session);
            }
        }
    }

    /** 任务状态变化时广播（ProgressBroadcaster 调用）。 */
    public void broadcast(int taskId, String eventType, String dataJson) {
        List<WebSocketSession> list = sessions.get(taskId);
        if (list == null || list.isEmpty()) {
            return;
        }
        String payload = "{\"eventType\":\"" + eventType + "\",\"data\":" + dataJson + "}";
        for (WebSocketSession s : list) {
            send(s, eventType, dataJson == null ? "{}" : dataJson);
        }
        log.debug("ws broadcast task={} event={} payload={}", taskId, eventType, payload);
    }

    private void send(WebSocketSession session, String eventType, String dataJson) {
        try {
            session.sendMessage(new TextMessage(
                    "{\"eventType\":\"" + eventType + "\",\"data\":" + dataJson + "}"));
        } catch (IOException e) {
            log.warn("ws send failed: {}", e.getMessage());
        }
    }

    private Integer taskIdOf(WebSocketSession session) {
        try {
            URI uri = session.getUri();
            String[] parts = uri.getPath().split("/");
            return Integer.valueOf(parts[parts.length - 1]);
        } catch (Exception e) {
            return null;
        }
    }
}
