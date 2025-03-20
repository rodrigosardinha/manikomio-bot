package io.quarkus.manikomio.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.manikomio.model.ServerLog;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.LocalDateTime;
import java.util.List;

@ApplicationScoped
public class ServerLogRepository implements PanacheRepository<ServerLog> {
    
    public List<ServerLog> findLogsByDateRange(LocalDateTime start, LocalDateTime end) {
        return ServerLog.findByDateRange(start, end);
    }

    public List<ServerLog> findLatestLogs(int limit) {
        return ServerLog.findLatestLogs(limit);
    }

    public List<ServerLog> findLogsByEventType(String eventType) {
        return ServerLog.findByEventType(eventType);
    }

    public List<ServerLog> findLogsByUserId(String userId) {
        return ServerLog.findByUserId(userId);
    }

    public List<ServerLog> findLogsByChannelId(String channelId) {
        return ServerLog.findByChannelId(channelId);
    }

    public long countLogsByEventType(String eventType) {
        return ServerLog.countByEventType(eventType);
    }

    public long countLogsByUserId(String userId) {
        return ServerLog.countByUserId(userId);
    }

    public long countLogsByChannelId(String channelId) {
        return ServerLog.countByChannelId(channelId);
    }
} 