package name.abuchen.portfolio.ui.api.dto;

import java.util.List;

/**
 * Data Transfer Object for taxonomy create and update requests.
 */
public class TaxonomyMutationDto
{
    private String name;
    private String source;
    private List<String> dimensions;
    private String templateId;
    private ClassificationMutationDto root;

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getSource()
    {
        return source;
    }

    public void setSource(String source)
    {
        this.source = source;
    }

    public List<String> getDimensions()
    {
        return dimensions;
    }

    public void setDimensions(List<String> dimensions)
    {
        this.dimensions = dimensions;
    }

    public String getTemplateId()
    {
        return templateId;
    }

    public void setTemplateId(String templateId)
    {
        this.templateId = templateId;
    }

    public ClassificationMutationDto getRoot()
    {
        return root;
    }

    public void setRoot(ClassificationMutationDto root)
    {
        this.root = root;
    }
}
