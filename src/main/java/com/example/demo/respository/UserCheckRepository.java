package com.example.demo.repository;

import com.example.demo.entity.UserCheck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserCheckRepository extends JpaRepository<UserCheck, Long> {
    Optional<UserCheck> findByChatId(Long chatId);
}