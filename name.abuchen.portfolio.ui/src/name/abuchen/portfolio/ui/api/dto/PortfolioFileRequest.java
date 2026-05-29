package name.abuchen.portfolio.ui.api.dto;

/**
 * Request body for portfolio file creation and duplication.
 */
public class PortfolioFileRequest
{
    private String name;
    private String path;

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getPath()
    {
        return path;
    }

    public void setPath(String path)
    {
        this.path = path;
    }
}
