package ru.domvporyadke.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.domvporyadke.domain.ServiceRequest;
import ru.domvporyadke.dto.ServiceRequestDtos;
import ru.domvporyadke.repository.ServiceRequestRepository;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class ServiceRequestService {

    private final ServiceRequestRepository repo;

    @Transactional
    public ServiceRequestDtos.Response create(ServiceRequestDtos.CreateRequest req) {
        Instant now = Instant.now();
        ServiceRequest r = ServiceRequest.builder()
                .category(req.category())
                .subtype(req.subtype())
                .buildingId(req.buildingId())
                .preferredTime(req.preferredTime())
                .description(req.description())
                .status("NEW")
                .createdAt(now)
                .registeredAt(now)
                .responseDueAt(now.plusSeconds(30L * 24 * 3600))
                .build();
        return toResponse(repo.save(r));
    }

    @Transactional(readOnly = true)
    public ServiceRequestDtos.Response get(Long id) {
        return repo.findById(id).map(this::toResponse)
                .orElseThrow(() -> new NoSuchElementException("request not found: " + id));
    }

    @Transactional
    public void delete(Long id) {
        if (!repo.existsById(id)) {
            throw new NoSuchElementException("request not found: " + id);
        }
        repo.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<ServiceRequestDtos.Response> listByUser(Long userId) {
        return repo.findByUserId(userId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public ServiceRequest create(Long userId, String category, String address,
                                 String apartment, String callerName, String description,
                                 Long ukId) {
        Instant now = Instant.now();
        ServiceRequest r = new ServiceRequest();
        r.setUserId(userId);
        r.setCategory(category);
        r.setAddress(address);
        r.setApartment(apartment);
        r.setCallerName(callerName);
        r.setDescription(description);
        r.setStatus("NEW");
        r.setCreatedAt(now);
        r.setRegisteredAt(now);
        r.setUkId(ukId);
        return repo.save(r);
    }

    @Transactional
    public ServiceRequest updateCaller(Long id, String apartment, String callerName) {
        ServiceRequest r = repo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("request not found: " + id));
        if (apartment != null) r.setApartment(apartment);
        if (callerName != null) r.setCallerName(callerName);
        return repo.save(r);
    }

    @Transactional(readOnly = true)
    public List<ServiceRequest> findByUserId(Long userId) {
        return repo.findByUserIdOrderByIdDesc(userId);
    }

    @Transactional(readOnly = true)
    public List<ServiceRequest> findOpenByUserId(Long userId) {
        return repo.findByUserIdAndStatusNotOrderByIdDesc(userId, "CLOSED");
    }

    @Transactional
    public ServiceRequest closeForce(Long id) {
        ServiceRequest r = repo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("request not found: " + id));
        r.setStatus("CLOSED");
        r.setClosedAt(Instant.now());
        return repo.save(r);
    }

    private ServiceRequestDtos.Response toResponse(ServiceRequest r) {
        return new ServiceRequestDtos.Response(
                r.getId(), r.getCategory(), r.getSubtype(), r.getStatus(),
                r.getBuildingId(), r.getUserId(), r.getMasterId(), r.getCreatedAt()
        );
    }
}