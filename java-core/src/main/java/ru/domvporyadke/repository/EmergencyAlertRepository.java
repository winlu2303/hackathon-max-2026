package ru.domvporyadke.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.domvporyadke.domain.EmergencyAlert;

import java.util.List;

public interface EmergencyAlertRepository extends JpaRepository<EmergencyAlert, Long> {
    List<EmergencyAlert> findByStatusAndCreatedAtBefore(String status, java.time.Instant threshold);
    List<EmergencyAlert> findByUserIdOrderByIdDesc(Long userId);

    List<EmergencyAlert> findByUserIdOrderByIdDesc(Long userId, org.springframework.data.domain.Sort sort);

    List<EmergencyAlert> findByUserIdAndStatusNotOrderByIdDesc(Long userId, String status);
}