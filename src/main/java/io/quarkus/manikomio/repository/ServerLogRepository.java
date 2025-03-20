package io.quarkus.manikomio.repository;

import io.quarkus.manikomio.model.ServerLog;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.OffsetDateTime;
import java.util.List;

@ApplicationScoped
public class ServerLogRepository implements PanacheRepository<ServerLog> {
    
    public List<ServerLog> findByDateRange(OffsetDateTime start, OffsetDateTime end) {
        return ServerLog.findByDateRange(start, end);
    }

    public List<ServerLog> findLatestLogs(int limit) {
        return ServerLog.findLatestLogs(limit);
    }

    public List<ServerLog> findByEventType(String eventType) {
        return ServerLog.findByEventType(eventType);
    }

    public List<ServerLog> findByUserId(String userId) {
        return ServerLog.findByUserId(userId);
    }

    public List<ServerLog> findByChannelId(String channelId) {
        return ServerLog.findByChannelId(channelId);
    }

    public long countByEventType(String eventType) {
        return ServerLog.countByEventType(eventType);
    }

    public long countByUserId(String userId) {
        return ServerLog.countByUserId(userId);
    }

    public long countByChannelId(String channelId) {
        return ServerLog.countByChannelId(channelId);
    }
} 