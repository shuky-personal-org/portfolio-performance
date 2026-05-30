package name.abuchen.portfolio.ui.api.dto;

/**
 * Data Transfer Object for reading and updating a security's price update
 * configuration (historical and latest quote feeds).
 */
public class SecurityPriceUpdatesDto
{
    private String feed;
    private String feedURL;
    private String latestFeed;
    private String latestFeedURL;

    public String getFeed()
    {
        return feed;
    }

    public void setFeed(String feed)
    {
        this.feed = feed;
    }

    public String getFeedURL()
    {
        return feedURL;
    }

    public void setFeedURL(String feedURL)
    {
        this.feedURL = feedURL;
    }

    public String getLatestFeed()
    {
        return latestFeed;
    }

    public void setLatestFeed(String latestFeed)
    {
        this.latestFeed = latestFeed;
    }

    public String getLatestFeedURL()
    {
        return latestFeedURL;
    }

    public void setLatestFeedURL(String latestFeedURL)
    {
        this.latestFeedURL = latestFeedURL;
    }
}
