package ru.domvporyadke.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.domvporyadke.domain.ServiceRequest;

import java.util.List;

@Repository
public interface ServiceRequestRepository extends JpaRepository<ServiceRequest, Long> {
    List<ServiceRequest> findByUserId(Long userId);
    List<ServiceRequest> findByUserIdOrderByIdDesc(Long userId);
    List<ServiceRequest> findByUserIdAndStatusNotOrderByIdDesc(Long userId, String status);
}