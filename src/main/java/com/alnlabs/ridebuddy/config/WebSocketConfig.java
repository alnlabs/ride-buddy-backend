package com.alnlabs.ridebuddy.config;

import com.alnlabs.ridebuddy.auth.JwtService;
import io.jsonwebtoken.Claims;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.nio.charset.StandardCharsets;
import java.net.URLDecoder;
import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtService jwtService;
    private final AppProperties appProperties;

    public WebSocketConfig(JwtService jwtService, AppProperties appProperties) {
        this.jwtService = jwtService;
        this.appProperties = appProperties;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String origins = appProperties.cors().allowedOrigins();
        var sockJs = registry.addEndpoint("/ws")
                .addInterceptors(new JwtHandshakeInterceptor(jwtService));
        var nativeEp = registry.addEndpoint("/ws-native")
                .addInterceptors(new JwtHandshakeInterceptor(jwtService));
        if ("*".equals(origins)) {
            sockJs.setAllowedOriginPatterns("*");
            nativeEp.setAllowedOriginPatterns("*");
        } else {
            String[] list = origins.split(",");
            sockJs.setAllowedOrigins(list);
            nativeEp.setAllowedOrigins(list);
        }
        sockJs.withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null) return message;
                if (StompCommand.CONNECT.equals(accessor.getCommand())
                        || StompCommand.SUBSCRIBE.equals(accessor.getCommand())
                        || StompCommand.SEND.equals(accessor.getCommand())) {
                    Map<String, Object> session = accessor.getSessionAttributes();
                    if (session != null) {
                        Object userId = session.get("userId");
                        if (userId != null) {
                            accessor.setUser(new UsernamePasswordAuthenticationToken(
                                    userId.toString(), null, List.of()));
                        }
                    }
                }
                return message;
            }
        });
    }

    static final class JwtHandshakeInterceptor implements HandshakeInterceptor {
        private final JwtService jwtService;

        JwtHandshakeInterceptor(JwtService jwtService) {
            this.jwtService = jwtService;
        }

        @Override
        public boolean beforeHandshake(
                ServerHttpRequest request,
                ServerHttpResponse response,
                WebSocketHandler wsHandler,
                Map<String, Object> attributes
        ) {
            String token = extractToken(request);
            if (token == null || token.isBlank()) {
                return false;
            }
            try {
                Claims claims = jwtService.parse(token);
                if (!jwtService.isAccessToken(claims)) {
                    return false;
                }
                attributes.put("userId", claims.getSubject());
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        public void afterHandshake(
                ServerHttpRequest request,
                ServerHttpResponse response,
                WebSocketHandler wsHandler,
                Exception exception
        ) {
            // no-op
        }

        private static String extractToken(ServerHttpRequest request) {
            List<String> auth = request.getHeaders().get("Authorization");
            if (auth != null && !auth.isEmpty()) {
                String h = auth.get(0);
                if (h != null && h.startsWith("Bearer ")) {
                    return h.substring(7);
                }
            }
            if (request instanceof ServletServerHttpRequest servletRequest) {
                String q = servletRequest.getServletRequest().getParameter("access_token");
                if (q != null && !q.isBlank()) {
                    return q;
                }
            }
            String query = request.getURI().getQuery();
            if (query != null) {
                for (String part : query.split("&")) {
                    int eq = part.indexOf('=');
                    if (eq > 0 && "access_token".equals(part.substring(0, eq))) {
                        return URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8);
                    }
                }
            }
            return null;
        }
    }
}
