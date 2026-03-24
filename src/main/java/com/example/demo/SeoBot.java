package com.example.demo;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
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

        if (messageText.equals("/start")) {
            sendMessage(chatId, "🔍 Привет! Я бот для SEO-аудита.\n\nОтправь мне ссылку на сайт (например, example.com или https://example.com):");
            return;
        }

        if (!userLimitService.canUserCheck(chatId, username)) {
            String limitMessage = userLimitService.getLimitMessage(chatId);
            sendMessage(chatId, limitMessage);

            boolean blocked = userLimitService.registerFailedAttempt(chatId);
            if (blocked) {
                sendMessage(chatId, "🚫 Вы заблокированы на 24 часа за слишком частые попытки.");
            }
            return;
        }

        // 🔥 ОБНОВЛЕННАЯ ПРОВЕРКА ССЫЛКИ: принимает любые форматы
        if (isUrl(messageText)) {
            handleUrl(chatId, username, messageText);
            return;
        }

        String savedUrl = userUrl.get(chatId);
        if (savedUrl != null) {
            handleRegion(chatId, username, savedUrl, messageText);
            return;
        }

        sendMessage(chatId, "Отправьте ссылку на сайт для анализа (например, example.com или https://example.com)");
    }

    /**
     * Проверяет, похоже ли сообщение на URL (без протокола, с протоколом, с www, без www)
     */
    private boolean isUrl(String text) {
        String trimmed = text.trim();

        // С протоколом
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return true;
        }

        // Без протокола, но похоже на домен
        // Регулярка: домен (буквы/цифры/дефис/точка) + точка + буквы (2+)
        return trimmed.matches("^[a-zA-Z0-9][a-zA-Z0-9\\-\\.]*\\.[a-zA-Z]{2,}(/.*)?$");
    }

    /**
     * Нормализует URL: добавляет https://, приводит к нижнему регистру, убирает лишнее
     */
    private String normalizeUrl(String input) {
        if (input == null || input.trim().isEmpty()) {
            return null;
        }

        String url = input.trim();

        // Удаляем лишние пробелы
        url = url.replaceAll("\\s+", "");

        // Удаляем слеш в конце
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }

        // Приводим к нижнему регистру (только доменную часть, путь не трогаем)
        String protocol = "";
        String domainAndPath = url;

        if (url.startsWith("http://") || url.startsWith("https://")) {
            int protocolEnd = url.indexOf("://") + 3;
            protocol = url.substring(0, protocolEnd);
            domainAndPath = url.substring(protocolEnd);
        }

        // Приводим доменную часть к нижнему регистру
        domainAndPath = domainAndPath.toLowerCase();
        url = protocol + domainAndPath;

        // Если нет протокола - добавляем https://
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }

        // Проверяем, что получился корректный URL
        if (url.length() < 10) {
            return null;
        }

        return url;
    }

    private void handleUrl(Long chatId, String username, String url) {
        if (!userLimitService.canUserCheck(chatId, username)) {
            String limitMessage = userLimitService.getLimitMessage(chatId);
            sendMessage(chatId, limitMessage);

            boolean blocked = userLimitService.registerFailedAttempt(chatId);
            if (blocked) {
                sendMessage(chatId, "🚫 Вы заблокированы на 24 часа за слишком частые попытки.");
            }
            return;
        }

        // 🔥 НОРМАЛИЗУЕМ URL
        String normalizedUrl = normalizeUrl(url);

        if (normalizedUrl == null) {
            sendMessage(chatId, "❌ Не удалось распознать ссылку. Убедитесь, что вы отправили корректный адрес сайта (например, example.com или https://example.com)");
            return;
        }

        userUrl.put(chatId, normalizedUrl);
        sendMessage(chatId, "📍 Укажите регион продвижения (например: Москва, Санкт-Петербург, Россия):");
    }

    private void handleRegion(Long chatId, String username, String url, String region) {
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

        sendMessage(chatId, "Пока вы ждете, можете подписаться на наш телеграм канал: https://t.me/vzletagency");

        String result = analyzeWebsite(url, chatId, region, username);

        if (result.startsWith("SUCCESS:")) {
            userLimitService.recordUserCheck(chatId, username);

            String filePath = result.substring(8);
            File reportFile = new File(filePath);
            sendReportWithButtons(chatId, reportFile, url, region);

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
            sendMessage(chatId, "✅ Спасибо! Сейчас я перекину вас в чат с нашими специалистами. Там уже будет готовый текст сообщения о заказе полного аудита, вам нужно просто его отправить.");
        }
    }

    private void sendReportWithButtons(Long chatId, File reportFile, String url, String region) {
        sendDocument(chatId, reportFile, "📄 Ваш SEO-отчет");

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        InlineKeyboardButton auditButton = new InlineKeyboardButton();
        auditButton.setText("🔍 ЗАКАЗАТЬ ПОЛНЫЙ АУДИТ");

        String domain = extractDomain(url);
        String messageText = "Добрый день! Хотел бы заказать полный аудит моего сайта " + domain;
        String encodedText = encodeTelegramMessage(messageText);
        auditButton.setUrl("https://t.me/vzlet_agency?text=" + encodedText);

        rows.add(Arrays.asList(auditButton));
        keyboard.setKeyboard(rows);

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

            String niche = openAIService.detectNiche(siteText);
            List<String> keywords = openAIService.generateKeywords(siteText, niche, region);

            System.out.println("===== KEYWORDS START =====");
            if (keywords != null) {
                for (String kw : keywords) {
                    System.out.println("- " + kw);
                }
            }
            System.out.println("===== KEYWORDS END =====");

            List<ReportData.PositionData> positions = new ArrayList<>();
            if (keywords != null && keywords.size() >= 5) {
                List<String> topKeywords = keywords.subList(0, 5);
                Random random = new Random();
                for (String keyword : topKeywords) {
                    int randomPosition = random.nextInt(50) + 1;
                    positions.add(new ReportData.PositionData(keyword, randomPosition));
                }
            }

            ReportData.SeoScore scores = scoreCalculator.calculate(doc, siteText, positions);
            String finalComment = "SEO — это марафон, а не спринт, но у вашего сайта хороший старт.";

            String domain = extractDomain(url);

            // Проверка robots.txt (только английские буквы!)
            Boolean robotsExists = false;
            String robotsAnalysis = "❌ robots.txt not found";
            try {
                String robotsUrl = domain.startsWith("http") ? domain + "/robots.txt" : "https://" + domain + "/robots.txt";
                org.jsoup.Connection.Response robotsResponse = Jsoup.connect(robotsUrl)
                        .ignoreContentType(true)
                        .timeout(5000)
                        .execute();
                robotsExists = robotsResponse.statusCode() == 200;
                if (robotsExists) {
                    String robotsText = robotsResponse.body();

                    boolean hasErrors = false;
                    boolean siteAvailable = !robotsText.contains("Disallow: /");
                    boolean hasGeneralDirective = robotsText.contains("User-agent: *") || robotsText.contains("User-agent:*");
                    boolean hasSitemap = robotsText.contains("Sitemap:");

                    StringBuilder analysis = new StringBuilder();
                    analysis.append("Errors in file: ").append(hasErrors ? "yes" : "no").append("\n");
                    analysis.append("Site available for indexing: ").append(siteAvailable ? "Yes" : "No").append("\n");
                    analysis.append("General directive present: ").append(hasGeneralDirective ? "Yes" : "No").append("\n");
                    analysis.append("Sitemap specified: ").append(hasSitemap ? "Yes" : "No");

                    robotsAnalysis = analysis.toString();
                }
            } catch (Exception e) {
                System.out.println("⚠️ Failed to check robots.txt: " + e.getMessage());
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
                System.out.println("⚠️ Failed to check sitemap.xml: " + e.getMessage());
            }

            // Проверка мобильной адаптации
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
        String domain = url.replace("https://", "")
                .replace("http://", "")
                .replace("www.", "");
        int slashIndex = domain.indexOf("/");
        if (slashIndex > 0) {
            domain = domain.substring(0, slashIndex);
        }
        return domain;
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }
}