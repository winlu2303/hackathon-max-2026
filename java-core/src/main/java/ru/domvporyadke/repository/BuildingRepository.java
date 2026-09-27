package ru.domvporyadke.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.domvporyadke.domain.Building;

public interface BuildingRepository extends JpaRepository<Building, Long> {
}