package name.abuchen.portfolio.ui.api.dto;

import java.util.ArrayList;
import java.util.List;

public class TaxonomyImportResultDto
{
    private String taxonomyId;
    private String taxonomyName;
    private String action;
    private int createdObjects;
    private int modifiedObjects;
    private List<String> warnings = new ArrayList<>();

    public String getTaxonomyId()
    {
        return taxonomyId;
    }

    public void setTaxonomyId(String taxonomyId)
    {
        this.taxonomyId = taxonomyId;
    }

    public String getTaxonomyName()
    {
        return taxonomyName;
    }

    public void setTaxonomyName(String taxonomyName)
    {
        this.taxonomyName = taxonomyName;
    }

    public String getAction()
    {
        return action;
    }

    public void setAction(String action)
    {
        this.action = action;
    }

    public int getCreatedObjects()
    {
        return createdObjects;
    }

    public void setCreatedObjects(int createdObjects)
    {
        this.createdObjects = createdObjects;
    }

    public int getModifiedObjects()
    {
        return modifiedObjects;
    }

    public void setModifiedObjects(int modifiedObjects)
    {
        this.modifiedObjects = modifiedObjects;
    }

    public List<String> getWarnings()
    {
        return warnings;
    }

    public void setWarnings(List<String> warnings)
    {
        this.warnings = warnings != null ? warnings : new ArrayList<>();
    }
}
