package io.quarkus.manikomio.service;

import io.quarkus.manikomio.model.ServerLog;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.awt.Color;
import java.time.OffsetDateTime;
import java.util.List;

@ApplicationScoped
public class LoggingService {

    @Inject
    DiscordBotService discordBotService;

    @ConfigProperty(name = "discord.bot.log-channel-id")
    String logChannelId;

    private String getEventEmoji(String eventType) {
        return switch (eventType) {
            case "MESSAGE_SENT" -> "✉️";
            case "MESSAGE_EDITED" -> "📝";
            case "MESSAGE_DELETED" -> "🗑️";
            case "MEMBER_JOINED" -> "👋";
            case "MEMBER_LEFT" -> "👋";
            case "MEMBER_BANNED" -> "🔨";
            case "MEMBER_UNBANNED" -> "🔓";
            case "MEMBER_TIMEOUT" -> "⏰";
            case "CHANNEL_CREATED" -> "📝";
            case "CHANNEL_DELETED" -> "🗑️";
            case "VOICE_JOINED" -> "🎤";
            case "VOICE_LEFT" -> "🎤";
            case "VOICE_MOVED" -> "🔄";
            default -> "📋";
        };
    }

    @Transactional
    public ServerLog createLog(String userId, String username, String eventType, String description, 
                             String channelId, String channelName, String guildId, String message) {
        try {
            System.out.println("Iniciando criação de log...");
            System.out.println("Parâmetros: userId=" + userId + ", username=" + username + ", eventType=" + eventType);
            
            // Criar o log no banco de dados
            ServerLog log = new ServerLog();
            log.setEventType(eventType);
            log.setDescription(description);
            log.setMessage(message);
            log.setUserId(userId);
            log.setUsername(username);
            log.setChannelId(channelId);
            log.setChannelName(channelName);
            log.setGuildId(guildId);
            log.setCreatedAt(OffsetDateTime.now());
            
            System.out.println("Persistindo log no banco de dados...");
            log.persist();
            System.out.println("Log persistido com sucesso!");

            // Enviar mensagem para o canal de logs
            System.out.println("Tentando enviar mensagem para o canal de logs (ID: " + logChannelId + ")...");
            TextChannel logChannel = discordBotService.getJda().getTextChannelById(logChannelId);
            if (logChannel != null) {
                System.out.println("Canal de logs encontrado: " + logChannel.getName());
                EmbedBuilder embed = new EmbedBuilder()
                    .setColor(Color.BLUE)
                    .setTitle(getEventEmoji(eventType) + " " + eventType)
                    .setDescription(description)
                    .addField("Usuário", username, true)
                    .addField("Canal", channelName, true);

                // Adiciona a mensagem/ação se existir
                if (message != null && !message.isEmpty()) {
                    embed.addField("Conteúdo", message, false);
                }

                embed.setTimestamp(OffsetDateTime.now());

                logChannel.sendMessageEmbeds(embed.build()).queue(
                    success -> System.out.println("Log enviado com sucesso para o canal"),
                    error -> {
                        System.err.println("Erro ao enviar log para o canal: " + error.getMessage());
                        error.printStackTrace();
                    }
                );
            } else {
                System.err.println("Canal de logs não encontrado! ID: " + logChannelId);
            }

            return log;
        } catch (Exception e) {
            System.err.println("Erro ao criar log: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
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

    public List<ServerLog> getLogsByDateRange(OffsetDateTime start, OffsetDateTime end) {
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