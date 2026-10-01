package com.streamguard.live;

import com.fasterxml.jackson.databind.*;
import com.streamguard.auth.AuthService;
import com.streamguard.core.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/** Authenticated signaling only; media travels peer-to-peer, never through this socket. */
@Component
public class LiveHub extends TextWebSocketHandler {
  private final Db db;
  private final AuthService auth;
  private final ObjectMapper json;
  private final int maxViewers;
  private final Map<String, Peer> peers = new ConcurrentHashMap<>();
  private final Map<UUID, Room> rooms = new ConcurrentHashMap<>();

  private record Peer(
      WebSocketSession socket,
      UUID stream,
      UUID user,
      boolean host,
      boolean moderator,
      UUID viewerSession) {}

  private static class Room {
    String host;
    final Set<String> viewers = ConcurrentHashMap.newKeySet();
  }

  public LiveHub(
      Db db, AuthService auth, ObjectMapper json, @Value("${app.max-viewers}") int maxViewers) {
    this.db = db;
    this.auth = auth;
    this.json = json;
    this.maxViewers = maxViewers;
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession s) {
    s.setTextMessageSizeLimit(32768);
  }

  @Override
  protected synchronized void handleTextMessage(WebSocketSession socket, TextMessage message)
      throws Exception {
    try {
      var data = json.readTree(message.getPayload());
      String type = data.path("type").asText();
      if (type.equals("join")) {
        if (peers.containsKey(socket.getId())) throw new ApiError(409, "Ya estás conectado");
        UUID stream = UUID.fromString(data.path("streamId").asText());
        var row =
            db.one(
                "SELECT s.status,c.id AS channel_id,c.owner_id FROM streams s JOIN channels c ON"
                    + " c.id=s.channel_id WHERE s.id=?",
                stream);
        if (!row.get("status").equals("LIVE")) throw new ApiError(409, "La transmisión terminó");
        UUID user = auth.resolve(data.path("token").asText(null));
        boolean host = data.path("host").asBoolean(false);
        boolean owner = Objects.equals(user, row.get("owner_id"));
        if (host && !owner) throw new ApiError(403, "Solo el creador puede transmitir");
        boolean moderator =
            owner
                || (user != null
                    && db.count(
                            "SELECT count(*) FROM channel_moderators WHERE channel_id=? AND"
                                + " user_id=?",
                            row.get("channel_id"),
                            user)
                        > 0);
        Room room = rooms.computeIfAbsent(stream, k -> new Room());
        if (host && room.host != null) throw new ApiError(409, "Ya hay un emisor conectado");
        if (!host && room.viewers.size() >= maxViewers)
          throw new ApiError(429, "La sala alcanzó el límite de espectadores de esta versión");
        var wrapped = new ConcurrentWebSocketSessionDecorator(socket, 10000, 256 * 1024);
        UUID view =
            host
                ? null
                : db.insert(
                    "INSERT INTO stream_viewer_sessions(stream_id,user_id) VALUES (?,?) RETURNING"
                        + " id",
                    stream,
                    user);
        peers.put(socket.getId(), new Peer(wrapped, stream, user, host, moderator, view));
        if (host) room.host = socket.getId();
        else room.viewers.add(socket.getId());
        send(
            wrapped,
            Map.of(
                "type",
                "joined",
                "peerId",
                socket.getId(),
                "moderator",
                moderator,
                "hostOnline",
                room.host != null));
        if (host) {
          for (String viewer : room.viewers)
            send(wrapped, Map.of("type", "viewer-joined", "peerId", viewer));
        } else if (room.host != null)
          send(
              peers.get(room.host).socket(),
              Map.of("type", "viewer-joined", "peerId", socket.getId()));
        broadcast(
            stream,
            Map.of(
                "type",
                "presence",
                "viewers",
                room.viewers.size(),
                "hostOnline",
                room.host != null));
      } else {
        Peer sender = peers.get(socket.getId());
        if (sender == null) throw new ApiError(401, "Primero ingresa a una sala");
        if (type.equals("signal")) {
          Peer target = peers.get(data.path("to").asText());
          if (target == null
              || !target.stream().equals(sender.stream())
              || sender.host() == target.host())
            throw new ApiError(403, "Destino de señal inválido");
          send(
              target.socket(),
              Map.of("type", "signal", "from", socket.getId(), "payload", data.path("payload")));
        } else if (type.equals("ping")) send(sender.socket(), Map.of("type", "pong"));
        else throw new ApiError(400, "Evento desconocido");
      }
    } catch (Exception e) {
      send(
          socket,
          Map.of(
              "type",
              "error",
              "message",
              e instanceof ApiError ? e.getMessage() : "No se pudo procesar el evento"));
    }
  }

  @Override
  public synchronized void afterConnectionClosed(WebSocketSession socket, CloseStatus status) {
    Peer peer = peers.remove(socket.getId());
    if (peer == null) return;
    Room room = rooms.get(peer.stream());
    if (room == null) return;
    if (peer.viewerSession() != null)
      db.exec("UPDATE stream_viewer_sessions SET left_at=now() WHERE id=?", peer.viewerSession());
    room.viewers.remove(socket.getId());
    if (peer.host()) {
      room.host = null;
      db.exec(
          "UPDATE streams SET status='ENDED',ended_at=now() WHERE id=? AND status='LIVE'",
          peer.stream());
      broadcast(
          peer.stream(), Map.of("type", "ended", "message", "El creador finalizó la transmisión"));
    } else if (room.host != null && peers.containsKey(room.host))
      send(peers.get(room.host).socket(), Map.of("type", "viewer-left", "peerId", socket.getId()));
    broadcast(
        peer.stream(),
        Map.of(
            "type", "presence", "viewers", room.viewers.size(), "hostOnline", room.host != null));
    if (room.host == null && room.viewers.isEmpty()) rooms.remove(peer.stream());
  }

  public int viewers(UUID stream) {
    Room r = rooms.get(stream);
    return r == null ? 0 : r.viewers.size();
  }

  public boolean hosting(UUID stream) {
    Room r = rooms.get(stream);
    return r != null && r.host != null;
  }

  public void broadcast(UUID stream, Object payload) {
    peers.values().stream()
        .filter(p -> p.stream().equals(stream))
        .forEach(p -> send(p.socket(), payload));
  }

  public void toModerators(UUID stream, Object payload) {
    peers.values().stream()
        .filter(p -> p.stream().equals(stream) && p.moderator())
        .forEach(p -> send(p.socket(), payload));
  }

  public void toHost(UUID stream, Object payload) {
    peers.values().stream()
        .filter(p -> p.stream().equals(stream) && p.host())
        .forEach(p -> send(p.socket(), payload));
  }

  public void toUser(UUID user, Object payload) {
    peers.values().stream()
        .filter(p -> user.equals(p.user()))
        .forEach(p -> send(p.socket(), payload));
  }

  private void send(WebSocketSession socket, Object payload) {
    try {
      if (socket.isOpen()) socket.sendMessage(new TextMessage(json.writeValueAsString(payload)));
    } catch (Exception ignored) {
      /* disconnect cleanup occurs through lifecycle */
    }
  }
}
