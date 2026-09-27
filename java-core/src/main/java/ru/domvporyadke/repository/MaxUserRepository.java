package ru.domvporyadke.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.domvporyadke.domain.MaxUser;
import java.util.Optional;

public interface MaxUserRepository extends JpaRepository<MaxUser, Long> {
    Optional<MaxUser> findByChatId(Long chatId);
    Optional<MaxUser> findByUserId(Long userId);
}