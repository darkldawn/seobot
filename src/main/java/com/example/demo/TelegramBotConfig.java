package com.example.demo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Configuration
public class TelegramBotConfig {

    @Bean
    public DefaultBotOptions defaultBotOptions() {
        DefaultBotOptions options = new DefaultBotOptions();

        // Если нужен прокси для Telegram API, раскомментируйте:
        // options.setProxyHost("127.0.0.1");
        // options.setProxyPort(10808);
        // options.setProxyType(DefaultBotOptions.ProxyType.SOCKS5);

        System.out.println("✅ DefaultBotOptions создан");
        return options;
    }

    @Bean
    public TelegramBotsApi telegramBotsApi(SeoBot seoBot) throws TelegramApiException {
        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
        botsApi.registerBot(seoBot);
        System.out.println("✅ Telegram Bot зарегистрирован и готов к работе!");
        return botsApi;
    }
}