package ru.domvporyadke.max;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Nats;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class NatsListener {

    @Value("${app.nats.url}")
    private String natsUrl;

    private final MessageHandler messageHandler;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private Connection natsConnection;
    private Dispatcher dispatcher;

    @PostConstruct
    public void start() throws Exception {
        natsConnection = Nats.connect(natsUrl);
        dispatcher = natsConnection.createDispatcher(msg -> {
            try {
                JsonNode update = objectMapper.readTree(msg.getData());
                messageHandler.handle(update);
            } catch (Exception e) {
                log.error("Failed to handle NATS message", e);
            }
        });
        dispatcher.subscribe("normal");
        dispatcher.subscribe("emergency");
        log.info("NATS listener started, subscribed to 'normal' and 'emergency'");
    }

    @PreDestroy
    public void stop() throws Exception {
        if (natsConnection != null) natsConnection.close();
    }
}