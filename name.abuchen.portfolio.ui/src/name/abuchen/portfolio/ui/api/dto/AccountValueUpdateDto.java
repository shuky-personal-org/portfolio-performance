package name.abuchen.portfolio.ui.api.dto;

import java.time.LocalDateTime;

/**
 * Data Transfer Object for setting an account to a target value.
 */
public class AccountValueUpdateDto
{
    private Double value;
    private LocalDateTime dateTime;
    private String note;
    private String source;

    public Double getValue()
    {
        return value;
    }

    public void setValue(Double value)
    {
        this.value = value;
    }

    public LocalDateTime getDateTime()
    {
        return dateTime;
    }

    public void setDateTime(LocalDateTime dateTime)
    {
        this.dateTime = dateTime;
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
