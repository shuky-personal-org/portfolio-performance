package name.abuchen.portfolio.ui.api.dto;

import java.util.List;

/**
 * Data Transfer Object for classification node create and update requests.
 */
public class ClassificationMutationDto
{
    private String id;
    private String name;
    private String description;
    private String color;
    private Double weight;
    private Integer rank;
    private String key;
    private List<ClassificationMutationDto> children;
    private List<AssignmentMutationDto> assignments;

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

    public String getDescription()
    {
        return description;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

    public String getColor()
    {
        return color;
    }

    public void setColor(String color)
    {
        this.color = color;
    }

    public Double getWeight()
    {
        return weight;
    }

    public void setWeight(Double weight)
    {
        this.weight = weight;
    }

    public Integer getRank()
    {
        return rank;
    }

    public void setRank(Integer rank)
    {
        this.rank = rank;
    }

    public String getKey()
    {
        return key;
    }

    public void setKey(String key)
    {
        this.key = key;
    }

    public List<ClassificationMutationDto> getChildren()
    {
        return children;
    }

    public void setChildren(List<ClassificationMutationDto> children)
    {
        this.children = children;
    }

    public List<AssignmentMutationDto> getAssignments()
    {
        return assignments;
    }

    public void setAssignments(List<AssignmentMutationDto> assignments)
    {
        this.assignments = assignments;
    }
}
