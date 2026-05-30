package name.abuchen.portfolio.ui.api.dto;

/**
 * Request body for updating a portfolio's base (reporting) currency.
 */
public class BaseCurrencyUpdateRequest
{
    private String currencyCode;
    private String password;

    public String getCurrencyCode()
    {
        return currencyCode;
    }

    public void setCurrencyCode(String currencyCode)
    {
        this.currencyCode = currencyCode;
    }

    public String getPassword()
    {
        return password;
    }

    public void setPassword(String password)
    {
        this.password = password;
    }
}
