package com.example.demo.service;

import com.example.demo.entity.UserCheck;
import com.example.demo.repository.UserCheckRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
public class UserLimitService {

    private final UserCheckRepository userCheckRepository;
    private final Long ADMIN_USER_ID = 546908839L;

    // 🔥 НОВЫЕ КОНСТАНТЫ
    private final int MAX_CHECKS_PER_WEEK = 3;        // 3 проверки в неделю
    private final int WEEK_IN_DAYS = 7;                // 7 дней
    private final int MAX_FAILED_ATTEMPTS = 7;         // Максимальное число неудачных попыток
    private final int ATTEMPT_WINDOW_HOURS = 24;       // Окно для подсчета попыток: 24 часа
    private final int BLOCK_HOURS = 24;                 // Длительность блокировки: 24 часа

    public UserLimitService(UserCheckRepository userCheckRepository) {
        this.userCheckRepository = userCheckRepository;
    }

    /**
     * Проверяет, может ли пользователь сделать проверку
     * Возвращает true, если можно, false если нельзя
     */
    public boolean canUserCheck(Long chatId, String username) {
        // Администратор без ограничений
        if (chatId.equals(ADMIN_USER_ID) || "@darkldawn".equals(username)) {
            System.out.println("👑 Администратор " + username + " - без ограничений");
            return true;
        }

        Optional<UserCheck> userCheckOpt = userCheckRepository.findByChatId(chatId);

        // Если пользователь никогда не проверял - можно
        if (userCheckOpt.isEmpty()) {
            return true;
        }

        UserCheck userCheck = userCheckOpt.get();
        LocalDateTime now = LocalDateTime.now();

        // 🔥 ПРОВЕРКА НА БЛОКИРОВКУ
        if (userCheck.getBlockedUntil() != null && now.isBefore(userCheck.getBlockedUntil())) {
            System.out.println("🚫 Пользователь " + chatId + " заблокирован до " + userCheck.getBlockedUntil());
            return false;
        }

        // 🔥 НОВАЯ ЛОГИКА: 3 проверки в 7 дней
        // Если прошло больше 7 дней с последнего сброса - сбрасываем счетчик
        if (userCheck.getLastResetDate() == null) {
            userCheck.setLastResetDate(now);
            userCheck.setChecksCount(0);
        } else {
            long daysSinceReset = ChronoUnit.DAYS.between(userCheck.getLastResetDate(), now);
            if (daysSinceReset >= WEEK_IN_DAYS) {
                // Прошло 7 дней - сбрасываем счетчик
                userCheck.setLastResetDate(now);
                userCheck.setChecksCount(0);
                userCheckRepository.save(userCheck);
                System.out.println("🔄 Сброс счетчика для пользователя " + chatId);
            }
        }

        // Проверяем, не исчерпаны ли проверки за эту неделю
        return userCheck.getChecksCount() < MAX_CHECKS_PER_WEEK;
    }

    /**
     * Записывает успешную проверку пользователя
     */
    @Transactional
    public void recordUserCheck(Long chatId, String username) {
        // Администратора не записываем
        if (chatId.equals(ADMIN_USER_ID) || "@darkldawn".equals(username)) {
            return;
        }

        Optional<UserCheck> userCheckOpt = userCheckRepository.findByChatId(chatId);
        LocalDateTime now = LocalDateTime.now();

        if (userCheckOpt.isPresent()) {
            UserCheck userCheck = userCheckOpt.get();

            // 🔥 Проверяем, не пора ли сбросить счетчик
            if (userCheck.getLastResetDate() == null) {
                userCheck.setLastResetDate(now);
                userCheck.setChecksCount(1);
            } else {
                long daysSinceReset = ChronoUnit.DAYS.between(userCheck.getLastResetDate(), now);
                if (daysSinceReset >= WEEK_IN_DAYS) {
                    // Прошло 7 дней - сбрасываем и начинаем новую неделю
                    userCheck.setLastResetDate(now);
                    userCheck.setChecksCount(1);
                    System.out.println("🔄 Сброс счетчика для пользователя " + chatId);
                } else {
                    // В пределах текущей недели - увеличиваем счетчик
                    userCheck.setChecksCount(userCheck.getChecksCount() + 1);
                }
            }

            userCheck.setLastCheckDate(now);
            // Сбрасываем неудачные попытки после успеха
            userCheck.setFailedAttempts(0);
            userCheck.setFirstFailedAttempt(null);

            userCheckRepository.save(userCheck);
            System.out.println("📝 Обновлена проверка для пользователя " + chatId +
                    " (всего в этой неделе: " + userCheck.getChecksCount() +
                    ", сброс: " + userCheck.getLastResetDate());
        } else {
            // Новый пользователь
            UserCheck newUserCheck = new UserCheck(chatId, username);
            newUserCheck.setLastResetDate(now);
            newUserCheck.setChecksCount(1);
            userCheckRepository.save(newUserCheck);
            System.out.println("📝 Создана запись для нового пользователя " + chatId);
        }
    }

    /**
     * 🔥 Регистрирует неудачную попытку и проверяет, не пора ли блокировать
     */
    @Transactional
    public boolean registerFailedAttempt(Long chatId) {
        Optional<UserCheck> userCheckOpt = userCheckRepository.findByChatId(chatId);
        LocalDateTime now = LocalDateTime.now();

        UserCheck userCheck;
        if (userCheckOpt.isPresent()) {
            userCheck = userCheckOpt.get();
        } else {
            // Если пользователя еще нет в БД, создаем
            userCheck = new UserCheck(chatId, null);
        }

        // Проверяем, не истекло ли окно для подсчета попыток
        if (userCheck.getFirstFailedAttempt() == null) {
            // Первая неудачная попытка
            userCheck.setFirstFailedAttempt(now);
            userCheck.setFailedAttempts(1);
        } else {
            long hoursSinceFirst = ChronoUnit.HOURS.between(userCheck.getFirstFailedAttempt(), now);

            if (hoursSinceFirst < ATTEMPT_WINDOW_HOURS) {
                // В том же окне - увеличиваем счетчик
                userCheck.setFailedAttempts(userCheck.getFailedAttempts() + 1);
            } else {
                // Окно истекло - сбрасываем и начинаем заново
                userCheck.setFirstFailedAttempt(now);
                userCheck.setFailedAttempts(1);
            }
        }

        // Проверяем, не превышен ли лимит попыток
        if (userCheck.getFailedAttempts() >= MAX_FAILED_ATTEMPTS) {
            // Блокируем пользователя
            userCheck.setBlockedUntil(now.plusHours(BLOCK_HOURS));
            userCheck.setFailedAttempts(0);
            userCheck.setFirstFailedAttempt(null);
            userCheckRepository.save(userCheck);
            System.out.println("🚫 Пользователь " + chatId + " заблокирован до " + userCheck.getBlockedUntil());
            return true; // Заблокирован
        }

        userCheckRepository.save(userCheck);
        System.out.println("⚠️ Неудачная попытка #" + userCheck.getFailedAttempts() + " для пользователя " + chatId);
        return false; // Еще не заблокирован
    }

    /**
     * Получает сообщение об ограничении
     */
    public String getLimitMessage(Long chatId) {
        Optional<UserCheck> userCheckOpt = userCheckRepository.findByChatId(chatId);

        if (userCheckOpt.isEmpty()) {
            return "";
        }

        UserCheck userCheck = userCheckOpt.get();
        LocalDateTime now = LocalDateTime.now();

        // 🔥 ПРОВЕРКА НА БЛОКИРОВКУ
        if (userCheck.getBlockedUntil() != null && now.isBefore(userCheck.getBlockedUntil())) {
            long hoursLeft = ChronoUnit.HOURS.between(now, userCheck.getBlockedUntil());
            return String.format(
                    "🚫 Вы были заблокированы за слишком частые попытки.\n" +
                            "Блокировка снимутся через %d часов.\n" +
                            "Пожалуйста, не пытайтесь обойти ограничения.",
                    hoursLeft
            );
        }

        // 🔥 НОВОЕ СООБЩЕНИЕ ДЛЯ ЛИМИТА 3 В НЕДЕЛЮ
        int checksUsed = userCheck.getChecksCount() != null ? userCheck.getChecksCount() : 0;
        int checksLeft = MAX_CHECKS_PER_WEEK - checksUsed;

        if (userCheck.getLastResetDate() != null) {
            LocalDateTime nextReset = userCheck.getLastResetDate().plusDays(WEEK_IN_DAYS);
            String nextResetDate = nextReset.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
            long daysUntilReset = ChronoUnit.DAYS.between(now, nextReset);

            return String.format(
                    "⏳ У вас осталось %d из %d бесплатных проверок на этой неделе.\n" +
                            "Следующий сброс лимита: %s (через %d дней)\n\n" +
                            "👑 Хотите снять ограничения? Напишите @darkldawn\n\n" +
                            "⚠️ Внимание: после 7 неудачных попыток вы будете заблокированы на 24 часа!",
                    checksLeft, MAX_CHECKS_PER_WEEK, nextResetDate, daysUntilReset
            );
        }

        return "⏳ У вас осталось 3 из 3 бесплатных проверок на этой неделе.";
    }
}