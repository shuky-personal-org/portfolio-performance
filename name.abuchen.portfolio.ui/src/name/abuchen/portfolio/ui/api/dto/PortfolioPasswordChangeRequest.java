package name.abuchen.portfolio.ui.api.dto;

/**
 * Request body for changing an encrypted portfolio file password.
 */
public class PortfolioPasswordChangeRequest
{
    private String currentPassword;
    private String newPassword;

    public String getCurrentPassword()
    {
        return currentPassword;
    }

    public void setCurrentPassword(String currentPassword)
    {
        this.currentPassword = currentPassword;
    }

    public String getNewPassword()
    {
        return newPassword;
    }

    public void setNewPassword(String newPassword)
    {
        this.newPassword = newPassword;
    }
}
