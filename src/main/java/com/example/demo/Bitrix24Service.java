package com.example.demo;

import org.springframework.stereotype.Component;

@Component
public class Bitrix24Service {

    public Bitrix24Service() {
        System.out.println("⚠️ Bitrix24Service работает в режиме заглушки");
    }

    public void createLead(String domain, Long chatId, String username, String firstName) {
        System.out.println("📝 [ЗАГЛУШКА] Создание лида:");
        System.out.println("  Сайт: " + domain);
        System.out.println("  ChatId: " + chatId);
        System.out.println("  Username: " + username);
        System.out.println("  Имя: " + firstName);
    }

    public boolean checkConnection() {
        return true;
    }
}