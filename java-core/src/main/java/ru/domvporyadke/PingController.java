package ru.domvporyadke.web;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.domvporyadke.service.MonthlyPingService;

@RestController
@RequestMapping("/api/v1/ping")
@RequiredArgsConstructor
public class PingController {

    private final MonthlyPingService monthlyPingService;

    @PostMapping("/trigger")
    public String trigger() {
        monthlyPingService.monthlyPing();
        return "ok";
    }
}