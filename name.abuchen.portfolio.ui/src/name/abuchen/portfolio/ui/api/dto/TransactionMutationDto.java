package name.abuchen.portfolio.ui.api.dto;

import java.time.LocalDateTime;

/**
 * Data Transfer Object for transaction create and update requests.
 */
public class TransactionMutationDto
{
    /** "ACCOUNT" or "PORTFOLIO" — used by the general transactions API. */
    private String transactionType;

    /** AccountTransaction.Type or PortfolioTransaction.Type name. */
    private String type;

    /** UUID of the owning account or security account (portfolio). */
    private String ownerUuid;

    private String securityUuid;
    private String securityAccountUuid;
    private String accountUuid;
    private LocalDateTime dateTime;
    private Double amount;
    private String currencyCode;
    private Double shares;
    private String note;
    private String source;

    public String getTransactionType()
    {
        return transactionType;
    }

    public void setTransactionType(String transactionType)
    {
        this.transactionType = transactionType;
    }

    public String getType()
    {
        return type;
    }

    public void setType(String type)
    {
        this.type = type;
    }

    public String getOwnerUuid()
    {
        return ownerUuid;
    }

    public void setOwnerUuid(String ownerUuid)
    {
        this.ownerUuid = ownerUuid;
    }

    public String getSecurityUuid()
    {
        return securityUuid;
    }

    public void setSecurityUuid(String securityUuid)
    {
        this.securityUuid = securityUuid;
    }

    public String getSecurityAccountUuid()
    {
        return securityAccountUuid;
    }

    public void setSecurityAccountUuid(String securityAccountUuid)
    {
        this.securityAccountUuid = securityAccountUuid;
    }

    public String getAccountUuid()
    {
        return accountUuid;
    }

    public void setAccountUuid(String accountUuid)
    {
        this.accountUuid = accountUuid;
    }

    public LocalDateTime getDateTime()
    {
        return dateTime;
    }

    public void setDateTime(LocalDateTime dateTime)
    {
        this.dateTime = dateTime;
    }

    public Double getAmount()
    {
        return amount;
    }

    public void setAmount(Double amount)
    {
        this.amount = amount;
    }

    public String getCurrencyCode()
    {
        return currencyCode;
    }

    public void setCurrencyCode(String currencyCode)
    {
        this.currencyCode = currencyCode;
    }

    public Double getShares()
    {
        return shares;
    }

    public void setShares(Double shares)
    {
        this.shares = shares;
    }

    public String getNote()
    {
        return note;
    }

    public void setNote(String note)
    {
        this.note = note;
    }

    public String getSource()
    {
        return source;
    }

    public void setSource(String source)
    {
        this.source = source;
    }
}
