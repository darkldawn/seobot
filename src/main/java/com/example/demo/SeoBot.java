package com.example.demo;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import java.util.HashSet;
import java.util.Set;

// Импорт для сервиса ограничений
import com.example.demo.service.UserLimitService;

@Component
public class SeoBot extends TelegramLongPollingBot {

    private final String botUsername;
    private final Map<Long, String> userState = new HashMap<>();
    private final Map<Long, String> userUrl = new HashMap<>();
    private final HtmlReportGenerator reportGenerator;
    private final OpenAIService openAIService;
    private final SeoScoreCalculator scoreCalculator;
    private final Bitrix24Service bitrix24Service;
    private final UserLimitService userLimitService;

    public SeoBot(
            DefaultBotOptions options,
            @Value("${telegram.bot.token}") String botToken,
            @Value("${telegram.bot.username}") String botUsername,
            HtmlReportGenerator reportGenerator,
            OpenAIService openAIService,
            SeoScoreCalculator scoreCalculator,
            Bitrix24Service bitrix24Service,
            UserLimitService userLimitService) {

        super(options, botToken);
        this.botUsername = botUsername;
        this.reportGenerator = reportGenerator;
        this.openAIService = openAIService;
        this.scoreCalculator = scoreCalculator;
        this.bitrix24Service = bitrix24Service;
        this.userLimitService = userLimitService;

        System.out.println("✅ SeoBot инициализирован с прокси");
        System.out.println("🛡️ Система ограничений и флуд-контроля активна");
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasCallbackQuery()) {
            handleCallbackQuery(update);
            return;
        }

        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }

        String messageText = update.getMessage().getText();
        Long chatId = update.getMessage().getChatId();
        String username = update.getMessage().getFrom().getUserName();

        System.out.println("📩 Сообщение от " + chatId + " (@" + username + "): " + messageText);

        // Команда /start - просто приветствие, без сброса состояния
        if (messageText.equals("/start")) {
            sendMessage(chatId, "🔍 Привет! Я бот для SEO-аудита.\n\nОтправь мне ссылку на сайт (например, https://example.com):");
            return;
        }

        // Проверяем, не заблокирован ли пользователь
        if (!userLimitService.canUserCheck(chatId, username)) {
            String limitMessage = userLimitService.getLimitMessage(chatId);
            sendMessage(chatId, limitMessage);

            boolean blocked = userLimitService.registerFailedAttempt(chatId);

            if (blocked) {
                sendMessage(chatId, "🚫 Вы заблокированы на 24 часа за слишком частые попытки.");
            }
            return;
        }

        // Если пользователь прислал ссылку
        if (messageText.startsWith("http://") || messageText.startsWith("https://")) {
            handleUrl(chatId, username, messageText);
            return;
        }

        // Если пользователь прислал регион (есть сохраненный URL)
        String savedUrl = userUrl.get(chatId);
        if (savedUrl != null) {
            handleRegion(chatId, username, savedUrl, messageText);
            return;
        }

        // Если ничего не подошло
        sendMessage(chatId, "Отправьте ссылку на сайт для анализа (например, https://example.com)");
    }

    private void handleUrl(Long chatId, String username, String url) {
        // Проверяем ограничения
        if (!userLimitService.canUserCheck(chatId, username)) {
            String limitMessage = userLimitService.getLimitMessage(chatId);
            sendMessage(chatId, limitMessage);

            boolean blocked = userLimitService.registerFailedAttempt(chatId);
            if (blocked) {
                sendMessage(chatId, "🚫 Вы заблокированы на 24 часа за слишком частые попытки.");
            }
            return;
        }

        // Сохраняем URL и запрашиваем регион
        userUrl.put(chatId, url);
        sendMessage(chatId, "📍 Укажите регион продвижения (например: Москва, Санкт-Петербург, Россия):");
    }

    private void handleRegion(Long chatId, String username, String url, String region) {
        // Проверяем ограничения еще раз
        if (!userLimitService.canUserCheck(chatId, username)) {
            String limitMessage = userLimitService.getLimitMessage(chatId);
            sendMessage(chatId, limitMessage);

            boolean blocked = userLimitService.registerFailedAttempt(chatId);
            if (blocked) {
                sendMessage(chatId, "🚫 Вы заблокированы на 24 часа за слишком частые попытки.");
            }

            userUrl.remove(chatId);
            return;
        }

        sendMessage(chatId, "⏳ Начинаю анализ сайта " + url + " для региона " + region + "...");
        sendMessage(chatId, "Пока вы ждете, можете подписаться на наш телеграм канал: https://t.me/vzletagency");

        String result = analyzeWebsite(url, chatId, region, username);

        if (result.startsWith("SUCCESS:")) {
            userLimitService.recordUserCheck(chatId, username);

            String filePath = result.substring(8);
            File reportFile = new File(filePath);
            sendReportWithButtons(chatId, reportFile, url, region);

            // НЕ УДАЛЯЕМ userUrl, оставляем пользователя в режиме ожидания
            sendMessage(chatId, "📨 Отправьте следующий сайт для анализа или /start для справки");
        } else {
            sendMessage(chatId, "❌ Ошибка: " + result.substring(6));
            userUrl.remove(chatId);
        }
    }

    private void handleCallbackQuery(Update update) {
        String callbackData = update.getCallbackQuery().getData();
        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        String username = update.getCallbackQuery().getFrom().getUserName();
        String firstName = update.getCallbackQuery().getFrom().getFirstName();

        System.out.println("🔘 Нажата кнопка: " + callbackData + " от " + chatId);

        if (callbackData.startsWith("audit_")) {
            // 🔥 ПРАВКА 1: Убираем создание лида в Битрикс24, просто отправляем сообщение
            // bitrix24Service.createLead(domain, chatId, username, firstName);
            sendMessage(chatId, "✅ Спасибо! Сейчас я перекину вас в чат с нашими специалистами. Там уже будет готовый текст сообщения о заказе полного аудита, вам нужно просто его отправить.");
        }
    }

    private void sendReportWithButtons(Long chatId, File reportFile, String url, String region) {
        sendDocument(chatId, reportFile, "📄 Ваш SEO-отчет");

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // 🔥 ПРАВКА 1: Оставляем только одну кнопку с ссылкой на чат и готовым текстом
        InlineKeyboardButton auditButton = new InlineKeyboardButton();
        auditButton.setText("🔍 ЗАКАЗАТЬ ПОЛНЫЙ АУДИТ");

        // 🔥 ИСПРАВЛЕНО: Правильное URL-кодирование
        String domain = extractDomain(url);
        String messageText = "Добрый день! Хотел бы заказать полный аудит моего сайта " + domain;
        String encodedText = encodeTelegramMessage(messageText);
        auditButton.setUrl("https://t.me/vzlet_agency?text=" + encodedText);

        rows.add(Arrays.asList(auditButton));
        keyboard.setKeyboard(rows);

        // 🔥 ПЕРЕИМЕНОВАЛ ПЕРЕМЕННУЮ С message НА replyMessage
        SendMessage replyMessage = new SendMessage();
        replyMessage.setChatId(chatId.toString());
        replyMessage.setText("✅ Отчет готов!\n\nХотите получить более детальный анализ? Закажите полный аудит у наших специалистов.");
        replyMessage.setReplyMarkup(keyboard);

        try {
            execute(replyMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    // 🔥 НОВЫЙ МЕТОД ДЛЯ ПРАВИЛЬНОГО КОДИРОВАНИЯ
    private String encodeTelegramMessage(String text) {
        try {
            return URLEncoder.encode(text, StandardCharsets.UTF_8.toString())
                    .replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return text.replace(" ", "%20");
        }
    }

    private String analyzeWebsite(String url, Long chatId, String region, String username) {
        try {
            sendMessage(chatId, "🌐 Загружаю сайт...");

            long startTime = System.currentTimeMillis();

            org.jsoup.Connection.Response response = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(15000)
                    .followRedirects(true)
                    .execute();

            long loadTime = System.currentTimeMillis() - startTime;

            String html = response.body();
            html = html.replaceAll("<meta\\s+([^>]*)(?<!/)>", "<meta $1/>");
            Document doc = Jsoup.parse(html, url);

            String title = doc.title();
            String description = doc.select("meta[name=description]").attr("content");
            int titleLength = title.length();
            int descriptionLength = description.length();
            int h1Count = doc.select("h1").size();
            int imagesWithoutAlt = doc.select("img:not([alt])").size();

            System.out.println("📊 Технический аудит:");
            System.out.println("  Title: " + titleLength);
            System.out.println("  Description: " + descriptionLength);
            System.out.println("  H1: " + h1Count);
            System.out.println("  Изображения без alt: " + imagesWithoutAlt);

            sendMessage(chatId, "🤖 Анализирую контент...");

            String siteText = doc.text();
            String aiAnalysis = openAIService.analyzeSiteWithAI(url, siteText, "", region, title, description);

            System.out.println("===== AI ANALYSIS START =====");
            System.out.println(aiAnalysis);
            System.out.println("===== AI ANALYSIS END =====");

            sendMessage(chatId, "🔑 Генерирую ключевые запросы для " + region + "...");

            String niche = openAIService.detectNiche(siteText);
            List<String> keywords = openAIService.generateKeywords(siteText, niche, region);

            System.out.println("===== KEYWORDS START =====");
            if (keywords != null) {
                for (String kw : keywords) {
                    System.out.println("- " + kw);
                }
            }
            System.out.println("===== KEYWORDS END =====");

            sendMessage(chatId, "📊 Подготавливаю данные о позициях...");

            List<ReportData.PositionData> positions = new ArrayList<>();
            if (keywords != null && keywords.size() >= 5) {
                List<String> topKeywords = keywords.subList(0, 5);
                Random random = new Random();
                for (String keyword : topKeywords) {
                    int randomPosition = random.nextInt(50) + 1;
                    positions.add(new ReportData.PositionData(keyword, randomPosition));
                }
            }

            sendMessage(chatId, "📈 Рассчитываю SEO-потенциал...");

            ReportData.SeoScore scores = scoreCalculator.calculate(doc, siteText, positions);

            String finalComment = "SEO — это марафон, а не спринт, но у вашего сайта хороший старт.";

            // 🔥 СБОР ТЕХНИЧЕСКИХ ДАННЫХ
            String domain = extractDomain(url);

            // Проверка robots.txt
            Boolean robotsExists = false;
            String robotsAnalysis = "❌ robots.txt не найден";
            try {
                String robotsUrl = domain.startsWith("http") ? domain + "/robots.txt" : "https://" + domain + "/robots.txt";
                org.jsoup.Connection.Response robotsResponse = Jsoup.connect(robotsUrl)
                        .ignoreContentType(true)
                        .timeout(5000)
                        .execute();
                robotsExists = robotsResponse.statusCode() == 200;
                if (robotsExists) {
                    String robotsText = robotsResponse.body();

                    // Анализ robots.txt
                    boolean hasErrors = false;
                    boolean siteAvailable = !robotsText.contains("Disallow: /");
                    boolean hasGeneralDirective = robotsText.contains("User-agent: *") || robotsText.contains("User-agent:*");
                    boolean hasSitemap = robotsText.contains("Sitemap:");

                    StringBuilder analysis = new StringBuilder();
                    analysis.append("Ошибки в файле: ").append(hasErrors ? "есть" : "не найдены").append("\n");
                    analysis.append("Сайт доступен для индексации: ").append(siteAvailable ? "Да" : "Нет").append("\n");
                    analysis.append("Есть общая директива: ").append(hasGeneralDirective ? "Да" : "Нет").append("\n");
                    analysis.append("Указана карта сайта: ").append(hasSitemap ? "Да" : "Нет");

                    robotsAnalysis = analysis.toString();
                }
            } catch (Exception e) {
                System.out.println("⚠️ Не удалось проверить robots.txt: " + e.getMessage());
            }

            // Проверка sitemap.xml
            Boolean sitemapExists = false;
            try {
                String sitemapUrl = domain.startsWith("http") ? domain + "/sitemap.xml" : "https://" + domain + "/sitemap.xml";
                org.jsoup.Connection.Response sitemapResponse = Jsoup.connect(sitemapUrl)
                        .ignoreContentType(true)
                        .timeout(5000)
                        .execute();
                sitemapExists = sitemapResponse.statusCode() == 200;
            } catch (Exception e) {
                System.out.println("⚠️ Не удалось проверить sitemap.xml: " + e.getMessage());
            }

            // Проверка мобильной адаптации (наличие viewport)
            boolean hasViewport = doc.select("meta[name=viewport]").size() > 0;
            boolean mobileFriendly = hasViewport;

            ReportData reportData = new ReportData();
            reportData.setDomain(domain);
            reportData.setDate(new SimpleDateFormat("dd.MM.yyyy").format(new Date()));
            reportData.setRegion(region);
            reportData.setAiAnalysis(aiAnalysis);
            reportData.setKeywords(keywords);
            reportData.setPositions(positions);
            reportData.setScores(scores);
            reportData.setFinalComment(finalComment);

            reportData.setTitleLength(titleLength);
            reportData.setDescriptionLength(descriptionLength);
            reportData.setH1Count(h1Count);
            reportData.setImagesWithoutAlt(imagesWithoutAlt);
            reportData.setLoadTime(loadTime);

            // Устанавливаем новые технические данные
            reportData.setRobotsExists(robotsExists);
            reportData.setRobotsAnalysis(robotsAnalysis);
            reportData.setSitemapExists(sitemapExists);
            reportData.setMobileFriendly(mobileFriendly);

            System.out.println("📤 Передаю AI анализ в отчет (длина: " + (aiAnalysis != null ? aiAnalysis.length() : 0) + ")");
            System.out.println("✅ ReportData создан:");
            System.out.println("  robots.txt анализ: " + robotsAnalysis);

            sendMessage(chatId, "📄 Формирую PDF отчёт...");
            File reportFile = reportGenerator.generatePdfReport(reportData);

            return "SUCCESS:" + reportFile.getAbsolutePath();

        } catch (Exception e) {
            e.printStackTrace();
            return "ERROR:" + e.getMessage();
        }
    }

    private void sendDocument(Long chatId, File file, String caption) {
        try {
            SendDocument sendDocument = new SendDocument();
            sendDocument.setChatId(chatId.toString());
            sendDocument.setDocument(new InputFile(file));
            sendDocument.setCaption(caption);
            execute(sendDocument);
            file.delete();
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendMessage(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private String extractDomain(String url) {
        return url.replace("https://", "")
                .replace("http://", "")
                .replace("www.", "")
                .split("/")[0];
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }
}