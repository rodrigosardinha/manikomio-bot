package io.quarkus.manikomio.model;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;
import java.util.List;

@Entity
@Table(name = "server_logs")
@Getter
@Setter
public class ServerLog extends PanacheEntity {
    public String eventType;
    public String description;
    public String message;
    
    @Column(name = "user_id")
    public String userId;
    public String username;
    
    @Column(name = "channel_id")
    public String channelId;
    public String channelName;
    
    @Column(name = "guild_id")
    public String guildId;
    
    @Column(name = "created_at")
    public OffsetDateTime createdAt;

    public ServerLog() {
        this.createdAt = OffsetDateTime.now();
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

    public static List<ServerLog> findByDateRange(OffsetDateTime start, OffsetDateTime end) {
        return find("createdAt BETWEEN ?1 AND ?2", start, end).list();
    }

    public static List<ServerLog> findLatestLogs(int limit) {
        return find("ORDER BY createdAt DESC").page(0, limit).list();
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