package ru.domvporyadke.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.domvporyadke.domain.MaxUser;
import ru.domvporyadke.repository.MaxUserRepository;

@Service
public class MaxUserService {

    private final MaxUserRepository repo;

    public MaxUserService(MaxUserRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public MaxUser getOrCreate(Long chatId) {
        return repo.findByChatId(chatId).orElseGet(() -> repo.save(new MaxUser(chatId)));
    }

    @Transactional
    public MaxUser linkToUser(Long chatId, Long userId) {
        MaxUser u = getOrCreate(chatId);
        u.setUserId(userId);
        return repo.save(u);
    }

    @Transactional(readOnly = true)
    public Long getLinkedUserId(Long chatId) {
        return repo.findByChatId(chatId).map(MaxUser::getUserId).orElse(null);
    }

    @Transactional(readOnly = true)
    public Long getUkId(Long chatId) {
        return repo.findByChatId(chatId).map(MaxUser::getUkId).orElse(null);
    }

    @Transactional
    public void setPhone(Long chatId, String phone) {
        MaxUser u = getOrCreate(chatId);
        u.setPhone(phone);
        repo.save(u);
    }

    @Transactional
    public void saveProfile(Long chatId, String role, String address, Long buildingId, Long ukId) {
        MaxUser u = getOrCreate(chatId);
        u.setRole(role);
        u.setAddress(address);
        u.setBuildingId(buildingId);
        u.setUkId(ukId);
        repo.save(u);
    }

    @Transactional(readOnly = true)
    public MaxUser findByChatId(Long chatId) {
        return repo.findByChatId(chatId).orElse(null);
    }
}