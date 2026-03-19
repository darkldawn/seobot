package com.example.demo;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class PixerSeoService {

    private final WebClient webClient;

    public PixerSeoService() {
        this.webClient = WebClient.builder()
                .baseUrl("https://pixerseo.ru/api/v1")
                .build();

        System.out.println("🚀 Инициализация PixerSEO сервиса (бесплатный)");
    }

    /**
     * Проверяет позиции для списка ключевых слов
     */
    public List<ReportData.PositionData> checkPositions(List<String> keywords, String domain, String region) {
        List<ReportData.PositionData> results = new ArrayList<>();

        // Конвертируем регион в код для PixerSEO
        String regionCode = getRegionCode(region);

        for (String keyword : keywords) {
            int position = checkSinglePosition(keyword, domain, regionCode);
            results.add(new ReportData.PositionData(keyword, position));

            // Задержка между запросами (чтобы не превысить лимит)
            try {
                Thread.sleep(600); // ~100 запросов в минуту
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        return results;
    }

    /**
     * Проверяет позицию для одного ключевого слова
     */
    private int checkSinglePosition(String keyword, String domain, String regionCode) {
        try {
            String encodedKeyword = URLEncoder.encode(keyword, StandardCharsets.UTF_8);

            String url = String.format("/position?query=%s&region=%s",
                    encodedKeyword, regionCode);

            System.out.println("🔍 Проверяем позицию для: " + keyword);

            Map response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null) {
                return 0;
            }

            // Парсим ответ PixerSEO
            List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");
            if (results == null || results.isEmpty()) {
                return 0;
            }

            for (int i = 0; i < results.size(); i++) {
                Map<String, Object> result = results.get(i);
                String siteUrl = (String) result.get("url");

                if (siteUrl != null && siteUrl.contains(domain)) {
                    int position = i + 1;
                    System.out.println("✅ На позиции " + position + " для: " + keyword);
                    return position;
                }
            }

            System.out.println("❌ Не найден в топе для: " + keyword);
            return 0;

        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 429) {
                System.err.println("⚠️ Слишком много запросов. Лимит 100/день");
            } else {
                System.err.println("❌ Ошибка API: " + e.getMessage());
            }
            return 0;

        } catch (Exception e) {
            System.err.println("❌ Ошибка при проверке: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Получает информацию о доступных регионах
     */
    public List<String> getAvailableRegions() {
        try {
            Map response = webClient.get()
                    .uri("/regions")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null) return List.of("Москва");

            List<Map<String, Object>> regions = (List<Map<String, Object>>) response.get("regions");
            List<String> regionNames = new ArrayList<>();

            for (Map<String, Object> region : regions) {
                regionNames.add((String) region.get("name"));
            }

            return regionNames;

        } catch (Exception e) {
            System.err.println("❌ Ошибка получения регионов: " + e.getMessage());
            return List.of("Москва");
        }
    }

    /**
     * Преобразует название региона в код для PixerSEO
     */
    private String getRegionCode(String region) {
        return switch (region.toLowerCase()) {
            case "москва" -> "1";
            case "спб", "санкт-петербург" -> "2";
            case "россия" -> "225";
            default -> "1";
        };
    }

    /**
     * Проверяет остаток лимита
     */
    public int getRemainingLimit() {
        try {
            Map response = webClient.get()
                    .uri("/limits")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null) return 0;

            return (Integer) response.get("remaining");

        } catch (Exception e) {
            return 0;
        }
    }
}