package com.example.demo;

import java.util.List;
import java.util.Map;

public class ReportData {
    private String domain;
    private String date;
    private String region;
    private String aiAnalysis;
    private List<String> keywords;
    private List<PositionData> positions;
    private SeoScore scores;
    private String finalComment;

    // Технический аудит (существующие)
    private int titleLength;
    private int descriptionLength;
    private int h1Count;
    private int imagesWithoutAlt;
    private long loadTime;

    // НОВЫЕ ПОЛЯ
    private Boolean robotsExists;
    private String robotsAnalysis;  // Анализ robots.txt
    private Boolean sitemapExists;
    private Boolean mobileFriendly;
    private Map<String, List<String>> duplicateTitles;
    private Map<String, List<String>> duplicateDescriptions;
    private Map<String, List<String>> duplicatePages;

    // Геттеры и сеттеры существующих полей
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public String getAiAnalysis() { return aiAnalysis; }
    public void setAiAnalysis(String aiAnalysis) { this.aiAnalysis = aiAnalysis; }

    public List<String> getKeywords() { return keywords; }
    public void setKeywords(List<String> keywords) { this.keywords = keywords; }

    public List<PositionData> getPositions() { return positions; }
    public void setPositions(List<PositionData> positions) { this.positions = positions; }

    public SeoScore getScores() { return scores; }
    public void setScores(SeoScore scores) { this.scores = scores; }

    public String getFinalComment() { return finalComment; }
    public void setFinalComment(String finalComment) { this.finalComment = finalComment; }

    public int getTitleLength() { return titleLength; }
    public void setTitleLength(int titleLength) { this.titleLength = titleLength; }

    public int getDescriptionLength() { return descriptionLength; }
    public void setDescriptionLength(int descriptionLength) { this.descriptionLength = descriptionLength; }

    public int getH1Count() { return h1Count; }
    public void setH1Count(int h1Count) { this.h1Count = h1Count; }

    public int getImagesWithoutAlt() { return imagesWithoutAlt; }
    public void setImagesWithoutAlt(int imagesWithoutAlt) { this.imagesWithoutAlt = imagesWithoutAlt; }

    public long getLoadTime() { return loadTime; }
    public void setLoadTime(long loadTime) { this.loadTime = loadTime; }

    // Геттеры и сеттеры для новых полей
    public Boolean getRobotsExists() { return robotsExists; }
    public void setRobotsExists(Boolean robotsExists) { this.robotsExists = robotsExists; }

    public String getRobotsAnalysis() { return robotsAnalysis; }
    public void setRobotsAnalysis(String robotsAnalysis) { this.robotsAnalysis = robotsAnalysis; }

    public Boolean getSitemapExists() { return sitemapExists; }
    public void setSitemapExists(Boolean sitemapExists) { this.sitemapExists = sitemapExists; }

    public Boolean getMobileFriendly() { return mobileFriendly; }
    public void setMobileFriendly(Boolean mobileFriendly) { this.mobileFriendly = mobileFriendly; }

    public Map<String, List<String>> getDuplicateTitles() { return duplicateTitles; }
    public void setDuplicateTitles(Map<String, List<String>> duplicateTitles) { this.duplicateTitles = duplicateTitles; }

    public Map<String, List<String>> getDuplicateDescriptions() { return duplicateDescriptions; }
    public void setDuplicateDescriptions(Map<String, List<String>> duplicateDescriptions) { this.duplicateDescriptions = duplicateDescriptions; }

    public Map<String, List<String>> getDuplicatePages() { return duplicatePages; }
    public void setDuplicatePages(Map<String, List<String>> duplicatePages) { this.duplicatePages = duplicatePages; }

    public static class PositionData {
        private String keyword;
        private int position;
        private String status;

        public PositionData(String keyword, int position) {
            this.keyword = keyword;
            this.position = position;
            if (position <= 3) this.status = "🟢";
            else if (position <= 10) this.status = "🟡";
            else this.status = "🔴";
        }

        public String getKeyword() { return keyword; }
        public int getPosition() { return position; }
        public String getStatus() { return status; }
    }

    public static class SeoScore {
        private int tech;
        private int content;
        private int index;
        private int commercial;
        private int total;
        private int usagePercent;

        public int getAverage() {
            return (tech + content + index + commercial) / 4;
        }

        public int getTech() { return tech; }
        public void setTech(int tech) { this.tech = tech; }

        public int getContent() { return content; }
        public void setContent(int content) { this.content = content; }

        public int getIndex() { return index; }
        public void setIndex(int index) { this.index = index; }

        public int getCommercial() { return commercial; }
        public void setCommercial(int commercial) { this.commercial = commercial; }

        public int getTotal() { return total; }
        public void setTotal(int total) { this.total = total; }

        public int getUsagePercent() { return usagePercent; }
        public void setUsagePercent(int usagePercent) { this.usagePercent = usagePercent; }
    }
}