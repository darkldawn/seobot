package com.example.demo.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_checks")
public class UserCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_id", nullable = false, unique = true)
    private Long chatId;

    @Column(name = "username")
    private String username;

    @Column(name = "last_check_date")
    private LocalDateTime lastCheckDate;

    @Column(name = "checks_count")
    private Integer checksCount = 0;

    // 🔥 НОВОЕ ПОЛЕ ДЛЯ ОТСЛЕЖИВАНИЯ НЕДЕЛЬНОГО ЛИМИТА
    @Column(name = "last_reset_date")
    private LocalDateTime lastResetDate;  // Дата последнего сброса счетчика

    @Column(name = "failed_attempts")
    private Integer failedAttempts = 0;

    @Column(name = "first_failed_attempt")
    private LocalDateTime firstFailedAttempt;

    @Column(name = "blocked_until")
    private LocalDateTime blockedUntil;

    // Конструкторы
    public UserCheck() {}

    public UserCheck(Long chatId, String username) {
        this.chatId = chatId;
        this.username = username;
        this.lastCheckDate = LocalDateTime.now();
        this.checksCount = 1;
        this.failedAttempts = 0;
    }

    // Геттеры и сеттеры
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getChatId() { return chatId; }
    public void setChatId(Long chatId) { this.chatId = chatId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public LocalDateTime getLastCheckDate() { return lastCheckDate; }
    public void setLastCheckDate(LocalDateTime lastCheckDate) { this.lastCheckDate = lastCheckDate; }

    public Integer getChecksCount() { return checksCount; }
    public void setChecksCount(Integer checksCount) { this.checksCount = checksCount; }

    // 🔥 НОВЫЙ ГЕТТЕР И СЕТТЕР
    public LocalDateTime getLastResetDate() { return lastResetDate; }
    public void setLastResetDate(LocalDateTime lastResetDate) { this.lastResetDate = lastResetDate; }

    public Integer getFailedAttempts() { return failedAttempts; }
    public void setFailedAttempts(Integer failedAttempts) { this.failedAttempts = failedAttempts; }

    public LocalDateTime getFirstFailedAttempt() { return firstFailedAttempt; }
    public void setFirstFailedAttempt(LocalDateTime firstFailedAttempt) { this.firstFailedAttempt = firstFailedAttempt; }

    public LocalDateTime getBlockedUntil() { return blockedUntil; }
    public void setBlockedUntil(LocalDateTime blockedUntil) { this.blockedUntil = blockedUntil; }
}