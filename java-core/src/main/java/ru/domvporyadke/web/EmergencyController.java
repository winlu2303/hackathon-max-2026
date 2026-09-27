package ru.domvporyadke.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ru.domvporyadke.dto.EmergencyDtos;
import ru.domvporyadke.service.EmergencyService;

@RestController
@RequestMapping("/api/v1/emergency")
@RequiredArgsConstructor
public class EmergencyController {

    private final EmergencyService service;

    @PostMapping
    @PreAuthorize("hasRole('RESIDENT')")
    public ResponseEntity<EmergencyDtos.Response> create(@RequestBody @Valid EmergencyDtos.CreateRequest req) {
        return ResponseEntity.status(201).body(service.create(req));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('RESIDENT','DISPATCHER')")
    public ResponseEntity<EmergencyDtos.Response> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.get(id));
    }

    @PostMapping("/{id}/ack")
    @PreAuthorize("hasRole('DISPATCHER')")
    public ResponseEntity<EmergencyDtos.Response> ack(@PathVariable Long id) {
        return ResponseEntity.ok(service.ack(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('DISPATCHER')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}