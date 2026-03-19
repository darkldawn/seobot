package com.example.demo;

import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class SeoScoreCalculator {

    public ReportData.SeoScore calculate(Document doc, String siteContent,
                                         List<ReportData.PositionData> positions) {
        ReportData.SeoScore score = new ReportData.SeoScore();

        // Техническое состояние (0-10)
        score.setTech(calculateTechScore(doc));

        // Контент (0-10)
        score.setContent(calculateContentScore(siteContent));

        // Индексация (0-10)
        score.setIndex(calculateIndexScore(doc));

        // Коммерческие факторы (0-10)
        score.setCommercial(calculateCommercialScore(doc));

        // Общий потенциал (среднее)
        score.setTotal(score.getAverage());

        // Процент использования (на основе позиций)
        score.setUsagePercent(calculateUsagePercent(positions));

        return score;
    }

    private int calculateTechScore(Document doc) {
        int score = 0;

        // Title (макс 2)
        String title = doc.title();
        if (title.length() >= 30 && title.length() <= 60) score += 2;
        else if (title.length() > 0) score += 1;

        // Description (макс 2)
        String desc = doc.select("meta[name=description]").attr("content");
        if (desc.length() >= 50 && desc.length() <= 160) score += 2;
        else if (desc.length() > 0) score += 1;

        // H1 (макс 2)
        int h1Count = doc.select("h1").size();
        if (h1Count == 1) score += 2;
        else if (h1Count > 1) score += 1;

        // Изображения (макс 2)
        int imagesWithoutAlt = doc.select("img:not([alt])").size();
        int totalImages = doc.select("img").size();
        if (totalImages > 0) {
            double altRatio = (double)(totalImages - imagesWithoutAlt) / totalImages;
            if (altRatio > 0.9) score += 2;
            else if (altRatio > 0.7) score += 1;
        }

        // Скорость загрузки (макс 2) - упрощенно
        score += 2; // Заглушка, позже можно добавить реальную проверку

        return Math.min(score, 10);
    }

    private int calculateContentScore(String siteContent) {
        int wordCount = siteContent.split("\\s+").length;
        if (wordCount > 3000) return 10;
        if (wordCount > 2000) return 8;
        if (wordCount > 1000) return 6;
        if (wordCount > 500) return 4;
        return 2;
    }

    private int calculateCommercialScore(Document doc) {
        String text = doc.text().toLowerCase();
        int score = 0;

        if (text.contains("цена") || text.contains("стоимость")) score += 2;
        if (text.contains("купить") || text.contains("заказать")) score += 2;
        if (text.contains("доставка")) score += 2;
        if (text.contains("гарантия")) score += 2;
        if (text.contains("отзыв")) score += 2;

        return Math.min(score, 10);
    }

    private int calculateIndexScore(Document doc) {
        // Проверяем robots meta
        String robots = doc.select("meta[name=robots]").attr("content");
        if (robots.contains("noindex")) return 4;
        if (robots.contains("nofollow")) return 6;
        return 8; // базово хорошо
    }

    private int calculateUsagePercent(List<ReportData.PositionData> positions) {
        if (positions == null || positions.isEmpty()) return 35;

        long top3Count = positions.stream().filter(p -> p.getPosition() <= 3).count();
        long top10Count = positions.stream().filter(p -> p.getPosition() <= 10).count();

        // Простая формула: чем больше в топе, тем выше процент
        int percent = 30 + (int)(top3Count * 5) + (int)(top10Count * 2);
        return Math.min(percent, 100);
    }
}