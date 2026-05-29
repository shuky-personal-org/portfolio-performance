package name.abuchen.portfolio.ui.api.dto;

/**
 * Data Transfer Object for security account create and update requests.
 */
public class SecurityAccountMutationDto
{
    private String name;
    private String note;
    private Boolean retired;
    private String referenceAccountUuid;

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
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

    public String getReferenceAccountUuid()
    {
        return referenceAccountUuid;
    }

    public void setReferenceAccountUuid(String referenceAccountUuid)
    {
        this.referenceAccountUuid = referenceAccountUuid;
    }
}
