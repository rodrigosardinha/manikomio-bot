# Manikomio Bot

Bot do Discord para registrar logs do servidor MaNiKoMiO.

## Requisitos

- Java 17 ou superior
- Maven
- PostgreSQL
- Token do Bot do Discord
- ID do canal de logs

## Configuração

1. Clone o repositório
2. Configure o banco de dados PostgreSQL:
   - Crie um banco de dados chamado `manikomio_bot`
   - Configure as credenciais no arquivo `src/main/resources/application.yaml`

3. Configure as variáveis de ambiente:
   ```bash
   export DISCORD_BOT_TOKEN=seu_token_aqui
   export DISCORD_LOG_CHANNEL_ID=id_do_canal_de_logs
   ```

4. Compile o projeto:
   ```bash
   mvn clean package
   ```

5. Execute o bot:
   ```bash
   java -jar target/quarkus-app/quarkus-run.jar
   ```

## Funcionalidades

O bot registra os seguintes eventos:
- Criação de mensagens
- Deleção de mensagens
- Atualização de mensagens
- Criação de canais
- Deleção de canais

Todos os eventos são:
- Salvos no banco de dados PostgreSQL
- Enviados para o canal de logs configurado
- Formatados de forma clara e organizada

## Estrutura do Projeto

- `src/main/java/io/quarkus/manikomio/model/ServerLog.java`: Entidade para armazenar os logs
- `src/main/java/io/quarkus/manikomio/service/DiscordBotService.java`: Serviço que gerencia o bot e os logs
- `src/main/resources/application.yaml`: Configurações do projeto # manikomio-bot
