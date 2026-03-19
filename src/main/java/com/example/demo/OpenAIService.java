package com.example.demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;

@Component
public class OpenAIService {

    private final String apiKey;
    private final WebClient webClient;

    public OpenAIService(
            @Value("${openai.api.key}") String apiKey) {  // Убрали proxyHost и proxyPort

        this.apiKey = apiKey;

        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(30));

        this.webClient = WebClient.builder()
                .clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(httpClient))
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();

        System.out.println("🚀 Инициализация OpenAI сервиса");
        System.out.println("API ключ: " + (apiKey != null ? apiKey.substring(0, 8) + "..." : "null"));
    }

    public String askOpenAI(String prompt) {
        try {
            System.out.println("📤 Отправляю запрос к OpenAI...");

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "gpt-3.5-turbo");
            requestBody.put("messages", List.of(
                    Map.of("role", "system", "content", "Ты профессиональный SEO-специалист и маркетолог. Отвечай на русском языке, строго следуя инструкциям по формату."),
                    Map.of("role", "user", "content", prompt)
            ));
            requestBody.put("max_tokens", 2500);
            requestBody.put("temperature", 0.7);

            Map response = webClient.post()
                    .uri("/chat/completions")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response != null) {
                if (response.containsKey("error")) {
                    Map error = (Map) response.get("error");
                    System.err.println("❌ Ошибка OpenAI: " + error.get("message"));
                    return null;
                }

                if (response.containsKey("choices")) {
                    List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                    if (!choices.isEmpty()) {
                        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                        String content = (String) message.get("content");
                        System.out.println("📥 Получен ответ от OpenAI (" + content.length() + " символов)");

                        if (response.containsKey("usage")) {
                            System.out.println("💰 Использовано токенов: " + response.get("usage"));
                        }

                        return content;
                    }
                }
            }

            return null;

        } catch (WebClientResponseException e) {
            System.err.println("❌ Ошибка HTTP: " + e.getStatusCode());
            System.err.println("Ответ: " + e.getResponseBodyAsString());

            if (e.getStatusCode().value() == 401) {
                System.err.println("❌ Ошибка авторизации. Проверьте API ключ OpenAI.");
            } else if (e.getStatusCode().value() == 429) {
                System.err.println("❌ Слишком много запросов или закончились средства.");
            }

            return null;

        } catch (Exception e) {
            System.err.println("❌ Ошибка: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public String analyzeSiteWithAI(String url, String siteContent, String siteType, String region,
                                    String title, String description) {
        String contentPreview = siteContent.length() > 4000 ? siteContent.substring(0, 4000) + "..." : siteContent;

        String prompt = "Ты профессиональный SEO-аналитик и маркетолог. Проанализируй сайт и напиши подробный SEO-отчет с анализом оффера.\n\n" +
                "URL сайта: " + url + "\n" +
                "Регион продвижения: " + region + "\n" +
                "Title страницы: " + title + "\n" +
                "Description страницы: " + description + "\n" +
                "Контент сайта:\n" + contentPreview + "\n\n" +

                "ОТВЕЧАЙ СТРОГО ПО СЛЕДУЮЩЕМУ ФОРМАТУ. ИСПОЛЬЗУЙ ТОЧНО ТАКИЕ ЖЕ ЗАГОЛОВКИ И РАЗДЕЛИТЕЛИ:\n\n" +

                "===ТЕМАТИКА===\n" +
                "[напиши тематику сайта одним предложением]\n\n" +

                "===СИТУАЦИЯ===\n" +
                "[напиши анализ ситуации на сайте одним абзацем]\n\n" +

                "===ОЦЕНКА TITLE===\n" +
                "[оцени качество title: соответствует ли содержанию, есть ли ключевые слова, длина, привлекательность для клика. Дай оценку от 1 до 10]\n\n" +

                "===ОЦЕНКА DESCRIPTION===\n" +
                "[оцени качество description: соответствует ли содержанию, есть ли ключевые слова, длина, призыв к действию. Дай оценку от 1 до 10]\n\n" +

                "===ОФФЕР===\n" +
                "[проанализируй коммерческую составляющую сайта - есть ли УТП, цены, гарантии, преимущества, насколько убедительно для клиента. Учти особенности ниши.]\n\n" +

                "===ПРИМЕРЫ ОФФЕРОВ===\n" +
                "- [пример оффера 1]\n" +
                "- [пример оффера 2]\n" +
                "- [пример оффера 3]\n\n" +

                "===ПРОБЛЕМЫ===\n" +
                "- [проблема 1]\n" +
                "- [проблема 2]\n" +
                "- [проблема 3]\n\n" +

                "===РЕКОМЕНДАЦИИ===\n" +
                "- [рекомендация 1]\n" +
                "- [рекомендация 2]\n" +
                "- [рекомендация 3]\n\n" +

                "===ПРОГНОЗ===\n" +
                "[напиши прогноз при внедрении рекомендаций одним абзацем]\n\n" +

                "ВАЖНО: Строго соблюдай этот формат! Каждый раздел должен начинаться с '===' и заканчиваться пустой строкой. Примеры офферов обязательно начинай с дефиса каждый с новой строки.";

        return askOpenAI(prompt);
    }

    public List<String> generateKeywords(String siteContent, String niche, String region) {
        String contentPreview = siteContent.length() > 2000 ? siteContent.substring(0, 2000) + "..." : siteContent;

        String prompt = "На основе контента сайта и его тематики '" + niche + "', составь список ключевых запросов для SEO-продвижения в регионе '" + region + "'.\n\n" +
                "Контент сайта:\n" + contentPreview + "\n\n" +

                "ОТВЕЧАЙ СТРОГО ПО СЛЕДУЮЩЕМУ ШАБЛОНУ, РАЗДЕЛЯЯ КАТЕГОРИИ ЗАГОЛОВКАМИ ===ВЧ===, ===СЧ===, ===НЧ===:\n\n" +
                "===ВЧ===\n" +
                "- [высокочастотный запрос 1]\n" +
                "- [высокочастотный запрос 2]\n" +
                "- [высокочастотный запрос 3]\n" +
                "- [высокочастотный запрос 4]\n" +
                "- [высокочастотный запрос 5]\n\n" +
                "===СЧ===\n" +
                "- [среднечастотный запрос 1]\n" +
                "- [среднечастотный запрос 2]\n" +
                "- [среднечастотный запрос 3]\n" +
                "- [среднечастотный запрос 4]\n" +
                "- [среднечастотный запрос 5]\n\n" +
                "===НЧ===\n" +
                "- [низкочастотный запрос 1]\n" +
                "- [низкочастотный запрос 2]\n" +
                "- [низкочастотный запрос 3]\n" +
                "- [низкочастотный запрос 4]\n" +
                "- [низкочастотный запрос 5]\n\n" +

                "ВАЖНО: Строго соблюдай этот формат! Каждый запрос должен начинаться с дефиса и пробела.";

        String response = askOpenAI(prompt);

        if (response == null) {
            return getDefaultKeywords(niche, region);
        }

        return parseKeywordsResponse(response);
    }

    public String analyzeTitle(String title) {
        String prompt = "Ты SEO-специалист. Проанализируй заголовок (title) страницы.\n\n" +
                "Title: " + title + "\n\n" +
                "Оцени по шкале от 1 до 10 и напиши краткий анализ (2-3 предложения).\n" +
                "Учитывай: длину, наличие ключевых слов, привлекательность для клика, соответствие содержанию.";

        return askOpenAI(prompt);
    }

    public String analyzeDescription(String description) {
        String prompt = "Ты SEO-специалист. Проанализируй мета-описание (description) страницы.\n\n" +
                "Description: " + description + "\n\n" +
                "Оцени по шкале от 1 до 10 и напиши краткий анализ (2-3 предложения).\n" +
                "Учитывай: длину, наличие ключевых слов, призыв к действию, информативность.";

        return askOpenAI(prompt);
    }

    public String detectNiche(String siteContent) {
        String contentPreview = siteContent.length() > 1500 ? siteContent.substring(0, 1500) : siteContent;

        String prompt = "Определи основную тематику (нишу) сайта по его контенту. Верни ТОЛЬКО ОДНО слово или короткую фразу на русском языке (например: шторы, ремонт квартир, мебель, окна, маркетинговое агентство, интернет-магазин одежды, услуги).\n\n" +
                "Контент сайта:\n" + contentPreview;

        String response = askOpenAI(prompt);

        if (response == null) {
            return extractNicheFromText(siteContent);
        }

        response = response.trim()
                .replaceAll("^[\\d\\.\\-\\*\\s]+", "")
                .replaceAll("\\n.*$", "")
                .toLowerCase();

        if (response.isEmpty() || response.length() > 50) {
            return extractNicheFromText(siteContent);
        }

        return response;
    }

    private List<String> parseKeywordsResponse(String response) {
        List<String> keywords = new ArrayList<>();
        String[] lines = response.split("\n");

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("-") || trimmed.startsWith("•")) {
                String kw = trimmed.substring(1).trim();
                kw = kw.replace("*", "").replace("\"", "").trim();
                if (!kw.isEmpty() && !kw.startsWith("[") && kw.length() > 3) {
                    keywords.add(kw);
                }
            }
        }

        return keywords;
    }

    private String extractNicheFromText(String text) {
        text = text.toLowerCase();
        if (text.contains("seo") || text.contains("продвижение сайтов") || text.contains("раскрутка") || text.contains("топ выдачи")) return "seo агентство";
        if (text.contains("маркетинг") || text.contains("реклама") || text.contains("таргет")) return "маркетинговое агентство";
        if (text.contains("штора") || text.contains("гардина")) return "шторы";
        if (text.contains("ремонт") || text.contains("отделка")) return "ремонт квартир";
        if (text.contains("мебель") || text.contains("шкаф") || text.contains("стол")) return "мебель";
        if (text.contains("строй") || text.contains("строитель")) return "строительство";
        if (text.contains("окна") || text.contains("остекление")) return "окна";
        if (text.contains("кухня") || text.contains("кухонный")) return "кухни на заказ";
        if (text.contains("дизайн") || text.contains("интерьер")) return "дизайн интерьера";
        return "услуги";
    }

    private List<String> getDefaultKeywords(String niche, String region) {
        String regionPrefix = region != null && !region.isEmpty() ? " " + region : " Москва";

        return switch (niche) {
            case "seo агентство", "маркетинговое агентство" -> List.of(
                    "SEO продвижение сайтов" + regionPrefix,
                    "Создание сайтов под ключ" + regionPrefix,
                    "Маркетинговое агентство" + regionPrefix,
                    "Таргетированная реклама" + regionPrefix,
                    "Контекстная реклама" + regionPrefix,
                    "Аудит сайта" + regionPrefix,
                    "SMM продвижение" + regionPrefix,
                    "Разработка лендинга" + regionPrefix,
                    "Поддержка сайтов" + regionPrefix,
                    "Раскрутка сайта" + regionPrefix,
                    "Реклама в интернете" + regionPrefix,
                    "Продвижение бизнеса" + regionPrefix,
                    "Комплексный маркетинг" + regionPrefix,
                    "SEO оптимизация" + regionPrefix,
                    "Продвижение в Яндексе" + regionPrefix
            );
            case "шторы" -> List.of(
                    "Купить шторы" + regionPrefix,
                    "Римские шторы" + regionPrefix,
                    "Рулонные шторы" + regionPrefix,
                    "Блэкаут шторы" + regionPrefix,
                    "Пошив штор на заказ" + regionPrefix,
                    "Дизайнер штор" + regionPrefix,
                    "Шторы для спальни" + regionPrefix,
                    "Шторы для гостиной" + regionPrefix,
                    "Шторы на кухню" + regionPrefix,
                    "Итальянские шторы" + regionPrefix,
                    "Ткани для штор" + regionPrefix,
                    "Карнизы для штор" + regionPrefix,
                    "Жалюзи" + regionPrefix,
                    "Рольшторы" + regionPrefix,
                    "Шторы с вышивкой" + regionPrefix
            );
            case "ремонт квартир" -> List.of(
                    "Ремонт квартир под ключ" + regionPrefix,
                    "Ремонт в новостройке" + regionPrefix,
                    "Косметический ремонт" + regionPrefix,
                    "Капитальный ремонт" + regionPrefix,
                    "Отделка квартир" + regionPrefix,
                    "Ремонт комнаты" + regionPrefix,
                    "Ремонт ванной комнаты" + regionPrefix,
                    "Ремонт кухни" + regionPrefix,
                    "Ремонт студии" + regionPrefix,
                    "Евроремонт" + regionPrefix,
                    "Ремонт офиса" + regionPrefix,
                    "Строительная бригада" + regionPrefix,
                    "Дизайн интерьера" + regionPrefix,
                    "Ремонт под ключ цены" + regionPrefix,
                    "Отделочные работы" + regionPrefix
            );
            default -> List.of(
                    "Услуги" + regionPrefix,
                    "Заказать услуги" + regionPrefix,
                    "Профессиональные услуги" + regionPrefix,
                    "Лучшие специалисты" + regionPrefix,
                    "Цены на услуги" + regionPrefix,
                    "Выезд специалиста" + regionPrefix,
                    "Бесплатная консультация" + regionPrefix,
                    "Бесплатный замер" + regionPrefix,
                    "Работа под ключ" + regionPrefix,
                    "Гарантия качества" + regionPrefix,
                    "Отзывы клиентов" + regionPrefix,
                    "Портфолио работ" + regionPrefix,
                    "Рассчитать стоимость" + regionPrefix,
                    "Оставить заявку" + regionPrefix,
                    "Онлайн консультация" + regionPrefix
            );
        };
    }
}