package io.quarkus.manikomio.service;

import io.quarkus.manikomio.model.ServerLog;
import io.quarkus.manikomio.repository.ServerLogRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.InjectMock;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@QuarkusTest
public class LoggingServiceTest {

    @Inject
    LoggingService loggingService;

    @InjectMock
    ServerLogRepository serverLogRepository;

    @Test
    void testCreateLog() {
        // Arrange
        String userId = "123";
        String username = "testUser";
        String eventType = "MESSAGE_SENT";
        String description = "Test message";
        String channelId = "456";
        String channelName = "test-channel";
        String guildId = "789";
        String message = "Test message content";

        // Act
        loggingService.createLog(userId, username, eventType, description, channelId, channelName, guildId, message);

        // Assert
        verify(serverLogRepository).persist(any(ServerLog.class));
    }

    @Test
    void testGetLogsByUserId() {
        // Arrange
        String userId = "123";
        ServerLog log1 = new ServerLog();
        log1.setUserId(userId);
        log1.setUsername("testUser");
        log1.setEventType("MESSAGE_SENT");
        log1.setDescription("Test message 1");
        log1.setChannelId("456");
        log1.setChannelName("test-channel");
        log1.setGuildId("789");
        log1.setCreatedAt(OffsetDateTime.now());

        ServerLog log2 = new ServerLog();
        log2.setUserId(userId);
        log2.setUsername("testUser");
        log2.setEventType("MESSAGE_DELETED");
        log2.setDescription("Test message 2");
        log2.setChannelId("456");
        log2.setChannelName("test-channel");
        log2.setGuildId("789");
        log2.setCreatedAt(OffsetDateTime.now());

        when(serverLogRepository.findByUserId(userId)).thenReturn(Arrays.asList(log1, log2));

        // Act
        List<ServerLog> logs = loggingService.getLogsByUserId(userId);

        // Assert
        assertEquals(2, logs.size());
        assertEquals(userId, logs.get(0).getUserId());
        assertEquals(userId, logs.get(1).getUserId());
    }

    @Test
    void testGetLogsByEventType() {
        // Arrange
        String eventType = "MESSAGE_SENT";
        ServerLog log1 = new ServerLog();
        log1.setUserId("123");
        log1.setUsername("testUser");
        log1.setEventType(eventType);
        log1.setDescription("Test message 1");
        log1.setChannelId("456");
        log1.setChannelName("test-channel");
        log1.setGuildId("789");
        log1.setCreatedAt(OffsetDateTime.now());

        ServerLog log2 = new ServerLog();
        log2.setUserId("456");
        log2.setUsername("testUser2");
        log2.setEventType(eventType);
        log2.setDescription("Test message 2");
        log2.setChannelId("789");
        log2.setChannelName("test-channel-2");
        log2.setGuildId("789");
        log2.setCreatedAt(OffsetDateTime.now());

        when(serverLogRepository.findByEventType(eventType)).thenReturn(Arrays.asList(log1, log2));

        // Act
        List<ServerLog> logs = loggingService.getLogsByEventType(eventType);

        // Assert
        assertEquals(2, logs.size());
        assertEquals(eventType, logs.get(0).getEventType());
        assertEquals(eventType, logs.get(1).getEventType());
    }

    @Test
    void testGetLogsByDateRange() {
        // Arrange
        OffsetDateTime startDate = OffsetDateTime.now().minusDays(1);
        OffsetDateTime endDate = OffsetDateTime.now();
        ServerLog log1 = new ServerLog();
        log1.setUserId("123");
        log1.setUsername("testUser");
        log1.setEventType("MESSAGE_SENT");
        log1.setDescription("Test message 1");
        log1.setChannelId("456");
        log1.setChannelName("test-channel");
        log1.setGuildId("789");
        log1.setCreatedAt(startDate.plusHours(1));

        ServerLog log2 = new ServerLog();
        log2.setUserId("456");
        log2.setUsername("testUser2");
        log2.setEventType("MESSAGE_DELETED");
        log2.setDescription("Test message 2");
        log2.setChannelId("789");
        log2.setChannelName("test-channel-2");
        log2.setGuildId("789");
        log2.setCreatedAt(endDate.minusHours(1));

        when(serverLogRepository.findByDateRange(startDate, endDate)).thenReturn(Arrays.asList(log1, log2));

        // Act
        List<ServerLog> logs = loggingService.getLogsByDateRange(startDate, endDate);

        // Assert
        assertEquals(2, logs.size());
        assertTrue(logs.get(0).getCreatedAt().isAfter(startDate));
        assertTrue(logs.get(1).getCreatedAt().isBefore(endDate));
    }

    @Test
    void testGetLatestLogs() {
        // Arrange
        int limit = 5;
        ServerLog log1 = new ServerLog();
        log1.setUserId("123");
        log1.setUsername("testUser");
        log1.setEventType("MESSAGE_SENT");
        log1.setDescription("Test message 1");
        log1.setChannelId("456");
        log1.setChannelName("test-channel");
        log1.setGuildId("789");
        log1.setCreatedAt(OffsetDateTime.now().minusHours(2));

        ServerLog log2 = new ServerLog();
        log2.setUserId("456");
        log2.setUsername("testUser2");
        log2.setEventType("MESSAGE_DELETED");
        log2.setDescription("Test message 2");
        log2.setChannelId("789");
        log2.setChannelName("test-channel-2");
        log2.setGuildId("789");
        log2.setCreatedAt(OffsetDateTime.now().minusHours(1));

        when(serverLogRepository.findLatestLogs(limit)).thenReturn(Arrays.asList(log1, log2));

        // Act
        List<ServerLog> logs = loggingService.getLatestLogs(limit);

        // Assert
        assertEquals(2, logs.size());
        assertTrue(logs.get(0).getCreatedAt().isAfter(logs.get(1).getCreatedAt()));
    }
} 