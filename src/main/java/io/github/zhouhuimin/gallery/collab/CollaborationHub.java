package io.github.zhouhuimin.gallery.collab;
import io.github.zhouhuimin.gallery.auth.*;
import io.github.zhouhuimin.gallery.common.*;
import io.github.zhouhuimin.gallery.mapper.UserMapper;
import io.github.zhouhuimin.gallery.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.*;
import org.springframework.scheduling.annotation.Scheduled;
import java.util.concurrent.ConcurrentHashMap;

@Component @RequiredArgsConstructor
public class CollaborationHub extends TextWebSocketHandler implements MessageListener {
    private final AccessService access;private final PictureService pictures;private final UserMapper users;private final Jsons json;
    private final ConcurrentHashMap<String,WebSocketSession> sessions=new ConcurrentHashMap<>();
    @Override public void afterConnectionEstablished(WebSocketSession original) throws Exception {
        if(sessions.size()>=500) { original.close(CloseStatus.POLICY_VIOLATION);return; }
        var session=new ConcurrentWebSocketSessionDecorator(original,5000,65536);sessions.put(session.getId(),session);snapshot(session,"SNAPSHOT");
    }
    private void snapshot(WebSocketSession session,String type) {
        try {
            var user=users.selectById((Long)session.getAttributes().get("userId"));var actor=AuthService.actor(user);
            var p=access.readPicture(actor,(Long)session.getAttributes().get("pictureId"));
            var message=java.util.Map.of("type",type,"picture",pictures.view(p));session.sendMessage(new TextMessage(json.write(message)));
        } catch(Exception e) { sessions.remove(session.getId());try { session.close(CloseStatus.POLICY_VIOLATION); } catch(Exception ignored) {} }
    }
    @Override protected void handleTextMessage(WebSocketSession session,TextMessage message) { snapshot(sessions.getOrDefault(session.getId(),session),"SNAPSHOT"); }
    @Override public void afterConnectionClosed(WebSocketSession session,CloseStatus status) { sessions.remove(session.getId()); }
    @Override public void handleTransportError(WebSocketSession session,Throwable exception) throws Exception { sessions.remove(session.getId());session.close(CloseStatus.SERVER_ERROR); }
    @Override public void onMessage(Message message,byte[] pattern) {
        var event=json.read(new String(message.getBody(),java.nio.charset.StandardCharsets.UTF_8));long pictureId=event.path("pictureId").asLong();
        for(var session:sessions.values()) if((Long)session.getAttributes().get("pictureId")==pictureId) snapshot(session,"PICTURE_CHANGED");
    }
    // Redis Pub/Sub is best effort. Periodic DB snapshots repair missed broadcasts and detect membership revocation even during inactivity.
    @Scheduled(fixedDelay=15000) public void reconcile() { for(var session:sessions.values()) snapshot(session,"SNAPSHOT"); }
}
