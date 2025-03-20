package io.quarkus.manikomio.service;

import io.quarkus.manikomio.model.ServerLog;
import io.quarkus.manikomio.repository.ServerLogRepository;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent;
import net.dv8tion.jda.api.events.channel.ChannelDeleteEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import jakarta.transaction.Transactional;

import java.util.EnumSet;

@ApplicationScoped
public class DiscordBotService extends ListenerAdapter {

    private static final Logger LOG = Logger.getLogger(DiscordBotService.class);

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
            LOG.info("Iniciando o bot Discord...");
            LOG.info("Token: " + (botToken != null ? "Presente" : "Ausente"));
            LOG.info("Log Channel ID: " + (logChannelId != null ? logChannelId : "Ausente"));

            if (botToken == null || botToken.trim().isEmpty()) {
                throw new IllegalStateException("Token do bot não configurado!");
            }

            if (logChannelId == null || logChannelId.trim().isEmpty()) {
                throw new IllegalStateException("ID do canal de log não configurado!");
            }

            EnumSet<GatewayIntent> intents = EnumSet.of(
                GatewayIntent.GUILD_MESSAGES,
                GatewayIntent.MESSAGE_CONTENT,
                GatewayIntent.GUILD_MEMBERS,
                GatewayIntent.GUILD_VOICE_STATES
            );

            LOG.info("Configurando intents: " + intents);

            jda = JDABuilder.createDefault(botToken)
                    .enableIntents(intents)
                    .addEventListeners(this)
                    .build();

            LOG.info("Aguardando o bot ficar pronto...");
            jda.awaitReady();
            LOG.info("Bot está online!");
            
            // Verificar se o canal de log existe e se o bot tem permissões
            TextChannel logChannel = jda.getTextChannelById(logChannelId);
            if (logChannel == null) {
                LOG.error("Canal de log não encontrado! ID: " + logChannelId);
                LOG.info("Canais disponíveis:");
                jda.getTextChannels().forEach(channel -> 
                    LOG.info("Canal: " + channel.getName() + " (ID: " + channel.getId() + ")")
                );
                throw new IllegalStateException("Canal de log não encontrado!");
            }
            
            LOG.info("Canal de log encontrado: " + logChannel.getName() + " (ID: " + logChannel.getId() + ")");
            
            // Verificar permissões do bot
            if (!logChannel.canTalk()) {
                LOG.error("O bot não tem permissão para enviar mensagens no canal de log!");
                throw new IllegalStateException("O bot não tem permissão para enviar mensagens no canal de log!");
            }
            
            // Enviar mensagem de teste
            logChannel.sendMessage("Bot iniciado com sucesso! 🚀").queue(
                success -> LOG.info("Mensagem de teste enviada com sucesso!"),
                error -> LOG.error("Erro ao enviar mensagem de teste: " + error.getMessage())
            );
        } catch (Exception e) {
            LOG.error("Erro ao iniciar o bot: " + e.getMessage(), e);
            e.printStackTrace();
            throw new RuntimeException("Falha ao iniciar o bot", e);
        }
    }

    @Override
    @Transactional
    public void onMessageReceived(MessageReceivedEvent event) {
        try {
            LOG.info("Evento de mensagem recebido");
            
            if (event.getAuthor().isBot()) {
                LOG.debug("Ignorando mensagem de bot");
                return;
            }

            String messageContent = event.getMessage().getContentDisplay();
            String authorName = event.getAuthor().getName();
            String channelName = event.getChannel().getName();
            
            LOG.info(String.format("Processando mensagem de %s no canal %s: %s", 
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
            
            LOG.info("Log criado com sucesso: " + log.id);
            sendLogToChannel(String.format("%s enviou uma mensagem em #%s: %s",
                authorName,
                channelName,
                messageContent));
            
        } catch (Exception e) {
            LOG.error("Erro ao processar mensagem recebida: " + e.getMessage(), e);
            e.printStackTrace();
        }
    }

    @Override
    @Transactional
    public void onMessageUpdate(MessageUpdateEvent event) {
        try {
            LOG.info("Evento de atualização de mensagem recebido");
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
            LOG.error("Erro ao processar atualização de mensagem: " + e.getMessage(), e);
            e.printStackTrace();
        }
    }

    @Override
    @Transactional
    public void onMessageDelete(MessageDeleteEvent event) {
        try {
            LOG.info("Evento de deleção de mensagem recebido");
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
            LOG.error("Erro ao processar deleção de mensagem: " + e.getMessage(), e);
            e.printStackTrace();
        }
    }

    @Override
    @Transactional
    public void onChannelCreate(ChannelCreateEvent event) {
        try {
            LOG.info("Evento de criação de canal recebido");
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
            LOG.error("Erro ao processar criação de canal: " + e.getMessage(), e);
            e.printStackTrace();
        }
    }

    @Override
    @Transactional
    public void onChannelDelete(ChannelDeleteEvent event) {
        try {
            LOG.info("Evento de deleção de canal recebido");
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
            LOG.error("Erro ao processar deleção de canal: " + e.getMessage(), e);
            e.printStackTrace();
        }
    }

    @Override
    @Transactional
    public void onGuildVoiceUpdate(GuildVoiceUpdateEvent event) {
        try {
            LOG.debug("Estado de voz atualizado para o usuário: " + event.getMember().getEffectiveName());
            
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
            LOG.error("Erro ao processar evento de atualização de estado de voz: " + e.getMessage(), e);
        }
    }

    protected void sendLogToChannel(String message) {
        try {
            LOG.info("Tentando enviar mensagem para o canal de log: " + message);
            TextChannel channel = jda.getTextChannelById(logChannelId);
            
            if (channel == null) {
                LOG.error("Canal de log não encontrado! ID: " + logChannelId);
                return;
            }
            
            LOG.info("Canal de log encontrado: " + channel.getName());
            
            if (!channel.canTalk()) {
                LOG.error("O bot não tem permissão para enviar mensagens no canal de log!");
                return;
            }
            
            channel.sendMessage(message).queue(
                success -> LOG.info("Mensagem enviada com sucesso para o canal de log"),
                error -> LOG.error("Erro ao enviar mensagem para o canal de log: " + error.getMessage())
            );
        } catch (Exception e) {
            LOG.error("Erro ao tentar enviar mensagem para o canal de log: " + e.getMessage(), e);
            e.printStackTrace();
        }
    }
} 