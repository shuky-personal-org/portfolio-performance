package name.abuchen.portfolio.ui.api.dto;

/**
 * Data Transfer Object describing an available quote feed provider.
 */
public class QuoteFeedDto
{
    private String id;
    private String name;

    public QuoteFeedDto()
    {
    }

    public QuoteFeedDto(String id, String name)
    {
        this.id = id;
        this.name = name;
    }

    public String getId()
    {
        return id;
    }

    public void setId(String id)
    {
        this.id = id;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }
}
