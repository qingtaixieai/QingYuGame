package game.world;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.*;
import org.springframework.web.socket.*;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import java.util.*;

@Configuration
@EnableWebSocket
public class SocketConfig implements WebSocketConfigurer {
    private final WorldService world;private final AccountService accounts;private final String origin;
    SocketConfig(WorldService world,AccountService accounts,@Value("${app.origin}") String origin){this.world=world;this.accounts=accounts;this.origin=origin;}
    @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry registry){
        registry.addHandler(new TextWebSocketHandler(){
            @Override public void afterConnectionEstablished(WebSocketSession s){
                s.setTextMessageSizeLimit(1024);s.setBinaryMessageSizeLimit(1024);
                world.connect((UUID)s.getAttributes().get("user"),(String)s.getAttributes().get("token"),(String)s.getAttributes().get("ip"),s);
            }
            @Override public void afterConnectionClosed(WebSocketSession s,CloseStatus status){world.disconnect(s.getId());}
            @Override public void handleTransportError(WebSocketSession s,Throwable error)throws Exception{world.disconnect(s.getId());s.close();}
            @Override protected void handleTextMessage(WebSocketSession s,TextMessage message)throws Exception{
                // All mutations use authenticated, CSRF-protected HTTP commands. Socket is state delivery only.
                s.close(CloseStatus.POLICY_VIOLATION);
            }
        },"/ws").setAllowedOrigins(origin).addInterceptors(new HandshakeInterceptor(){
            public boolean beforeHandshake(ServerHttpRequest request,ServerHttpResponse response,WebSocketHandler handler,Map<String,Object> attrs){
                try{
                    var req=((ServletServerHttpRequest)request).getServletRequest();
                    var a=accounts.require(req,true,false);attrs.put("ip",ModerationService.ip(req.getRemoteAddr()));attrs.put("user",a.id());attrs.put("token",accounts.token(req));return true;
                }catch(Exception e){response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);return false;}
            }
            public void afterHandshake(ServerHttpRequest request,ServerHttpResponse response,WebSocketHandler handler,Exception exception){}
        });
    }
}
