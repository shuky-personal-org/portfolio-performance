package name.abuchen.portfolio.ui.api.service;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.nullValue;

import java.time.LocalDateTime;

import org.junit.Test;

import name.abuchen.portfolio.model.Account;
import name.abuchen.portfolio.model.AccountTransaction;
import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.money.CurrencyUnit;

public class AccountManagementServiceTest
{
    @Test
    public void createsAccountWithNormalizedValues()
    {
        var client = new Client();

        var account = AccountManagementService.createAccount(client, "  Cash  ", " usd ", "  Savings  ");

        assertThat(client.getAccounts(), contains(account));
        assertThat(account.getName(), is("Cash"));
        assertThat(account.getCurrencyCode(), is(CurrencyUnit.USD));
        assertThat(account.getNote(), is("Savings"));
    }

    @Test
    public void defaultsAccountCurrencyToClientBaseCurrency()
    {
        var client = new Client();
        client.setBaseCurrency(CurrencyUnit.USD);

        var account = AccountManagementService.createAccount(client, "Cash", null, "");

        assertThat(account.getCurrencyCode(), is(CurrencyUnit.USD));
        assertThat(account.getNote(), nullValue());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsBlankAccountName()
    {
        AccountManagementService.createAccount(new Client(), " ", CurrencyUnit.EUR, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnsupportedCurrencyCode()
    {
        AccountManagementService.createAccount(new Client(), "Cash", "NOPE", null);
    }

    @Test
    public void updatesAccountMetadata()
    {
        var account = new Account("Cash");
        account.setCurrencyCode(CurrencyUnit.EUR);

        var modified = AccountManagementService.updateAccount(account, " Bank ", CurrencyUnit.USD, "  Reserve  ",
                        Boolean.TRUE);

        assertThat(modified, is(true));
        assertThat(account.getName(), is("Bank"));
        assertThat(account.getCurrencyCode(), is(CurrencyUnit.USD));
        assertThat(account.getNote(), is("Reserve"));
        assertThat(account.isRetired(), is(true));
    }

    @Test
    public void leavesAccountUnchangedWhenValuesMatch()
    {
        var account = new Account("Cash");
        account.setCurrencyCode(CurrencyUnit.EUR);
        account.setNote("Reserve");

        var modified = AccountManagementService.updateAccount(account, "Cash", CurrencyUnit.EUR, "Reserve",
                        Boolean.FALSE);

        assertThat(modified, is(false));
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsCurrencyChangeWhenTransactionsExist()
    {
        var account = new Account("Cash");
        account.setCurrencyCode(CurrencyUnit.EUR);
        account.addTransaction(new AccountTransaction(LocalDateTime.now(), CurrencyUnit.EUR, 1_000, null,
                        AccountTransaction.Type.DEPOSIT));

        AccountManagementService.updateAccount(account, null, CurrencyUnit.USD, null, null);
    }

    @Test
    public void deletesEmptyAccount()
    {
        var client = new Client();
        var account = AccountManagementService.createAccount(client, "Cash", CurrencyUnit.EUR, null);

        AccountManagementService.deleteAccount(client, account);

        assertThat(client.getAccounts(), empty());
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsDeletingAccountWithTransactions()
    {
        var client = new Client();
        var account = AccountManagementService.createAccount(client, "Cash", CurrencyUnit.EUR, null);
        account.addTransaction(new AccountTransaction(LocalDateTime.now(), CurrencyUnit.EUR, 1_000, null,
                        AccountTransaction.Type.DEPOSIT));

        AccountManagementService.deleteAccount(client, account);
    }
}
