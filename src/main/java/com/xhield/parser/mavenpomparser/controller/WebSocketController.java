package com.xhield.parser.mavenpomparser.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Controller
@RequestMapping("")
public class WebSocketController {
    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    private static final AtomicInteger activeUsers = new AtomicInteger(0);
    private static final ConcurrentHashMap<String, Boolean> clientStatus = new ConcurrentHashMap<>();

    // Track connected users
    public void userConnected(String sessionId) {
        clientStatus.put(sessionId, true);
        activeUsers.incrementAndGet();
        broadcastServerStatus();
    }

    // Track disconnected users
    public void userDisconnected(String sessionId) {
        clientStatus.remove(sessionId);
        activeUsers.decrementAndGet();
        broadcastServerStatus();
    }
    // Simulate periodic server status updates every 5 seconds
    @Scheduled(fixedRate = 5000)
    public void sendServerStatus() {
        boolean isServerUp = checkServerStatus(); // Implement your server health check logic
        messagingTemplate.convertAndSend("/api/topic/server-status", isServerUp ? "true" : "false");
    }
    private boolean checkServerStatus() {
        // Simulate a simple health check (replace with real logic)
        return true;
    }
    // Send server status update
    private void broadcastServerStatus() {
        boolean isUp = activeUsers.get() > 0; // If at least one user is connected, server is up
        messagingTemplate.convertAndSend("/api/topic/server-status", isUp);
    }

    @PostMapping("/send")
    public ResponseEntity sendMessage(@RequestBody String message) {
        messagingTemplate.convertAndSend("/topic/notifications", message);
        return ResponseEntity.ok( "Message sent: " + message);
    }
}

