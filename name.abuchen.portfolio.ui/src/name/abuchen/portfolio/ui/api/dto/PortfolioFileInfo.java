package name.abuchen.portfolio.ui.api.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Data Transfer Object for portfolio file information.
 * Contains basic information about a loaded portfolio file.
 */
public class PortfolioFileInfo {
    
    private String id;
    private String name;
    private String path;
    private String baseCurrency;
    private String timezone;
    private int version;
    private Set<String> saveFlags;
    private LocalDateTime lastModified;
    private boolean encrypted;
    private int securitiesCount;
    private int accountsCount;
    private int portfoliosCount;
    private int transactionsCount;
    private int taxonomiesCount;
    private boolean clientLoaded;
    private String clientInfo;
    
    // Keep only reporting periods and currency conversions as utility data
    private List<ReportingPeriodDto> reportingPeriods;
    private List<CurrencyConversionDto> currencyConversions;
    
    // Constructors
    public PortfolioFileInfo() {}
    
    // Getters and Setters
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path;
    }
    
    public void setPath(String path) {
        this.path = path;
    }
    
    public String getBaseCurrency() {
        return baseCurrency;
    }
    
    public void setBaseCurrency(String baseCurrency) {
        this.baseCurrency = baseCurrency;
    }
    
    public String getTimezone() {
        return timezone;
    }
    
    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }
    
    public int getVersion() {
        return version;
    }
    
    public void setVersion(int version) {
        this.version = version;
    }
    
    public Set<String> getSaveFlags() {
        return saveFlags;
    }
    
    public void setSaveFlags(Set<String> saveFlags) {
        this.saveFlags = saveFlags;
    }
    
    public LocalDateTime getLastModified() {
        return lastModified;
    }
    
    public void setLastModified(LocalDateTime lastModified) {
        this.lastModified = lastModified;
    }
    
    public boolean isEncrypted() {
        return encrypted;
    }
    
    public void setEncrypted(boolean encrypted) {
        this.encrypted = encrypted;
    }
    
    public int getSecuritiesCount() {
        return securitiesCount;
    }
    
    public void setSecuritiesCount(int securitiesCount) {
        this.securitiesCount = securitiesCount;
    }
    
    public int getAccountsCount() {
        return accountsCount;
    }
    
    public void setAccountsCount(int accountsCount) {
        this.accountsCount = accountsCount;
    }
    
    public int getPortfoliosCount() {
        return portfoliosCount;
    }
    
    public void setPortfoliosCount(int portfoliosCount) {
        this.portfoliosCount = portfoliosCount;
    }
    
    public int getTransactionsCount() {
        return transactionsCount;
    }
    
    public void setTransactionsCount(int transactionsCount) {
        this.transactionsCount = transactionsCount;
    }
    
    public int getTaxonomiesCount() {
        return taxonomiesCount;
    }
    
    public void setTaxonomiesCount(int taxonomiesCount) {
        this.taxonomiesCount = taxonomiesCount;
    }
    
    public boolean isClientLoaded() {
        return clientLoaded;
    }
    
    public void setClientLoaded(boolean clientLoaded) {
        this.clientLoaded = clientLoaded;
    }
    
    public String getClientInfo() {
        return clientInfo;
    }
    
    public void setClientInfo(String clientInfo) {
        this.clientInfo = clientInfo;
    }
    
    public List<ReportingPeriodDto> getReportingPeriods() {
        return reportingPeriods;
    }
    
    public void setReportingPeriods(List<ReportingPeriodDto> reportingPeriods) {
        this.reportingPeriods = reportingPeriods;
    }
    
    public List<CurrencyConversionDto> getCurrencyConversions() {
        return currencyConversions;
    }
    
    public void setCurrencyConversions(List<CurrencyConversionDto> currencyConversions) {
        this.currencyConversions = currencyConversions;
    }
}
