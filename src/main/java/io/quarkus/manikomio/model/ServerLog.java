package io.quarkus.manikomio.model;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "server_logs")
public class ServerLog extends PanacheEntity {
    public String eventType;
    public String description;
    public String userId;
    public String username;
    public String channelId;
    public String channelName;
    public LocalDateTime timestamp;

    public ServerLog() {
        this.timestamp = LocalDateTime.now();
    }

    // Métodos de consulta usando Panache
    public static List<ServerLog> findByEventType(String eventType) {
        return list("eventType", eventType);
    }

    public static List<ServerLog> findByUserId(String userId) {
        return list("userId", userId);
    }

    public static List<ServerLog> findByChannelId(String channelId) {
        return list("channelId", channelId);
    }

    public static List<ServerLog> findByDateRange(LocalDateTime start, LocalDateTime end) {
        return list("timestamp between ?1 and ?2", start, end);
    }

    public static List<ServerLog> findLatestLogs(int limit) {
        return find("ORDER BY timestamp DESC").page(0, limit).list();
    }

    public static long countByEventType(String eventType) {
        return count("eventType", eventType);
    }

    public static long countByUserId(String userId) {
        return count("userId", userId);
    }

    public static long countByChannelId(String channelId) {
        return count("channelId", channelId);
    }
} 