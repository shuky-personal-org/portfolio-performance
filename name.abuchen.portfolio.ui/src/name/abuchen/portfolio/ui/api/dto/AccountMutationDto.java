package name.abuchen.portfolio.ui.api.dto;

/**
 * Data Transfer Object for account create and update requests.
 */
public class AccountMutationDto
{
    private String name;
    private String currencyCode;
    private String note;
    private Boolean retired;

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getCurrencyCode()
    {
        return currencyCode;
    }

    public void setCurrencyCode(String currencyCode)
    {
        this.currencyCode = currencyCode;
    }

    public String getNote()
    {
        return note;
    }

    public void setNote(String note)
    {
        this.note = note;
    }

    public Boolean getRetired()
    {
        return retired;
    }

    public void setRetired(Boolean retired)
    {
        this.retired = retired;
    }
}
