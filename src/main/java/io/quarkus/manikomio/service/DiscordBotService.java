package io.quarkus.manikomio.service;

import io.quarkus.manikomio.model.ServerLog;
import io.quarkus.manikomio.repository.ServerLogRepository;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent;
import net.dv8tion.jda.api.events.channel.ChannelDeleteEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.GuildBanEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateTimeOutEvent;
import net.dv8tion.jda.api.events.guild.GuildUnbanEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;

@ApplicationScoped
@RegisterForReflection
public class DiscordBotService extends ListenerAdapter {

    private static final Logger LOGGER = Logger.getLogger(DiscordBotService.class);

    @ConfigProperty(name = "discord.bot.token")
    String botToken;

    @ConfigProperty(name = "discord.bot.log-channel-id")
    String logChannelId;

    @Inject
    ServerLogRepository logRepository;

    @Inject
    LoggingService loggingService;

    private JDA jda;
    private static final String COMMAND_PREFIX = "!";

    public JDA getJda() {
        return jda;
    }

    void onStart(@Observes StartupEvent ev) {
        try {
            LOGGER.info("Iniciando bot do Discord...");
            
            // Verifica se o token está configurado
            if (botToken == null || botToken.isEmpty()) {
                LOGGER.error("Token do bot não configurado!");
                throw new IllegalStateException("Token do bot não configurado");
            }
            
            // Verifica se o canal de logs está configurado
            if (logChannelId == null || logChannelId.isEmpty()) {
                LOGGER.error("ID do canal de logs não configurado!");
                throw new IllegalStateException("ID do canal de logs não configurado");
            }
            
            LOGGER.info("Configurando intents do bot...");
            // Configura as intents necessárias
            EnumSet<GatewayIntent> intents = EnumSet.of(
                GatewayIntent.GUILD_MESSAGES,
                GatewayIntent.MESSAGE_CONTENT,
                GatewayIntent.GUILD_MEMBERS,
                GatewayIntent.GUILD_VOICE_STATES,
                GatewayIntent.GUILD_MODERATION
            );
            
            LOGGER.info("Configurando bot com as seguintes intents: " + intents);
            
            // Inicializa o bot
            jda = JDABuilder.createDefault(botToken)
                    .enableIntents(intents)
                    .addEventListeners(this)
                    .build();
            
            LOGGER.info("Aguardando o bot ficar pronto...");
            try {
                jda.awaitReady();
            } catch (InterruptedException e) {
                LOGGER.error("Erro ao aguardar o bot ficar pronto: " + e.getMessage(), e);
                Thread.currentThread().interrupt();
                throw new RuntimeException("Erro ao aguardar o bot ficar pronto", e);
            }
            
            // Verifica se o canal de logs existe e se o bot tem permissão
            LOGGER.info("Verificando canal de logs (ID: " + logChannelId + ")...");
            TextChannel logChannel = jda.getTextChannelById(logChannelId);
            
            if (logChannel == null) {
                LOGGER.error("Canal de logs não encontrado! ID: " + logChannelId);
                throw new IllegalStateException("Canal de logs não encontrado");
            }
            
            if (!logChannel.canTalk()) {
                LOGGER.error("Bot não tem permissão para enviar mensagens no canal de logs!");
                throw new IllegalStateException("Bot não tem permissão para enviar mensagens no canal de logs");
            }
            
            LOGGER.info("Canal de logs verificado com sucesso: " + logChannel.getName());
            
            // Envia mensagem de teste
            LOGGER.info("Enviando mensagem de teste para o canal de logs...");
            logChannel.sendMessage("✅ Bot iniciado com sucesso! Sistema de logs ativo.").queue(
                success -> LOGGER.info("Mensagem de teste enviada com sucesso!"),
                error -> LOGGER.error("Erro ao enviar mensagem de teste: " + error.getMessage())
            );
            
            LOGGER.info("Bot iniciado com sucesso!");
        } catch (Exception e) {
            LOGGER.error("Erro ao iniciar o bot: " + e.getMessage(), e);
            throw e;
        }
    }

    private void processCommand(MessageReceivedEvent event) {
        String message = event.getMessage().getContentRaw();
        if (!message.startsWith(COMMAND_PREFIX)) {
            return;
        }

        String[] args = message.substring(1).split("\\s+");
        String command = args[0].toLowerCase();

        try {
            switch (command) {
                case "testlog":
                    handleTestLogCommand(event);
                    break;
                case "logs":
                    handleLogsCommand(event, args);
                    break;
                default:
                    // Comando desconhecido
                    event.getChannel().sendMessage("❌ Comando desconhecido. Use !logs para ver os comandos disponíveis.").queue();
                    break;
            }
        } catch (Exception e) {
            LOGGER.error("Erro ao processar comando: " + e.getMessage(), e);
            event.getChannel().sendMessage("❌ Erro ao processar comando: " + e.getMessage()).queue();
        }
    }

    private void handleTestLogCommand(MessageReceivedEvent event) {
        event.getChannel().sendMessage("✅ Sistema de logs funcionando!").queue();
    }

    private void handleLogsCommand(MessageReceivedEvent event, String[] args) {
        if (args.length < 2) {
            // Mostra os últimos logs
            List<ServerLog> logs = loggingService.getLatestLogs(5);
            sendLogsResponse(event, logs, "Últimos 5 logs");
            return;
        }

        String subCommand = args[1].toLowerCase();
        
        switch (subCommand) {
            case "user":
                if (args.length < 3) {
                    event.getChannel().sendMessage("❌ Por favor, mencione um usuário. Exemplo: !logs user @usuario").queue();
                    return;
                }
                handleUserLogsCommand(event, args[2]);
                break;
                
            case "type":
                if (args.length < 3) {
                    event.getChannel().sendMessage("❌ Por favor, especifique um tipo de log. Exemplo: !logs type MESSAGE").queue();
                    return;
                }
                handleTypeLogsCommand(event, args[2]);
                break;

            case "period":
                if (args.length < 4) {
                    event.getChannel().sendMessage("❌ Por favor, especifique o período. Exemplo: !logs period 1h 5").queue();
                    return;
                }
                handlePeriodLogsCommand(event, args[2], args[3]);
                break;
                
            default:
                event.getChannel().sendMessage("❌ Subcomando desconhecido. Use:\n" +
                    "!logs - Mostra os últimos logs\n" +
                    "!logs user @usuario - Mostra logs de um usuário\n" +
                    "!logs type tipo - Mostra logs de um tipo específico\n" +
                    "!logs period tempo limite - Mostra logs do período (ex: 1h 5)").queue();
                break;
        }
    }

    private void handlePeriodLogsCommand(MessageReceivedEvent event, String period, String limit) {
        try {
            int hours = Integer.parseInt(period.replace("h", ""));
            int maxResults = Integer.parseInt(limit);
            
            if (maxResults > 10) {
                event.getChannel().sendMessage("❌ O limite máximo de resultados é 10.").queue();
                return;
            }

            OffsetDateTime end = OffsetDateTime.now();
            OffsetDateTime start = end.minusHours(hours);
            
            List<ServerLog> logs = loggingService.getLogsByDateRange(start, end);
            if (logs.size() > maxResults) {
                logs = logs.subList(0, maxResults);
            }

            sendLogsResponse(event, logs, String.format("Logs das últimas %d horas (limitado a %d resultados)", hours, maxResults));
        } catch (NumberFormatException e) {
            event.getChannel().sendMessage("❌ Formato inválido. Use: !logs period 1h 5 (onde 1h é o período e 5 é o limite)").queue();
        }
    }

    private void sendLogsResponse(MessageReceivedEvent event, List<ServerLog> logs, String title) {
        if (logs.isEmpty()) {
            event.getChannel().sendMessage("📝 Nenhum log encontrado.").queue();
            return;
        }

        StringBuilder response = new StringBuilder();
        response.append("📝 **").append(title).append(":**\n\n");
        
        for (ServerLog log : logs) {
            // Adiciona emoji baseado no tipo de evento
            String eventEmoji = getEventEmoji(log.eventType);
            response.append(eventEmoji).append(" **").append(log.eventType).append("**\n");
            response.append("📄 ").append(log.description).append("\n");
            
            if (log.username != null) {
                response.append("👤 Usuário: ").append(log.username).append("\n");
            }
            if (log.channelName != null) {
                response.append("📺 Canal: ").append(log.channelName).append("\n");
            }
            response.append("⏰ Data: ").append(log.createdAt).append("\n");
            response.append("-------------------\n");
        }

        // Divide a mensagem em partes se for muito grande
        String message = response.toString();
        if (message.length() > 2000) {
            String[] parts = message.split("-------------------");
            StringBuilder currentPart = new StringBuilder();
            
            for (String part : parts) {
                if ((currentPart.length() + part.length() + 20) > 2000) {
                    event.getChannel().sendMessage(currentPart.toString()).queue();
                    currentPart = new StringBuilder();
                }
                currentPart.append(part).append("-------------------\n");
            }
            
            if (currentPart.length() > 0) {
                event.getChannel().sendMessage(currentPart.toString()).queue();
            }
        } else {
            event.getChannel().sendMessage(message).queue();
        }
    }

    private String getEventEmoji(String eventType) {
        switch (eventType) {
            case "MESSAGE": return "💬";
            case "MESSAGE_UPDATE": return "✏️";
            case "MESSAGE_DELETE": return "🗑️";
            case "CHANNEL_CREATE": return "📝";
            case "CHANNEL_DELETE": return "❌";
            case "VOICE_STATE_UPDATE": return "🔊";
            case "MEMBER_KICK": return "👢";
            case "MEMBER_BAN": return "🔨";
            case "MEMBER_TIMEOUT": return "⏳";
            case "MEMBER_UNBAN": return "🔓";
            default: return "📋";
        }
    }

    private void handleUserLogsCommand(MessageReceivedEvent event, String userMention) {
        // Remove os caracteres de menção do ID do usuário
        String userId = userMention.replaceAll("[<@!>]", "");
        
        List<ServerLog> logs = loggingService.getLogsByUserId(userId);
        sendLogsResponse(event, logs, "Logs do usuário");
    }

    private void handleTypeLogsCommand(MessageReceivedEvent event, String eventType) {
        List<ServerLog> logs = loggingService.getLogsByEventType(eventType.toUpperCase());
        sendLogsResponse(event, logs, "Logs do tipo " + eventType.toUpperCase());
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;
        
        // Processa comandos
        if (event.getMessage().getContentRaw().startsWith(COMMAND_PREFIX)) {
            processCommand(event);
            return;
        }

        // Loga a mensagem
        loggingService.createLog(
            event.getAuthor().getId(),
            event.getAuthor().getName(),
            "MESSAGE_SENT",
            "Mensagem enviada no canal " + event.getChannel().getName(),
            event.getChannel().getId(),
            event.getChannel().getName(),
            event.getGuild().getId(),
            event.getMessage().getContentDisplay()
        );
    }

    @Override
    public void onMessageUpdate(MessageUpdateEvent event) {
        if (event.getAuthor().isBot()) return;

        loggingService.createLog(
            event.getAuthor().getId(),
            event.getAuthor().getName(),
            "MESSAGE_EDITED",
            "Mensagem editada no canal " + event.getChannel().getName(),
            event.getChannel().getId(),
            event.getChannel().getName(),
            event.getGuild().getId(),
            event.getMessage().getContentDisplay()
        );
    }

    @Override
    public void onMessageDelete(MessageDeleteEvent event) {
        loggingService.createLog(
            "SYSTEM",
            "Sistema",
            "MESSAGE_DELETED",
            "Mensagem deletada no canal " + event.getChannel().getName(),
            event.getChannel().getId(),
            event.getChannel().getName(),
            event.getGuild().getId(),
            "Mensagem deletada"
        );
    }

    @Override
    public void onChannelCreate(ChannelCreateEvent event) {
        loggingService.createLog(
            "SYSTEM",
            "Sistema",
            "CHANNEL_CREATED",
            "Canal criado: " + event.getChannel().getName(),
            event.getChannel().getId(),
            event.getChannel().getName(),
            event.getGuild().getId(),
            "Canal criado"
        );
    }

    @Override
    public void onChannelDelete(ChannelDeleteEvent event) {
        loggingService.createLog(
            "SYSTEM",
            "Sistema",
            "CHANNEL_DELETED",
            "Canal deletado: " + event.getChannel().getName(),
            event.getChannel().getId(),
            event.getChannel().getName(),
            event.getGuild().getId(),
            "Canal deletado"
        );
    }

    @Override
    public void onGuildVoiceUpdate(GuildVoiceUpdateEvent event) {
        if (event.getChannelJoined() != null && event.getChannelLeft() == null) {
            // Usuário entrou em um canal de voz
            loggingService.createLog(
                event.getMember().getId(),
                event.getMember().getEffectiveName(),
                "VOICE_JOINED",
                "Entrou no canal de voz: " + event.getChannelJoined().getName(),
                event.getChannelJoined().getId(),
                event.getChannelJoined().getName(),
                event.getGuild().getId(),
                "Entrou no canal de voz"
            );
        } else if (event.getChannelJoined() == null && event.getChannelLeft() != null) {
            // Usuário saiu de um canal de voz
            loggingService.createLog(
                event.getMember().getId(),
                event.getMember().getEffectiveName(),
                "VOICE_LEFT",
                "Saiu do canal de voz: " + event.getChannelLeft().getName(),
                event.getChannelLeft().getId(),
                event.getChannelLeft().getName(),
                event.getGuild().getId(),
                "Saiu do canal de voz"
            );
        } else if (event.getChannelJoined() != null && event.getChannelLeft() != null) {
            // Usuário mudou de canal de voz
            loggingService.createLog(
                event.getMember().getId(),
                event.getMember().getEffectiveName(),
                "VOICE_MOVED",
                "Mudou do canal " + event.getChannelLeft().getName() + " para " + event.getChannelJoined().getName(),
                event.getChannelJoined().getId(),
                event.getChannelJoined().getName(),
                event.getGuild().getId(),
                "Mudou de canal de voz"
            );
        }
    }

    @Override
    public void onGuildMemberRemove(GuildMemberRemoveEvent event) {
        loggingService.createLog(
            event.getUser().getId(),
            event.getUser().getName(),
            "MEMBER_LEFT",
            "Membro saiu do servidor",
            "SYSTEM",
            "Sistema",
            event.getGuild().getId(),
            "Saiu do servidor"
        );
    }

    @Override
    public void onGuildBan(GuildBanEvent event) {
        loggingService.createLog(
            event.getUser().getId(),
            event.getUser().getName(),
            "MEMBER_BANNED",
            "Membro banido do servidor",
            "SYSTEM",
            "Sistema",
            event.getGuild().getId(),
            "Banido do servidor"
        );
    }

    @Override
    public void onGuildMemberUpdateTimeOut(GuildMemberUpdateTimeOutEvent event) {
        if (event.getNewTimeOutEnd() != null) {
            Duration timeoutDuration = Duration.between(OffsetDateTime.now(), event.getNewTimeOutEnd());
            loggingService.createLog(
                event.getMember().getId(),
                event.getMember().getEffectiveName(),
                "MEMBER_TIMEOUT",
                "Membro silenciado por " + timeoutDuration.toMinutes() + " minutos",
                "SYSTEM",
                "Sistema",
                event.getGuild().getId(),
                "Silenciado por " + timeoutDuration.toMinutes() + " minutos"
            );
        }
    }

    @Override
    public void onGuildUnban(GuildUnbanEvent event) {
        loggingService.createLog(
            event.getUser().getId(),
            event.getUser().getName(),
            "MEMBER_UNBANNED",
            "Membro desbanido do servidor",
            "SYSTEM",
            "Sistema",
            event.getGuild().getId(),
            "Desbanido do servidor"
        );
    }

    protected void sendLogToChannel(String message) {
        try {
            LOGGER.info("Tentando enviar mensagem para o canal de log: " + message);
            TextChannel channel = jda.getTextChannelById(logChannelId);
            
            if (channel == null) {
                LOGGER.error("Canal de log não encontrado! ID: " + logChannelId);
                return;
            }
            
            LOGGER.info("Canal de log encontrado: " + channel.getName());
            
            if (!channel.canTalk()) {
                LOGGER.error("O bot não tem permissão para enviar mensagens no canal de log!");
                return;
            }
            
            channel.sendMessage(message).queue(
                success -> LOGGER.info("Mensagem enviada com sucesso para o canal de log"),
                error -> LOGGER.error("Erro ao enviar mensagem para o canal de log: " + error.getMessage())
            );
        } catch (Exception e) {
            LOGGER.error("Erro ao tentar enviar mensagem para o canal de log: " + e.getMessage(), e);
            e.printStackTrace();
        }
    }
} 