package com.example.demo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Configuration
public class TelegramProxyConfig {

    @Bean
    public DefaultBotOptions defaultBotOptions() {
        DefaultBotOptions options = new DefaultBotOptions();

        // Настройка SOCKS5 прокси
        options.setProxyHost("127.0.0.1");
        options.setProxyPort(10808);
        options.setProxyType(DefaultBotOptions.ProxyType.SOCKS5);

        System.out.println("🔌 Настроен SOCKS5 прокси для Telegram бота: 127.0.0.1:10808");
        return options;
    }

    @Bean
    public TelegramBotsApi telegramBotsApi(SeoBot seoBot) throws TelegramApiException {
        TelegramBotsApi api = new TelegramBotsApi(DefaultBotSession.class);

        // Просто регистрируем бота - он уже создан с правильными опциями
        api.registerBot(seoBot);

        System.out.println("✅ Telegram бот зарегистрирован");
        return api;
    }
}