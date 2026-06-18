package name.abuchen.portfolio.ui.api.dto;

public class TwsAssignmentUpdateRequest
{
    private String twsInstanceId;
    private String password;

    public String getTwsInstanceId()
    {
        return twsInstanceId;
    }

    public void setTwsInstanceId(String twsInstanceId)
    {
        this.twsInstanceId = twsInstanceId;
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
