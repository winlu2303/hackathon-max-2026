package ru.domvporyadke.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "buildings")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Building {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private String address;
    @Column(name = "fias_id", unique = true) private String fiasId;
    @Column(name = "uk_name") private String ukName;
    @Column(name = "max_chat_id") private Long maxChatId;
    @Column(name = "created_at") private Instant createdAt;
}