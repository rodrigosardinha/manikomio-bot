package io.quarkus.manikomio.service;

import io.quarkus.manikomio.model.ServerLog;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@ApplicationScoped
public class LoggingService {

    @Transactional
    public ServerLog createLog(String eventType, String description, String userId, String username, 
                             String channelId, String channelName) {
        ServerLog log = new ServerLog();
        log.eventType = eventType;
        log.description = description;
        log.userId = userId;
        log.username = username;
        log.channelId = channelId;
        log.channelName = channelName;
        log.persist();
        return log;
    }

    public List<ServerLog> getLogsByEventType(String eventType) {
        return ServerLog.findByEventType(eventType);
    }

    public List<ServerLog> getLogsByUserId(String userId) {
        return ServerLog.findByUserId(userId);
    }

    public List<ServerLog> getLogsByChannelId(String channelId) {
        return ServerLog.findByChannelId(channelId);
    }

    public List<ServerLog> getLogsByDateRange(LocalDateTime start, LocalDateTime end) {
        return ServerLog.findByDateRange(start, end);
    }

    public List<ServerLog> getLatestLogs(int limit) {
        return ServerLog.findLatestLogs(limit);
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