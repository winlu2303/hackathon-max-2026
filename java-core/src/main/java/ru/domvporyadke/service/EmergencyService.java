package ru.domvporyadke.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.domvporyadke.domain.EmergencyAlert;
import ru.domvporyadke.dto.EmergencyDtos;
import ru.domvporyadke.repository.EmergencyAlertRepository;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class EmergencyService {

    private final EmergencyAlertRepository repo;

    @Transactional
    public EmergencyDtos.Response create(EmergencyDtos.CreateRequest req) {
        EmergencyAlert alert = EmergencyAlert.builder()
                .type(req.type())
                .buildingId(req.buildingId())
                .description(req.description())
                .status("NEW")
                .createdAt(Instant.now())
                .build();
        return toResponse(repo.save(alert));
    }

    @Transactional(readOnly = true)
    public EmergencyDtos.Response get(Long id) {
        return repo.findById(id).map(this::toResponse)
                .orElseThrow(() -> new NoSuchElementException("emergency not found: " + id));
    }

    @Transactional
    public EmergencyDtos.Response ack(Long id) {
        EmergencyAlert alert = repo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("emergency not found: " + id));
        alert.setStatus("IN_PROGRESS");
        alert.setAckAt(Instant.now());
        return toResponse(repo.save(alert));
    }

    @Transactional
    public void delete(Long id) {
        if (!repo.existsById(id)) {
            throw new NoSuchElementException("emergency not found: " + id);
        }
        repo.deleteById(id);
    }

    @Transactional
    public EmergencyAlert create(Long userId, String type, String address,
                                 String apartment, String callerName, String description,
                                 Long ukId) {
        EmergencyAlert e = new EmergencyAlert();
        e.setUserId(userId);
        e.setType(type);
        e.setAddress(address);
        e.setApartment(apartment);
        e.setCallerName(callerName);
        e.setDescription(description);
        e.setStatus("NEW");
        e.setCreatedAt(Instant.now());
        e.setUkId(ukId);
        return repo.save(e);
    }

    @Transactional
    public EmergencyAlert updateCaller(Long id, String apartment, String callerName) {
        EmergencyAlert e = repo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("emergency not found: " + id));
        if (apartment != null) e.setApartment(apartment);
        if (callerName != null) e.setCallerName(callerName);
        return repo.save(e);
    }

    @Transactional(readOnly = true)
    public List<EmergencyAlert> findByUserId(Long userId) {
        return repo.findByUserIdOrderByIdDesc(userId);
    }

    @Transactional(readOnly = true)
    public List<EmergencyAlert> findOpenByUserId(Long userId) {
        return repo.findByUserIdAndStatusNotOrderByIdDesc(userId, "CLOSED");
    }

    @Transactional
    public EmergencyAlert closeForce(Long id) {
        EmergencyAlert e = repo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("emergency not found: " + id));
        e.setStatus("CLOSED");
        e.setClosedAt(Instant.now());
        return repo.save(e);
    }

    private EmergencyDtos.Response toResponse(EmergencyAlert a) {
        return new EmergencyDtos.Response(
                a.getId(), a.getType(), a.getStatus(), a.getBuildingId(),
                a.getUserId(), a.getDescription(), a.getCreatedAt(),
                a.getAckAt(), a.getDispatchedAt()
        );
    }
}