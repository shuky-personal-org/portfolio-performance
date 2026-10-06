package name.abuchen.portfolio.ui.api.dto;

import java.util.Map;

/**
 * DTO for Flex import request.
 */
public class FlexImportRequest
{
    /**
     * Map of currency code to primary account UUID.
     */
    private Map<String, String> currencyAccountMap;

    /**
     * Map of currency code to secondary account UUID (for transfers).
     */
    private Map<String, String> currencySecondaryAccountMap;

    /**
     * Map of currency code to primary portfolio UUID.
     */
    private Map<String, String> currencyPortfolioMap;

    /**
     * Map of currency code to secondary portfolio UUID (for transfers).
     */
    private Map<String, String> currencySecondaryPortfolioMap;

    /**
     * Primary portfolio UUID.
     */
    private String portfolioUUID;

    /**
     * Secondary portfolio UUID (for portfolio transfers, optional).
     */
    private String secondaryPortfolioUUID;

    /**
     * Whether to convert BuySell transactions to Delivery transactions.
     */
    private boolean convertBuySellToDelivery = false;

    /**
     * Whether to remove dividends.
     */
    private boolean removeDividends = false;

    /**
     * Whether to import notes from source.
     */
    private boolean importNotes = true;

    /**
     * Relative file path to Flex report XML file (relative to portfolio directory).
     */
    private String filePath;

    public String getFilePath()
    {
        return filePath;
    }

    public void setFilePath(String filePath)
    {
        this.filePath = filePath;
    }

    public Map<String, String> getCurrencyAccountMap()
    {
        return currencyAccountMap;
    }

    public void setCurrencyAccountMap(Map<String, String> currencyAccountMap)
    {
        this.currencyAccountMap = currencyAccountMap;
    }

    public Map<String, String> getCurrencySecondaryAccountMap()
    {
        return currencySecondaryAccountMap;
    }

    public void setCurrencySecondaryAccountMap(Map<String, String> currencySecondaryAccountMap)
    {
        this.currencySecondaryAccountMap = currencySecondaryAccountMap;
    }

    public Map<String, String> getCurrencyPortfolioMap()
    {
        return currencyPortfolioMap;
    }

    public void setCurrencyPortfolioMap(Map<String, String> currencyPortfolioMap)
    {
        this.currencyPortfolioMap = currencyPortfolioMap;
    }

    public Map<String, String> getCurrencySecondaryPortfolioMap()
    {
        return currencySecondaryPortfolioMap;
    }

    public void setCurrencySecondaryPortfolioMap(Map<String, String> currencySecondaryPortfolioMap)
    {
        this.currencySecondaryPortfolioMap = currencySecondaryPortfolioMap;
    }

    public String getPortfolioUUID()
    {
        return portfolioUUID;
    }

    public void setPortfolioUUID(String portfolioUUID)
    {
        this.portfolioUUID = portfolioUUID;
    }

    public String getSecondaryPortfolioUUID()
    {
        return secondaryPortfolioUUID;
    }

    public void setSecondaryPortfolioUUID(String secondaryPortfolioUUID)
    {
        this.secondaryPortfolioUUID = secondaryPortfolioUUID;
    }

    public boolean isConvertBuySellToDelivery()
    {
        return convertBuySellToDelivery;
    }

    public void setConvertBuySellToDelivery(boolean convertBuySellToDelivery)
    {
        this.convertBuySellToDelivery = convertBuySellToDelivery;
    }

    public boolean isRemoveDividends()
    {
        return removeDividends;
    }

    public void setRemoveDividends(boolean removeDividends)
    {
        this.removeDividends = removeDividends;
    }

    public boolean isImportNotes()
    {
        return importNotes;
    }

    public void setImportNotes(boolean importNotes)
    {
        this.importNotes = importNotes;
    }
}

