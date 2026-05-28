package name.abuchen.portfolio.ui.api.service;

import java.time.LocalDateTime;
import java.util.Objects;

import name.abuchen.portfolio.model.Account;
import name.abuchen.portfolio.model.AccountTransaction;

/**
 * Creates account transactions required to set an account to a target value.
 */
public final class AccountValueAdjustmentService
{
    private AccountValueAdjustmentService()
    {
    }

    public static Adjustment setValue(Account account, LocalDateTime dateTime, long targetAmount, String note,
                    String source)
    {
        Objects.requireNonNull(account, "account must not be null");
        Objects.requireNonNull(dateTime, "dateTime must not be null");

        long previousAmount = account.getCurrentAmount(dateTime);
        long deltaAmount = targetAmount - previousAmount;

        if (deltaAmount == 0)
            return new Adjustment(previousAmount, targetAmount, deltaAmount, null);

        AccountTransaction.Type type = deltaAmount > 0
                        ? AccountTransaction.Type.DEPOSIT
                        : AccountTransaction.Type.REMOVAL;

        var transaction = new AccountTransaction(dateTime, account.getCurrencyCode(), Math.abs(deltaAmount), null,
                        type);
        transaction.setNote(note);
        transaction.setSource(source);

        account.addTransaction(transaction);

        return new Adjustment(previousAmount, targetAmount, deltaAmount, transaction);
    }

    public record Adjustment(long previousAmount, long targetAmount, long deltaAmount, AccountTransaction transaction)
    {
        public boolean isModified()
        {
            return transaction != null;
        }
    }
}
