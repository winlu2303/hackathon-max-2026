package ru.domvporyadke.max;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;

import javax.net.ssl.SSLException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class MAXApiClient {

    private final String apiUrl;
    private final String botToken;
    private final WebClient webClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public MAXApiClient(@Value("${app.max.api-url}") String apiUrl,
                        @Value("${app.max.bot-token}") String botToken) throws SSLException {
        this.apiUrl = apiUrl.endsWith("/") ? apiUrl.substring(0, apiUrl.length() - 1) : apiUrl;
        this.botToken = botToken;

        log.info("MAXApiClient init: apiUrl={}, tokenPrefix={}",
                this.apiUrl,
                botToken == null ? "null" : botToken.substring(0, Math.min(12, botToken.length())));

        HttpClient httpClient = HttpClient.create()
                .secure(spec -> {
                    try {
                        spec.sslContext(
                                SslContextBuilder.forClient()
                                        .trustManager(InsecureTrustManagerFactory.INSTANCE)
                                        .build()
                        );
                    } catch (SSLException e) {
                        throw new RuntimeException(e);
                    }
                });

        this.webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    public void sendMessage(long chatId, String text, List<List<Map<String, Object>>> rows) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("text", text);

            if (rows != null && !rows.isEmpty()) {
                body.put("attachments", List.of(
                    Map.of("type", "inline_keyboard", "payload", Map.of("buttons", rows))
                ));
            }

            String jsonBody = mapper.writeValueAsString(body);
            String url = apiUrl + "/messages?chat_id=" + chatId;

            log.info("SENDING TO MAX: url={}, body={}", url, jsonBody);

            String response = webClient.post()
                    .uri(url)
                    .header("Authorization", botToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(jsonBody.getBytes(StandardCharsets.UTF_8))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.info("MAX send OK: {}", response);
        } catch (WebClientResponseException e) {
            log.error("MAX send HTTP error: status={}, body={}",
                    e.getStatusCode(), e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("MAX send failed", e);
        }
    }

    public void sendSimpleMessage(long chatId, String text) {
        sendMessage(chatId, text, null);
    }

    public static List<Map<String, Object>> row(String text, String payload) {
        return List.of(
                Map.of("type", "callback", "text", text, "payload", payload)
        );
    }
}