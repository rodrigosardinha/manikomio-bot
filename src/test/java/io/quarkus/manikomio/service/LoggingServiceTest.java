package io.quarkus.manikomio.service;

import io.quarkus.manikomio.model.ServerLog;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class LoggingServiceTest {

    @Inject
    LoggingService loggingService;

    @BeforeEach
    @Transactional
    void setUp() {
        // Limpar todos os logs antes de cada teste
        ServerLog.deleteAll();
    }

    @Test
    @DisplayName("Deve criar um novo log com sucesso")
    @Transactional
    void testCreateLog() {
        // Arrange
        String eventType = "TEST_EVENT";
        String description = "Test description";
        String userId = "123";
        String username = "testUser";
        String channelId = "456";
        String channelName = "test-channel";

        // Act
        ServerLog log = loggingService.createLog(eventType, description, userId, username, channelId, channelName);

        // Assert
        assertNotNull(log);
        assertEquals(eventType, log.eventType);
        assertEquals(description, log.description);
        assertEquals(userId, log.userId);
        assertEquals(username, log.username);
        assertEquals(channelId, log.channelId);
        assertEquals(channelName, log.channelName);
        assertNotNull(log.timestamp);
    }

    @Test
    @DisplayName("Deve buscar logs por tipo de evento")
    @Transactional
    void testGetLogsByEventType() {
        // Arrange
        String eventType = "TEST_EVENT";
        loggingService.createLog(eventType, "Test 1", "123", "user1", "456", "channel1");
        loggingService.createLog(eventType, "Test 2", "124", "user2", "457", "channel2");

        // Act
        List<ServerLog> logs = loggingService.getLogsByEventType(eventType);

        // Assert
        assertEquals(2, logs.size());
        assertTrue(logs.stream().allMatch(log -> log.eventType.equals(eventType)));
    }

    @Test
    @DisplayName("Deve buscar logs por ID do usuário")
    @Transactional
    void testGetLogsByUserId() {
        // Arrange
        String userId = "123";
        loggingService.createLog("EVENT1", "Test 1", userId, "user1", "456", "channel1");
        loggingService.createLog("EVENT2", "Test 2", userId, "user1", "457", "channel2");

        // Act
        List<ServerLog> logs = loggingService.getLogsByUserId(userId);

        // Assert
        assertEquals(2, logs.size());
        assertTrue(logs.stream().allMatch(log -> log.userId.equals(userId)));
    }

    @Test
    @DisplayName("Deve buscar logs por ID do canal")
    @Transactional
    void testGetLogsByChannelId() {
        // Arrange
        String channelId = "456";
        loggingService.createLog("EVENT1", "Test 1", "123", "user1", channelId, "channel1");
        loggingService.createLog("EVENT2", "Test 2", "124", "user2", channelId, "channel1");

        // Act
        List<ServerLog> logs = loggingService.getLogsByChannelId(channelId);

        // Assert
        assertEquals(2, logs.size());
        assertTrue(logs.stream().allMatch(log -> log.channelId.equals(channelId)));
    }

    @Test
    @DisplayName("Deve buscar logs por intervalo de datas")
    @Transactional
    void testGetLogsByDateRange() {
        // Arrange
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.minusHours(1);
        LocalDateTime end = now.plusHours(1);

        loggingService.createLog("EVENT1", "Test 1", "123", "user1", "456", "channel1");
        loggingService.createLog("EVENT2", "Test 2", "124", "user2", "457", "channel2");

        // Act
        List<ServerLog> logs = loggingService.getLogsByDateRange(start, end);

        // Assert
        assertTrue(logs.size() >= 2);
        assertTrue(logs.stream().allMatch(log -> 
            !log.timestamp.isBefore(start) && !log.timestamp.isAfter(end)));
    }

    @Test
    @DisplayName("Deve buscar os logs mais recentes")
    @Transactional
    void testGetLatestLogs() {
        // Arrange
        loggingService.createLog("EVENT1", "Test 1", "123", "user1", "456", "channel1");
        loggingService.createLog("EVENT2", "Test 2", "124", "user2", "457", "channel2");
        loggingService.createLog("EVENT3", "Test 3", "125", "user3", "458", "channel3");

        // Act
        List<ServerLog> logs = loggingService.getLatestLogs(2);

        // Assert
        assertEquals(2, logs.size());
        assertTrue(logs.get(0).timestamp.isAfter(logs.get(1).timestamp));
    }

    @Test
    @DisplayName("Deve contar logs por tipo de evento")
    @Transactional
    void testCountLogsByEventType() {
        // Arrange
        String eventType = "TEST_EVENT";
        loggingService.createLog(eventType, "Test 1", "123", "user1", "456", "channel1");
        loggingService.createLog(eventType, "Test 2", "124", "user2", "457", "channel2");

        // Act
        long count = loggingService.countLogsByEventType(eventType);

        // Assert
        assertEquals(2, count);
    }
} 