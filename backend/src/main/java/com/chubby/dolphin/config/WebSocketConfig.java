package com.chubby.dolphin.config;

import com.chubby.dolphin.security.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocketMessageBroker
@Slf4j
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtUtil jwtUtil;

    public WebSocketConfig(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");          // clients subscribe here
        registry.setApplicationDestinationPrefixes("/app"); // clients send here
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/brain")
                .setAllowedOriginPatterns("http://localhost:4200", "https://*");
        registry.addEndpoint("/ws/brain")
                .setAllowedOriginPatterns("http://localhost:4200", "https://*")
                .withSockJS();  // SockJS fallback for Firefox
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null) return message;
                
                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String authHeader = accessor.getFirstNativeHeader("Authorization");
                    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                        log.warn("❌ Rejected WebSocket CONNECT: Missing/invalid Authorization header");
                        throw new MessageDeliveryException("Unauthorized STOMP connection: missing Authorization header");
                    }
                    String token = authHeader.substring(7);
                    if (!jwtUtil.isValid(token)) {
                        log.warn("❌ Rejected WebSocket CONNECT: Invalid JWT token");
                        throw new MessageDeliveryException("Unauthorized STOMP connection: invalid JWT token");
                    }
                    String email = jwtUtil.extractEmail(token);
                    String workspaceId = jwtUtil.extractWorkspaceId(token);
                    
                    if (workspaceId == null || workspaceId.isBlank()) {
                        log.warn("❌ Rejected WebSocket CONNECT: JWT token does not contain a workspaceId claim");
                        throw new MessageDeliveryException("Unauthorized STOMP connection: token lacks workspace context");
                    }
                    
                    // Store attributes in session
                    accessor.getSessionAttributes().put("email", email);
                    accessor.getSessionAttributes().put("workspaceId", workspaceId);
                    log.info("🔓 WebSocket CONNECT authenticated for email={}, workspaceId={}", email, workspaceId);
                } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    String destination = accessor.getDestination();
                    String workspaceId = (String) accessor.getSessionAttributes().get("workspaceId");
                    if (destination == null || workspaceId == null) {
                        log.warn("❌ Rejected WebSocket SUBSCRIBE: missing session workspace context");
                        throw new MessageDeliveryException("Access denied: missing session workspace context");
                    }
                    if (!isValidDestinationForWorkspace(destination, workspaceId)) {
                        log.warn("❌ Rejected WebSocket SUBSCRIBE by workspaceId={} to destination={}", workspaceId, destination);
                        throw new MessageDeliveryException("Access denied: subscription target does not match authorized workspace context");
                    }
                    log.info("✓ WebSocket SUBSCRIBE allowed for workspaceId={} to destination={}", workspaceId, destination);
                } else if (StompCommand.SEND.equals(accessor.getCommand())) {
                    String destination = accessor.getDestination();
                    String workspaceId = (String) accessor.getSessionAttributes().get("workspaceId");
                    if (destination == null || workspaceId == null) {
                        log.warn("❌ Rejected WebSocket SEND: missing session workspace context");
                        throw new MessageDeliveryException("Access denied: missing session workspace context");
                    }
                    if (!isValidDestinationForWorkspace(destination, workspaceId)) {
                        log.warn("❌ Rejected WebSocket SEND by workspaceId={} to destination={}", workspaceId, destination);
                        throw new MessageDeliveryException("Access denied: message send target does not match authorized workspace context");
                    }
                    log.info("✓ WebSocket SEND allowed for workspaceId={} to destination={}", workspaceId, destination);
                }
                
                return message;
            }
        });
    }

    private boolean isValidDestinationForWorkspace(String destination, String workspaceId) {
        // Destination could be:
        // /topic/workspace/{workspaceId}/brain
        // /topic/workspace/{workspaceId}/workflow
        // /topic/workspace/{workspaceId}/alerts
        // /topic/workspace/{workspaceId}/growth/portfolio
        if (destination.startsWith("/topic/workspace/")) {
            String[] parts = destination.split("/");
            if (parts.length >= 4) {
                String destWorkspaceId = parts[3];
                return workspaceId.equals(destWorkspaceId);
            }
        }
        if (destination.startsWith("/app/workspace/")) {
            String[] parts = destination.split("/");
            if (parts.length >= 4) {
                String destWorkspaceId = parts[3];
                return workspaceId.equals(destWorkspaceId);
            }
        }
        return false;
    }
}
