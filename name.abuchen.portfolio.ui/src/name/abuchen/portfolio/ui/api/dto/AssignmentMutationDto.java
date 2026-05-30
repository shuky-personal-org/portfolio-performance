package name.abuchen.portfolio.ui.api.dto;

/**
 * Data Transfer Object for classification assignment create and update requests.
 */
public class AssignmentMutationDto
{
    private String investmentVehicleUuid;
    private Double weight;
    private Integer rank;

    public String getInvestmentVehicleUuid()
    {
        return investmentVehicleUuid;
    }

    public void setInvestmentVehicleUuid(String investmentVehicleUuid)
    {
        this.investmentVehicleUuid = investmentVehicleUuid;
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
}
