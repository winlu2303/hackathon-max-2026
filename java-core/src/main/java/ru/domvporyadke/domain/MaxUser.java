package ru.domvporyadke.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "max_users")
public class MaxUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_id", nullable = false, unique = true)
    private Long chatId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "phone")
    private String phone;

    @Column(name = "role")
    private String role;

    @Column(name = "address")
    private String address;

    @Column(name = "building_id")
    private Long buildingId;

    @Column(name = "uk_id")
    private Long ukId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "last_ping_at")
    private LocalDateTime lastPingAt;

    public MaxUser() {}

    public MaxUser(Long chatId) {
        this.chatId = chatId;
    }

    @PreUpdate
    public void preUpdate() { this.updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public Long getChatId() { return chatId; }
    public void setChatId(Long chatId) { this.chatId = chatId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public Long getBuildingId() { return buildingId; }
    public void setBuildingId(Long buildingId) { this.buildingId = buildingId; }
    public Long getUkId() { return ukId; }
    public void setUkId(Long ukId) { this.ukId = ukId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public LocalDateTime getLastPingAt() { return lastPingAt; }
    public void setLastPingAt(LocalDateTime lastPingAt) { this.lastPingAt = lastPingAt; }
}