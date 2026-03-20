package com.example.demo;

import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

@Component
public class HtmlReportGenerator {

    public File generatePdfReport(ReportData data) throws IOException {
        String domain = data.getDomain();

        File htmlFile = File.createTempFile("report_" + domain, ".html");
        File pdfFile = File.createTempFile("SEO_Report_" + domain, ".pdf");

        String htmlContent = generateHtml(data);
        // Принудительно пишем в UTF-8
        Files.write(htmlFile.toPath(), htmlContent.getBytes("UTF-8"));

        try (OutputStream os = new FileOutputStream(pdfFile)) {
            PdfRendererBuilder builder = new PdfRendererBuilder();

            // Загружаем шрифт из ресурсов
            java.io.InputStream fontStream = getClass().getResourceAsStream("src/main/resources/fonts/DejaVuSans.ttf");
            if (fontStream != null) {
                builder.useFont(() -> fontStream, "DejaVuSans");
                System.out.println("✅ Шрифт DejaVuSans загружен из ресурсов");
            } else {
                System.out.println("⚠️ Шрифт не найден в ресурсах, будет использован шрифт по умолчанию");
            }

            // Указываем UTF-8
            builder.withFile(htmlFile);
            builder.toStream(os);
            builder.run();
        }

        htmlFile.delete();
        return pdfFile;
    }

    private String generateHtml(ReportData data) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html>\n");
        html.append("<head>\n");
        html.append("<meta charset=\"UTF-8\"/>\n");
        html.append("<title>SEO Отчет по сайту ").append(data.getDomain()).append("</title>\n");
        html.append("<style>\n");
        html.append("body { font-family: 'DejaVuSans', Arial, sans-serif; margin: 40px; background: white;}");
        html.append("h1 { color: #000000; font-size: 32px; margin-bottom: 5px; }\n");
        html.append("h2 { color: #000000; font-size: 24px; margin-top: 30px; margin-bottom: 15px; border-bottom: 2px solid #FFC107; padding-bottom: 5px; }\n");
        html.append("h3 { color: #000000; font-size: 20px; margin-top: 25px; margin-bottom: 10px; }\n");
        html.append("table { width: 100%; border-collapse: collapse; margin: 15px 0; }\n");
        html.append("th { background: #f0f0f0; padding: 10px; border: 1px solid #ddd; text-align: left; }\n");
        html.append("td { padding: 10px; border: 1px solid #ddd; }\n");
        html.append(".offer-box { background-color: #f9f9f9; border-left: 4px solid #FFC107; padding: 15px; margin: 15px 0; }\n");
        html.append(".numbered-list { list-style-type: decimal; padding-left: 30px; margin: 10px 0; }\n");
        html.append(".numbered-list li { margin-bottom: 8px; line-height: 1.4; }\n");
        html.append(".bullet-list { list-style-type: disc; padding-left: 20px; margin: 10px 0; }\n");
        html.append(".bullet-list li { margin-bottom: 8px; line-height: 1.4; }\n");
        html.append(".success { color: #4CAF50; font-weight: bold; }\n");
        html.append(".warning { color: #FFC107; font-weight: bold; }\n");
        html.append(".error { color: #F44336; font-weight: bold; }\n");
        html.append(".info { color: #2196F3; font-weight: bold; }\n");
        html.append(".duplicate-item { background-color: #ffebee; padding: 8px; margin: 5px 0; border-left: 4px solid #F44336; }\n");
        html.append(".analysis-pre { background-color: #f5f5f5; padding: 10px; border-radius: 5px; white-space: pre-wrap; font-family: Arial, sans-serif; }\n");
        html.append("</style>\n");
        html.append("</head>\n");
        html.append("<body>\n");

        // 1. ШАПКА
        html.append("<h1>").append(data.getDomain()).append("</h1>\n");
        html.append("<p>").append(data.getDate()).append("</p>\n");
        html.append("<p>").append(data.getRegion()).append("</p>\n");

        // 2. КРАТКАЯ ОЦЕНКА
        if (data.getScores() != null) {
            html.append("<h2>Краткая оценка сайта:</h2>\n");
            html.append("<p><b>SEO-потенциал:</b> ").append(data.getScores().getTotal()).append(" / 10</p>\n");
            html.append("<p>Сейчас сайт использует примерно <b>").append(data.getScores().getUsagePercent()).append("%</b> своего SEO-потенциала.</p>\n");
            html.append("<table>\n");
            html.append("<tr><td>Техническое состояние</td><td>").append(data.getScores().getTech()).append("/10</td></tr>\n");
            html.append("<tr><td>Контент</td><td>").append(data.getScores().getContent()).append("/10</td></tr>\n");
            html.append("<tr><td>Индексация</td><td>").append(data.getScores().getIndex()).append("/10</td></tr>\n");
            html.append("<tr><td>Коммерческий фактор</td><td>").append(data.getScores().getCommercial()).append("/10</td></tr>\n");
            html.append("</table>\n");
        }

        // Получаем текст AI анализа
        String aiText = data.getAiAnalysis();

        // Разбиваем на секции по маркерам ===
        Map<String, String> sections = parseSections(aiText);

        // 3. ОСНОВНАЯ ИНФОРМАЦИЯ (Тематика)
        html.append("<h2>Основная информация (тематика, ниша):</h2>\n");
        String mainInfo = sections.get("ТЕМАТИКА");
        if (mainInfo == null) mainInfo = sections.get("ТЕМАТИКА:");
        if (mainInfo == null) mainInfo = "Тематика сайта не определена автоматически. Для точного анализа рекомендуем заказать полный SEO аудит.";
        html.append("<p>").append(mainInfo.replace("\n", " ")).append("</p>\n");

        // 4. СИТУАЦИЯ
        html.append("<h2>Ситуация с сайтом на данный момент:</h2>\n");
        String situation = sections.get("СИТУАЦИЯ");
        if (situation == null) situation = sections.get("СИТУАЦИЯ:");
        if (situation == null) situation = "Для детального анализа ситуации рекомендуем заказать полный SEO аудит.";
        html.append("<p>").append(situation.replace("\n", " ")).append("</p>\n");

        // 5. АНАЛИЗ ОФФЕРА
        html.append("<h2>Анализ оффера</h2>\n");
        String offer = sections.get("ОФФЕР");
        if (offer == null) offer = sections.get("ОФФЕР:");
        if (offer == null) {
            offer = "На сайте присутствуют базовые коммерческие элементы. Для усиления оффера рекомендуем добавить: уникальное торговое предложение, гарантии, цены и преимущества перед конкурентами.";
        }
        html.append("<div class=\"offer-box\">");
        html.append("<p>").append(offer.replace("\n", " ")).append("</p>\n");
        html.append("</div>");

        // 6. ПРИМЕРЫ СИЛЬНЫХ ОФФЕРОВ
        html.append("<h2>Примеры офферов для вашей ниши</h2>\n");
        String examplesText = sections.get("ПРИМЕРЫ ОФФЕРОВ");
        if (examplesText == null) examplesText = sections.get("ПРИМЕРЫ ОФФЕРОВ:");

        if (examplesText != null && !examplesText.trim().isEmpty()) {
            String[] lines = examplesText.split("\n");
            List<String> items = new ArrayList<>();

            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("-") || trimmed.startsWith("•")) {
                    String item = trimmed.substring(1).trim();
                    if (!item.isEmpty()) {
                        items.add(item);
                    }
                } else if (!trimmed.isEmpty() && !trimmed.startsWith("===")) {
                    items.add(trimmed);
                }
            }

            html.append("<ol class=\"numbered-list\">\n");
            for (String item : items) {
                html.append("<li>").append(item).append("</li>\n");
            }
            html.append("</ol>\n");
        } else {
            html.append("<ol class=\"numbered-list\">\n");
            html.append("<li>Гарантированный результат в виде вывода запросов в ТОП-10 по Яндексу и Google</li>\n");
            html.append("<li>Бесплатный тестовый период для оценки эффективности работы</li>\n");
            html.append("<li>Система финансовых гарантий: оплата только за достигнутые результаты</li>\n");
            html.append("</ol>\n");
        }

        // 7. ТЕХНИЧЕСКИЙ АУДИТ (РАСШИРЕННЫЙ)
        html.append("<h2>Технический аудит</h2>\n");

        // Основные метрики
        html.append("<table>\n");
        html.append("<tr><th>Параметр</th><th>Оценка</th></tr>\n");
        html.append("<tr><td>Title</td><td>").append(data.getTitleLength()).append(" символов</td></tr>\n");
        html.append("<tr><td>Description</td><td>").append(data.getDescriptionLength()).append(" символов</td></tr>\n");
        html.append("<tr><td>H1</td><td>").append(data.getH1Count()).append(" шт.</td></tr>\n");
        html.append("<tr><td>Изображения без alt</td><td>").append(data.getImagesWithoutAlt()).append(" шт.</td></tr>\n");
        html.append("<tr><td>Скорость загрузки</td><td>").append(String.format("%.1f", data.getLoadTime()/1000.0)).append(" с</td></tr>\n");
        html.append("</table>\n");

        // Оценка Title от AI
        String titleEval = sections.get("ОЦЕНКА TITLE");
        if (titleEval != null && !titleEval.trim().isEmpty()) {
            html.append("<h3>Анализ Title</h3>\n");
            html.append("<p>").append(titleEval.replace("\n", " ")).append("</p>\n");
        }

        // Оценка Description от AI
        String descEval = sections.get("ОЦЕНКА DESCRIPTION");
        if (descEval != null && !descEval.trim().isEmpty()) {
            html.append("<h3>Анализ Description</h3>\n");
            html.append("<p>").append(descEval.replace("\n", " ")).append("</p>\n");
        }

        // Проверка robots.txt
        html.append("<h3>robots.txt</h3>\n");
        if (data.getRobotsAnalysis() != null && !data.getRobotsAnalysis().isEmpty()) {
            html.append("<div class=\"analysis-pre\">").append(data.getRobotsAnalysis().replace("\n", "<br/>")).append("</div>\n");
        } else if (data.getRobotsExists() != null && data.getRobotsExists()) {
            html.append("<p class=\"success\">✅ robots.txt найден</p>\n");
        } else {
            html.append("<p class=\"error\">❌ robots.txt отсутствует</p>\n");
        }


        // Мобильная адаптация
        html.append("<h3>Мобильная адаптация</h3>\n");
        if (data.getMobileFriendly() != null && data.getMobileFriendly()) {
            html.append("<p class=\"success\"> Сайт адаптирован для мобильных устройств</p>\n");
        } else {
            html.append("<p class=\"error\"> Сайт не оптимизирован для мобильных</p>\n");
        }

        // Дубли метатегов
        if (data.getDuplicateTitles() != null && !data.getDuplicateTitles().isEmpty()) {
            html.append("<h3>Дубликаты Title</h3>\n");
            for (Map.Entry<String, List<String>> entry : data.getDuplicateTitles().entrySet()) {
                html.append("<div class=\"duplicate-item\">\n");
                html.append("<p><b>Title:</b> ").append(entry.getKey()).append("</p>\n");
                html.append("<p><b>Страницы:</b> ").append(String.join(", ", entry.getValue())).append("</p>\n");
                html.append("</div>\n");
            }
        }

        if (data.getDuplicateDescriptions() != null && !data.getDuplicateDescriptions().isEmpty()) {
            html.append("<h3>Дубликаты Description</h3>\n");
            for (Map.Entry<String, List<String>> entry : data.getDuplicateDescriptions().entrySet()) {
                html.append("<div class=\"duplicate-item\">\n");
                html.append("<p><b>Description:</b> ").append(entry.getKey()).append("</p>\n");
                html.append("<p><b>Страницы:</b> ").append(String.join(", ", entry.getValue())).append("</p>\n");
                html.append("</div>\n");
            }
        }

        // 8. КЛЮЧЕВЫЕ ЗАПРОСЫ
        html.append("<h2>Ключевые запросы</h2>\n");
        html.append(generateKeywordsTable(data.getKeywords()));

        // 9. ПРОБЛЕМЫ
        html.append("<h2>Проблемы</h2>\n");
        String problemsText = sections.get("ПРОБЛЕМЫ");
        if (problemsText == null) problemsText = sections.get("ПРОБЛЕМЫ:");

        if (problemsText != null && !problemsText.trim().isEmpty()) {
            String[] lines = problemsText.split("\n");
            List<String> items = new ArrayList<>();

            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("-") || trimmed.startsWith("•")) {
                    String item = trimmed.substring(1).trim();
                    if (!item.isEmpty()) {
                        items.add(item);
                    }
                } else if (!trimmed.isEmpty() && !trimmed.startsWith("===")) {
                    items.add(trimmed);
                }
            }

            html.append("<ul class=\"bullet-list\">\n");
            for (String item : items) {
                html.append("<li>").append(item).append("</li>\n");
            }
            html.append("</ul>\n");
        } else {
            html.append("<ul class=\"bullet-list\">\n");
            html.append("<li>Для выявления всех проблем рекомендуем заказать полный SEO аудит</li>\n");
            html.append("</ul>\n");
        }

        // 10. РЕКОМЕНДАЦИИ
        html.append("<h2>Рекомендации</h2>\n");
        String recommendationsText = sections.get("РЕКОМЕНДАЦИИ");
        if (recommendationsText == null) recommendationsText = sections.get("РЕКОМЕНДАЦИИ:");

        if (recommendationsText != null && !recommendationsText.trim().isEmpty()) {
            String[] lines = recommendationsText.split("\n");
            List<String> items = new ArrayList<>();

            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("-") || trimmed.startsWith("•")) {
                    String item = trimmed.substring(1).trim();
                    if (!item.isEmpty()) {
                        items.add(item);
                    }
                } else if (!trimmed.isEmpty() && !trimmed.startsWith("===")) {
                    items.add(trimmed);
                }
            }

            html.append("<ul class=\"bullet-list\">\n");
            for (String item : items) {
                html.append("<li>").append(item).append("</li>\n");
            }
            html.append("</ul>\n");
        } else {
            html.append("<ul class=\"bullet-list\">\n");
            html.append("<li>Заказать полный SEO аудит для получения индивидуальных рекомендаций</li>\n");
            html.append("</ul>\n");
        }

        // 11. ПРОГНОЗ
        html.append("<h2>Прогноз</h2>\n");
        String forecast = sections.get("ПРОГНОЗ");
        if (forecast == null) forecast = sections.get("ПРОГНОЗ:");
        if (forecast == null) forecast = "Точный прогноз возможен после проведения полного SEO аудита.";
        html.append("<p>").append(forecast.replace("\n", " ")).append("</p>\n");

        // 12. ВЫВОД
        html.append("<h2>Небольшой вывод</h2>\n");
        html.append("<p>SEO — это <b>марафон, а не спринт</b>, но у вашего сайта хороший старт.</p>\n");

        // 13. ЧТО ДАЛЬШЕ
        html.append("<h2>Что делать дальше</h2>\n");
        html.append("<p>Если хотите получить:</p>\n");
        html.append("<ul class=\"bullet-list\">\n");
        html.append("<li>полный SEO аудит</li>\n");
        html.append("<li>стратегию продвижения</li>\n");
        html.append("<li>прогноз трафика</li>\n");
        html.append("<li>план роста позиций</li>\n");
        html.append("</ul>\n");
        html.append("<p>мы можем подготовить <b>расширенный отчёт по вашему сайту</b>.</p>\n");
        html.append("<p> Нажмите на кнопку <b>\"Заказать полный аудит\"</b> или напишите нам в телеграм.</p>\n");

        html.append("</body>\n</html>");
        return html.toString();
    }

    // ==================== МЕТОД ПАРСИНГА ====================

    private Map<String, String> parseSections(String text) {
        Map<String, String> sections = new HashMap<>();
        if (text == null || text.isEmpty()) return sections;

        String[] lines = text.split("\n");
        String currentSection = null;
        StringBuilder content = new StringBuilder();

        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.startsWith("===") && trimmed.endsWith("===")) {
                if (currentSection != null) {
                    sections.put(currentSection, content.toString().trim());
                }
                currentSection = trimmed.substring(3, trimmed.length() - 3).trim();
                content = new StringBuilder();
            }
            else if (currentSection != null && !trimmed.isEmpty()) {
                if (content.length() > 0) {
                    content.append("\n");
                }
                content.append(trimmed);
            }
        }

        if (currentSection != null) {
            sections.put(currentSection, content.toString().trim());
        }

        return sections;
    }

    private String generateKeywordsTable(List<String> keywords) {
        StringBuilder table = new StringBuilder();
        table.append("<table>\n");
        table.append("<tr><th>ВЧ</th><th>СЧ</th><th>НЧ</th></tr>\n");

        String[] defaultVch = {
                "SEO продвижение сайтов",
                "Создание сайтов под ключ",
                "Маркетинговое агентство",
                "Таргетированная реклама",
                "Контекстная реклама"
        };

        String[] defaultSch = {
                "Аудит сайта",
                "SMM продвижение",
                "Разработка лендинга",
                "Поддержка сайтов",
                "Раскрутка сайта"
        };

        String[] defaultNch = {
                "Реклама в интернете",
                "Продвижение бизнеса",
                "Комплексный маркетинг",
                "SEO оптимизация",
                "Продвижение в Яндексе"
        };

        List<String> vch = new ArrayList<>();
        List<String> sch = new ArrayList<>();
        List<String> nch = new ArrayList<>();

        if (keywords != null && !keywords.isEmpty()) {
            List<String> cleanKeywords = new ArrayList<>();
            for (String kw : keywords) {
                String clean = kw.replace("*", "").replace("-", "").replace("•", "").replace("—", "").trim();
                if (!clean.isEmpty() && !clean.startsWith("Высокочастотные") &&
                        !clean.startsWith("Среднечастотные") && !clean.startsWith("Низкочастотные") &&
                        !clean.startsWith("ВЧ") && !clean.startsWith("СЧ") && !clean.startsWith("НЧ") &&
                        clean.length() > 3) {
                    cleanKeywords.add(clean);
                }
            }

            for (int i = 0; i < cleanKeywords.size() && i < 15; i++) {
                if (i < 5) vch.add(cleanKeywords.get(i));
                else if (i < 10) sch.add(cleanKeywords.get(i));
                else nch.add(cleanKeywords.get(i));
            }
        }

        while (vch.size() < 5) vch.add(defaultVch[vch.size()]);
        while (sch.size() < 5) sch.add(defaultSch[sch.size()]);
        while (nch.size() < 5) nch.add(defaultNch[nch.size()]);

        for (int i = 0; i < 5; i++) {
            table.append("<tr>");
            table.append("<td>• ").append(vch.get(i)).append("</td>");
            table.append("<td>• ").append(sch.get(i)).append("</td>");
            table.append("<td>• ").append(nch.get(i)).append("</td>");
            table.append("</tr>\n");
        }
        table.append("</table>\n");

        return table.toString();
    }
}