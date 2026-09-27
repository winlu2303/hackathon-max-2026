package ru.domvporyadke.max;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class FSMService {

    private final StringRedisTemplate redis;

    private static final Duration SESSION_TTL = Duration.ofHours(24);
    private static final Duration PROFILE_TTL = Duration.ofDays(30);
    private static final Duration LAST_TTL = Duration.ofHours(2);

    public String getState(long chatId) {
        String state = redis.opsForValue().get("fsm:state:" + chatId);
        return state == null ? "NEW" : state;
    }

    public void setState(long chatId, String state) {
        redis.opsForValue().set("fsm:state:" + chatId, state, SESSION_TTL);
    }

    public void setTemp(long chatId, String key, String value) {
        redis.opsForHash().put("fsm:temp:" + chatId, key, value);
        redis.expire("fsm:temp:" + chatId, SESSION_TTL);
    }

    public String getTemp(long chatId, String key) {
        Object v = redis.opsForHash().get("fsm:temp:" + chatId, key);
        return v == null ? null : v.toString();
    }

    public boolean hasProfile(long chatId) {
        return Boolean.TRUE.equals(redis.hasKey("profile:" + chatId));
    }

    public void saveProfile(long chatId, String role, String address, Long buildingId, Long ukId) {
        Map<String, String> profile = Map.of(
            "role", role == null ? "" : role,
            "address", address == null ? "" : address,
            "buildingId", buildingId == null ? "" : buildingId.toString(),
            "ukId", ukId == null ? "" : ukId.toString()
        );
        redis.opsForHash().putAll("profile:" + chatId, profile);
        redis.expire("profile:" + chatId, PROFILE_TTL);
    }

    public String getProfile(long chatId, String key) {
        Object v = redis.opsForHash().get("profile:" + chatId, key);
        return v == null ? null : v.toString();
    }

    public void setLastRequest(long chatId, String type, Long id) {
        redis.opsForHash().put("fsm:last:" + chatId, type, String.valueOf(id));
        redis.expire("fsm:last:" + chatId, LAST_TTL);
    }

    public Long getLastRequest(long chatId, String type) {
        Object v = redis.opsForHash().get("fsm:last:" + chatId, type);
        return v == null ? null : Long.valueOf(v.toString());
    }

    public void clearState(long chatId) {
        redis.delete("fsm:state:" + chatId);
        redis.delete("fsm:temp:" + chatId);
    }

    public void clearAll(long chatId) {
        redis.delete("fsm:state:" + chatId);
        redis.delete("fsm:temp:" + chatId);
        redis.delete("fsm:last:" + chatId);
        redis.delete("profile:" + chatId);
    }
}