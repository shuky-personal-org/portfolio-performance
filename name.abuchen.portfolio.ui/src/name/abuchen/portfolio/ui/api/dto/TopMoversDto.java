package name.abuchen.portfolio.ui.api.dto;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Data Transfer Object for top security movers over a short reporting period.
 */
public class TopMoversDto
{
    @JsonProperty("portfolioId")
    private String portfolioId;

    @JsonProperty("days")
    private int days;

    @JsonProperty("startDate")
    private LocalDate startDate;

    @JsonProperty("endDate")
    private LocalDate endDate;

    @JsonProperty("limit")
    private int limit;

    @JsonProperty("gainers")
    private List<TopMoverEntryDto> gainers = new ArrayList<>();

    @JsonProperty("losers")
    private List<TopMoverEntryDto> losers = new ArrayList<>();

    public String getPortfolioId()
    {
        return portfolioId;
    }

    public void setPortfolioId(String portfolioId)
    {
        this.portfolioId = portfolioId;
    }

    public int getDays()
    {
        return days;
    }

    public void setDays(int days)
    {
        this.days = days;
    }

    public LocalDate getStartDate()
    {
        return startDate;
    }

    public void setStartDate(LocalDate startDate)
    {
        this.startDate = startDate;
    }

    public LocalDate getEndDate()
    {
        return endDate;
    }

    public void setEndDate(LocalDate endDate)
    {
        this.endDate = endDate;
    }

    public int getLimit()
    {
        return limit;
    }

    public void setLimit(int limit)
    {
        this.limit = limit;
    }

    public List<TopMoverEntryDto> getGainers()
    {
        return gainers;
    }

    public void setGainers(List<TopMoverEntryDto> gainers)
    {
        this.gainers = gainers;
    }

    public void addGainer(TopMoverEntryDto entry)
    {
        this.gainers.add(entry);
    }

    public List<TopMoverEntryDto> getLosers()
    {
        return losers;
    }

    public void setLosers(List<TopMoverEntryDto> losers)
    {
        this.losers = losers;
    }

    public void addLoser(TopMoverEntryDto entry)
    {
        this.losers.add(entry);
    }

    public static class TopMoverEntryDto
    {
        @JsonProperty("uuid")
        private String uuid;

        @JsonProperty("name")
        private String name;

        @JsonProperty("tickerSymbol")
        private String tickerSymbol;

        @JsonProperty("priceChangePercent")
        private double priceChangePercent;

        public String getUuid()
        {
            return uuid;
        }

        public void setUuid(String uuid)
        {
            this.uuid = uuid;
        }

        public String getName()
        {
            return name;
        }

        public void setName(String name)
        {
            this.name = name;
        }

        public String getTickerSymbol()
        {
            return tickerSymbol;
        }

        public void setTickerSymbol(String tickerSymbol)
        {
            this.tickerSymbol = tickerSymbol;
        }

        public double getPriceChangePercent()
        {
            return priceChangePercent;
        }

        public void setPriceChangePercent(double priceChangePercent)
        {
            this.priceChangePercent = priceChangePercent;
        }
    }
}
