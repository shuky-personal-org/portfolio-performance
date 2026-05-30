package name.abuchen.portfolio.ui.api.dto;

import java.time.Instant;

/**
 * Data Transfer Object for Security serialization.
 * Contains security information for API responses.
 */
public class SecurityDto {
    
    private String uuid;
    private String name;
    private String currencyCode;
    private String targetCurrencyCode;
    private String isin;
    private String tickerSymbol;
    private String wkn;
    private String note;
    private boolean isRetired;
    private String feed;
    private String feedURL;
    private String latestFeed;
    private String latestFeedURL;
    private int pricesCount;
    private Instant updatedAt;
    private Double lastPrice;
    private Double dailyPriceChange;
    
    // Holdings information
    private Double sharesHeld;
    private Double avgPricePerShare;
    private Long totalHoldingValueSecurityCurrency;
    private Long totalHoldingValueBaseCurrency;
    private Long unrealizedGainsAllTime;
    private Long unrealizedGainsAllTimeSecurityCurrency;
    private Long unrealizedGainsYTD;
    private Long unrealizedGainsYTDSecurityCurrency;
    private Long unrealizedGainsDaily;
    private Long unrealizedGainsDailySecurityCurrency;
    private Long totalEarnings;
    private String securityAccountUuid;
    
    // Constructors
    public SecurityDto() {}
    
    public SecurityDto(String uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }
    
    // Getters and Setters
    public String getUuid() {
        return uuid;
    }
    
    public void setUuid(String uuid) {
        this.uuid = uuid;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getCurrencyCode() {
        return currencyCode;
    }
    
    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
    }
    
    public String getTargetCurrencyCode() {
        return targetCurrencyCode;
    }
    
    public void setTargetCurrencyCode(String targetCurrencyCode) {
        this.targetCurrencyCode = targetCurrencyCode;
    }
    
    public String getIsin() {
        return isin;
    }
    
    public void setIsin(String isin) {
        this.isin = isin;
    }
    
    public String getTickerSymbol() {
        return tickerSymbol;
    }
    
    public void setTickerSymbol(String tickerSymbol) {
        this.tickerSymbol = tickerSymbol;
    }
    
    public String getWkn() {
        return wkn;
    }
    
    public void setWkn(String wkn) {
        this.wkn = wkn;
    }
    
    public String getNote() {
        return note;
    }
    
    public void setNote(String note) {
        this.note = note;
    }
    
    public boolean isRetired() {
        return isRetired;
    }
    
    public void setRetired(boolean retired) {
        isRetired = retired;
    }
    
    public String getFeed() {
        return feed;
    }
    
    public void setFeed(String feed) {
        this.feed = feed;
    }
    
    public String getFeedURL() {
        return feedURL;
    }
    
    public void setFeedURL(String feedURL) {
        this.feedURL = feedURL;
    }
    
    public String getLatestFeed() {
        return latestFeed;
    }
    
    public void setLatestFeed(String latestFeed) {
        this.latestFeed = latestFeed;
    }
    
    public String getLatestFeedURL() {
        return latestFeedURL;
    }
    
    public void setLatestFeedURL(String latestFeedURL) {
        this.latestFeedURL = latestFeedURL;
    }
    
    public int getPricesCount() {
        return pricesCount;
    }
    
    public void setPricesCount(int pricesCount) {
        this.pricesCount = pricesCount;
    }
    
    public Instant getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    public Double getLastPrice() {
        return lastPrice;
    }
    
    public void setLastPrice(Double lastPrice) {
        this.lastPrice = lastPrice;
    }
    
    public Double getDailyPriceChange() {
        return dailyPriceChange;
    }
    
    public void setDailyPriceChange(Double dailyPriceChange) {
        this.dailyPriceChange = dailyPriceChange;
    }
    
    public Double getSharesHeld() {
        return sharesHeld;
    }
    
    public void setSharesHeld(Double sharesHeld) {
        this.sharesHeld = sharesHeld;
    }
    
    public Double getAvgPricePerShare() {
        return avgPricePerShare;
    }
    
    public void setAvgPricePerShare(Double avgPricePerShare) {
        this.avgPricePerShare = avgPricePerShare;
    }
    
    public Long getTotalHoldingValueSecurityCurrency() {
        return totalHoldingValueSecurityCurrency;
    }
    
    public void setTotalHoldingValueSecurityCurrency(Long totalHoldingValueSecurityCurrency) {
        this.totalHoldingValueSecurityCurrency = totalHoldingValueSecurityCurrency;
    }
    
    public Long getTotalHoldingValueBaseCurrency() {
        return totalHoldingValueBaseCurrency;
    }
    
    public void setTotalHoldingValueBaseCurrency(Long totalHoldingValueBaseCurrency) {
        this.totalHoldingValueBaseCurrency = totalHoldingValueBaseCurrency;
    }
    
    public Long getUnrealizedGainsAllTime() {
        return unrealizedGainsAllTime;
    }
    
    public void setUnrealizedGainsAllTime(Long unrealizedGainsAllTime) {
        this.unrealizedGainsAllTime = unrealizedGainsAllTime;
    }

    public Long getUnrealizedGainsAllTimeSecurityCurrency() {
        return unrealizedGainsAllTimeSecurityCurrency;
    }

    public void setUnrealizedGainsAllTimeSecurityCurrency(Long unrealizedGainsAllTimeSecurityCurrency) {
        this.unrealizedGainsAllTimeSecurityCurrency = unrealizedGainsAllTimeSecurityCurrency;
    }
    
    public Long getUnrealizedGainsYTD() {
        return unrealizedGainsYTD;
    }
    
    public void setUnrealizedGainsYTD(Long unrealizedGainsYTD) {
        this.unrealizedGainsYTD = unrealizedGainsYTD;
    }

    public Long getUnrealizedGainsYTDSecurityCurrency() {
        return unrealizedGainsYTDSecurityCurrency;
    }

    public void setUnrealizedGainsYTDSecurityCurrency(Long unrealizedGainsYTDSecurityCurrency) {
        this.unrealizedGainsYTDSecurityCurrency = unrealizedGainsYTDSecurityCurrency;
    }
    
    public Long getUnrealizedGainsDaily() {
        return unrealizedGainsDaily;
    }
    
    public void setUnrealizedGainsDaily(Long unrealizedGainsDaily) {
        this.unrealizedGainsDaily = unrealizedGainsDaily;
    }

    public Long getUnrealizedGainsDailySecurityCurrency() {
        return unrealizedGainsDailySecurityCurrency;
    }

    public void setUnrealizedGainsDailySecurityCurrency(Long unrealizedGainsDailySecurityCurrency) {
        this.unrealizedGainsDailySecurityCurrency = unrealizedGainsDailySecurityCurrency;
    }
    
    public Long getTotalEarnings() {
        return totalEarnings;
    }
    
    public void setTotalEarnings(Long totalEarnings) {
        this.totalEarnings = totalEarnings;
    }

    public String getSecurityAccountUuid() {
        return securityAccountUuid;
    }

    public void setSecurityAccountUuid(String securityAccountUuid) {
        this.securityAccountUuid = securityAccountUuid;
    }
}

