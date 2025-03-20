package io.quarkus.manikomio.service;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.event.Observes;
import org.jboss.logging.Logger;

@Alternative
@ApplicationScoped
public class MockDiscordBotService extends DiscordBotService {

    private static final Logger LOG = Logger.getLogger(MockDiscordBotService.class);

    @Override
    void onStart(@Observes StartupEvent ev) {
        LOG.info("Iniciando mock do bot Discord para testes...");
        // Não faz nada, apenas simula o início do bot
    }

    @Override
    protected void sendLogToChannel(String message) {
        LOG.info("Mock - Mensagem enviada para o canal: " + message);
    }
} 