package name.abuchen.portfolio.ui.api.service;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;

import java.time.LocalDateTime;

import org.junit.Test;

import name.abuchen.portfolio.model.Account;
import name.abuchen.portfolio.model.AccountTransaction;
import name.abuchen.portfolio.money.CurrencyUnit;

public class AccountValueAdjustmentServiceTest
{
    @Test
    public void createsDepositWhenTargetValueIsHigherThanCurrentValue()
    {
        var account = new Account("Cash");
        account.setCurrencyCode(CurrencyUnit.EUR);
        var initialDate = LocalDateTime.of(2026, 5, 27, 12, 0);
        account.addTransaction(new AccountTransaction(initialDate, CurrencyUnit.EUR, 1_000, null,
                        AccountTransaction.Type.DEPOSIT));

        var adjustmentDate = LocalDateTime.of(2026, 5, 28, 12, 0);
        var adjustment = AccountValueAdjustmentService.setValue(account, adjustmentDate, 1_550, "Balance sync",
                        "api");

        assertThat(adjustment.previousAmount(), is(1_000L));
        assertThat(adjustment.targetAmount(), is(1_550L));
        assertThat(adjustment.deltaAmount(), is(550L));
        assertThat(adjustment.isModified(), is(true));

        var transaction = adjustment.transaction();
        assertThat(transaction.getType(), is(AccountTransaction.Type.DEPOSIT));
        assertThat(transaction.getAmount(), is(550L));
        assertThat(transaction.getDateTime(), is(adjustmentDate));
        assertThat(transaction.getNote(), is("Balance sync"));
        assertThat(transaction.getSource(), is("api"));
        assertThat(account.getCurrentAmount(adjustmentDate.plusNanos(1)), is(1_550L));
    }

    @Test
    public void createsRemovalWhenTargetValueIsLowerThanCurrentValue()
    {
        var account = new Account("Cash");
        account.setCurrencyCode(CurrencyUnit.EUR);
        var initialDate = LocalDateTime.of(2026, 5, 27, 12, 0);
        account.addTransaction(new AccountTransaction(initialDate, CurrencyUnit.EUR, 1_000, null,
                        AccountTransaction.Type.DEPOSIT));

        var adjustmentDate = LocalDateTime.of(2026, 5, 28, 12, 0);
        var adjustment = AccountValueAdjustmentService.setValue(account, adjustmentDate, 400, null, null);

        assertThat(adjustment.previousAmount(), is(1_000L));
        assertThat(adjustment.targetAmount(), is(400L));
        assertThat(adjustment.deltaAmount(), is(-600L));
        assertThat(adjustment.isModified(), is(true));

        var transaction = adjustment.transaction();
        assertThat(transaction.getType(), is(AccountTransaction.Type.REMOVAL));
        assertThat(transaction.getAmount(), is(600L));
        assertThat(account.getCurrentAmount(adjustmentDate.plusNanos(1)), is(400L));
    }

    @Test
    public void doesNotCreateTransactionWhenTargetValueEqualsCurrentValue()
    {
        var account = new Account("Cash");
        account.setCurrencyCode(CurrencyUnit.EUR);
        var initialDate = LocalDateTime.of(2026, 5, 27, 12, 0);
        account.addTransaction(new AccountTransaction(initialDate, CurrencyUnit.EUR, 1_000, null,
                        AccountTransaction.Type.DEPOSIT));

        var adjustmentDate = LocalDateTime.of(2026, 5, 28, 12, 0);
        var adjustment = AccountValueAdjustmentService.setValue(account, adjustmentDate, 1_000, null, null);

        assertThat(adjustment.previousAmount(), is(1_000L));
        assertThat(adjustment.targetAmount(), is(1_000L));
        assertThat(adjustment.deltaAmount(), is(0L));
        assertThat(adjustment.isModified(), is(false));
        assertThat(adjustment.transaction(), nullValue());
        assertThat(account.getTransactions().size(), is(1));
    }
}
