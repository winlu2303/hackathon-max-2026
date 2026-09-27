package ru.domvporyadke.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ru.domvporyadke.dto.ServiceRequestDtos;
import ru.domvporyadke.service.ServiceRequestService;

import java.util.List; 

@RestController
@RequestMapping("/api/v1/requests")
@RequiredArgsConstructor
public class ServiceRequestController {

    private final ServiceRequestService service;

    @PostMapping
    @PreAuthorize("hasRole('RESIDENT')")
    public ResponseEntity<ServiceRequestDtos.Response> create(@RequestBody @Valid ServiceRequestDtos.CreateRequest req) {
        return ResponseEntity.status(201).body(service.create(req));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('RESIDENT','MASTER','DISPATCHER')")
    public ResponseEntity<ServiceRequestDtos.Response> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.get(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('DISPATCHER')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('RESIDENT','MASTER','DISPATCHER')")
    public ResponseEntity<List<ServiceRequestDtos.Response>> listByUser(@RequestParam Long userId) {
        return ResponseEntity.ok(service.listByUser(userId));
    }
}