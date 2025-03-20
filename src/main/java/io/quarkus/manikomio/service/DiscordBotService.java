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
            logChannel.sendMessage("Bot iniciado com sucesso! 🚀").queue(
                success -> LOGGER.info("Mensagem de teste enviada com sucesso!"),
                error -> LOGGER.error("Erro ao enviar mensagem de teste: " + error.getMessage())
            );
            
            LOGGER.info("Bot iniciado com sucesso!");
        } catch (Exception e) {
            LOGGER.error("Erro ao iniciar o bot: " + e.getMessage(), e);
            throw e;
        }
    }

    @Override
    @Transactional
    public void onMessageReceived(MessageReceivedEvent event) {
        try {
            LOGGER.info("Evento de mensagem recebido");
            
            if (event.getAuthor().isBot()) {
                LOGGER.debug("Ignorando mensagem de bot");
                return;
            }

            String messageContent = event.getMessage().getContentDisplay();
            String authorName = event.getAuthor().getName();
            String channelName = event.getChannel().getName();
            
            LOGGER.info(String.format("Processando mensagem de %s no canal %s: %s", 
                authorName, channelName, messageContent));

            ServerLog log = loggingService.createLog(
                "MESSAGE_CREATE",
                String.format("%s enviou uma mensagem em #%s: %s",
                    authorName,
                    channelName,
                    messageContent),
                event.getAuthor().getId(),
                authorName,
                event.getChannel().getId(),
                channelName
            );
            
            LOGGER.info("Log criado com sucesso: " + log.id);
            sendLogToChannel(String.format("%s enviou uma mensagem em #%s: %s",
                authorName,
                channelName,
                messageContent));
            
        } catch (Exception e) {
            LOGGER.error("Erro ao processar mensagem recebida: " + e.getMessage(), e);
            e.printStackTrace();
        }
    }

    @Override
    @Transactional
    public void onMessageUpdate(MessageUpdateEvent event) {
        try {
            LOGGER.info("Evento de atualização de mensagem recebido");
            loggingService.createLog(
                "MESSAGE_UPDATE",
                String.format("%s editou uma mensagem em #%s",
                    event.getAuthor().getName(),
                    event.getChannel().getName()),
                event.getAuthor().getId(),
                event.getAuthor().getName(),
                event.getChannel().getId(),
                event.getChannel().getName()
            );
            sendLogToChannel(String.format("%s editou uma mensagem em #%s",
                event.getAuthor().getName(),
                event.getChannel().getName()));
        } catch (Exception e) {
            LOGGER.error("Erro ao processar atualização de mensagem: " + e.getMessage(), e);
            e.printStackTrace();
        }
    }

    @Override
    @Transactional
    public void onMessageDelete(MessageDeleteEvent event) {
        try {
            LOGGER.info("Evento de deleção de mensagem recebido");
            loggingService.createLog(
                "MESSAGE_DELETE",
                String.format("Uma mensagem foi deletada em #%s",
                    event.getChannel().getName()),
                null,
                null,
                event.getChannel().getId(),
                event.getChannel().getName()
            );
            sendLogToChannel(String.format("Uma mensagem foi deletada em #%s",
                event.getChannel().getName()));
        } catch (Exception e) {
            LOGGER.error("Erro ao processar deleção de mensagem: " + e.getMessage(), e);
            e.printStackTrace();
        }
    }

    @Override
    @Transactional
    public void onChannelCreate(ChannelCreateEvent event) {
        try {
            LOGGER.info("Evento de criação de canal recebido");
            loggingService.createLog(
                "CHANNEL_CREATE",
                String.format("Um novo canal foi criado: #%s",
                    event.getChannel().getName()),
                null,
                null,
                event.getChannel().getId(),
                event.getChannel().getName()
            );
            sendLogToChannel(String.format("Um novo canal foi criado: #%s",
                event.getChannel().getName()));
        } catch (Exception e) {
            LOGGER.error("Erro ao processar criação de canal: " + e.getMessage(), e);
            e.printStackTrace();
        }
    }

    @Override
    @Transactional
    public void onChannelDelete(ChannelDeleteEvent event) {
        try {
            LOGGER.info("Evento de deleção de canal recebido");
            loggingService.createLog(
                "CHANNEL_DELETE",
                String.format("O canal #%s foi deletado",
                    event.getChannel().getName()),
                null,
                null,
                event.getChannel().getId(),
                event.getChannel().getName()
            );
            sendLogToChannel(String.format("O canal #%s foi deletado",
                event.getChannel().getName()));
        } catch (Exception e) {
            LOGGER.error("Erro ao processar deleção de canal: " + e.getMessage(), e);
            e.printStackTrace();
        }
    }

    @Override
    @Transactional
    public void onGuildVoiceUpdate(GuildVoiceUpdateEvent event) {
        try {
            LOGGER.debug("Estado de voz atualizado para o usuário: " + event.getMember().getEffectiveName());
            
            String description;
            if (event.getChannelLeft() != null && event.getChannelJoined() == null) {
                description = String.format("%s saiu do canal de voz %s",
                    event.getMember().getEffectiveName(),
                    event.getChannelLeft().getName());
            } else if (event.getChannelJoined() != null && event.getChannelLeft() == null) {
                description = String.format("%s entrou no canal de voz %s",
                    event.getMember().getEffectiveName(),
                    event.getChannelJoined().getName());
            } else {
                description = String.format("%s mudou do canal %s para o canal %s",
                    event.getMember().getEffectiveName(),
                    event.getChannelLeft().getName(),
                    event.getChannelJoined().getName());
            }

            loggingService.createLog(
                "VOICE_STATE_UPDATE",
                description,
                event.getMember().getId(),
                event.getMember().getEffectiveName(),
                event.getChannelJoined() != null ? event.getChannelJoined().getId() : event.getChannelLeft().getId(),
                event.getChannelJoined() != null ? event.getChannelJoined().getName() : event.getChannelLeft().getName()
            );
            sendLogToChannel(description);
        } catch (Exception e) {
            LOGGER.error("Erro ao processar evento de atualização de estado de voz: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public void onGuildMemberRemove(GuildMemberRemoveEvent event) {
        try {
            LOGGER.info("Evento de remoção de membro recebido");
            
            // Verificar se foi um kick
            event.getGuild().retrieveAuditLogs()
                .type(ActionType.KICK)
                .limit(1)
                .queue(logs -> {
                    if (!logs.isEmpty()) {
                        AuditLogEntry entry = logs.get(0);
                        // Verifica se o log é recente (menos de 1 segundo)
                        if (entry.getTimeCreated().isAfter(OffsetDateTime.now().minusSeconds(1))) {
                            String description = String.format("%s foi expulso por %s. Motivo: %s",
                                event.getUser().getName(),
                                entry.getUser().getName(),
                                entry.getReason() != null ? entry.getReason() : "Nenhum motivo fornecido");
                            
                            loggingService.createLog(
                                "MEMBER_KICK",
                                description,
                                event.getUser().getId(),
                                event.getUser().getName(),
                                null,
                                null
                            );
                            
                            sendLogToChannel(description);
                        }
                    }
                });
        } catch (Exception e) {
            LOGGER.error("Erro ao processar evento de remoção de membro: " + e.getMessage(), e);
            e.printStackTrace();
        }
    }

    @Override
    @Transactional
    public void onGuildBan(GuildBanEvent event) {
        try {
            LOGGER.info("Evento de banimento recebido");
            
            event.getGuild().retrieveAuditLogs()
                .type(ActionType.BAN)
                .limit(1)
                .queue(logs -> {
                    if (!logs.isEmpty()) {
                        AuditLogEntry entry = logs.get(0);
                        if (entry.getTimeCreated().isAfter(OffsetDateTime.now().minusSeconds(1))) {
                            String description = String.format("%s foi banido por %s. Motivo: %s",
                                event.getUser().getName(),
                                entry.getUser().getName(),
                                entry.getReason() != null ? entry.getReason() : "Nenhum motivo fornecido");
                            
                            loggingService.createLog(
                                "MEMBER_BAN",
                                description,
                                event.getUser().getId(),
                                event.getUser().getName(),
                                null,
                                null
                            );
                            
                            sendLogToChannel(description);
                        }
                    }
                });
        } catch (Exception e) {
            LOGGER.error("Erro ao processar evento de banimento: " + e.getMessage(), e);
            e.printStackTrace();
        }
    }

    @Override
    @Transactional
    public void onGuildMemberUpdateTimeOut(GuildMemberUpdateTimeOutEvent event) {
        try {
            LOGGER.info("Evento de timeout recebido");
            
            if (event.getNewTimeOutEnd() != null) {
                event.getGuild().retrieveAuditLogs()
                    .type(ActionType.MEMBER_UPDATE)
                    .limit(1)
                    .queue(logs -> {
                        if (!logs.isEmpty()) {
                            AuditLogEntry entry = logs.get(0);
                            if (entry.getTimeCreated().isAfter(OffsetDateTime.now().minusSeconds(1))) {
                                Duration duration = Duration.between(OffsetDateTime.now(), event.getNewTimeOutEnd());
                                String description = String.format("%s recebeu timeout por %s por %d minutos. Motivo: %s",
                                    event.getMember().getEffectiveName(),
                                    entry.getUser().getName(),
                                    duration.toMinutes(),
                                    entry.getReason() != null ? entry.getReason() : "Nenhum motivo fornecido");
                                
                                loggingService.createLog(
                                    "MEMBER_TIMEOUT",
                                    description,
                                    event.getMember().getId(),
                                    event.getMember().getEffectiveName(),
                                    null,
                                    null
                                );
                                
                                sendLogToChannel(description);
                            }
                        }
                    });
            }
        } catch (Exception e) {
            LOGGER.error("Erro ao processar evento de timeout: " + e.getMessage(), e);
            e.printStackTrace();
        }
    }

    @Override
    @Transactional
    public void onGuildUnban(GuildUnbanEvent event) {
        try {
            LOGGER.info("Evento de desbanimento recebido");
            
            event.getGuild().retrieveAuditLogs()
                .type(ActionType.UNBAN)
                .limit(1)
                .queue(logs -> {
                    if (!logs.isEmpty()) {
                        AuditLogEntry entry = logs.get(0);
                        if (entry.getTimeCreated().isAfter(OffsetDateTime.now().minusSeconds(1))) {
                            String description = String.format("%s foi desbanido por %s",
                                event.getUser().getName(),
                                entry.getUser().getName());
                            
                            loggingService.createLog(
                                "MEMBER_UNBAN",
                                description,
                                event.getUser().getId(),
                                event.getUser().getName(),
                                null,
                                null
                            );
                            
                            sendLogToChannel(description);
                        }
                    }
                });
        } catch (Exception e) {
            LOGGER.error("Erro ao processar evento de desbanimento: " + e.getMessage(), e);
            e.printStackTrace();
        }
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