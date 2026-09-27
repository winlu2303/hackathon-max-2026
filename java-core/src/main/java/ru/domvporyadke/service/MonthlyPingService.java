package ru.domvporyadke.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import ru.domvporyadke.domain.MaxUser;
import ru.domvporyadke.max.MAXApiClient;
import ru.domvporyadke.repository.MaxUserRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MonthlyPingService {

    private final MaxUserRepository repo;
    private final MAXApiClient maxApiClient;

    @Scheduled(cron = "0 0 10 * * *")
    public void monthlyPing() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(30);
        List<MaxUser> users = repo.findAll();
        int sent = 0;
        for (MaxUser u : users) {
            if (u.getLastPingAt() == null || u.getLastPingAt().isBefore(threshold)) {
                try {
                    maxApiClient.sendSimpleMessage(u.getChatId(),
                            "Здравствуйте. Наши коммунальные службы стараются " +
                            "поддерживать Ваш дом в порядке. Подскажите, " +
                            "пожалуйста, есть ли необходимость вызова какого-либо мастера?");
                    u.setLastPingAt(LocalDateTime.now());
                    repo.save(u);
                    sent++;
                } catch (Exception e) {
                    log.warn("Ping failed for chat {}", u.getChatId(), e);
                }
            }
        }
        log.info("MonthlyPing: sent {} pings", sent);
    }
}