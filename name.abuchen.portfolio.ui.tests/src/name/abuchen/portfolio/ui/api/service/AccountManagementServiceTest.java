package name.abuchen.portfolio.ui.api.service;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;

import java.time.LocalDateTime;

import org.junit.Test;

import name.abuchen.portfolio.model.Account;
import name.abuchen.portfolio.model.AccountTransaction;
import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.money.CurrencyUnit;
import name.abuchen.portfolio.ui.api.dto.AccountMutationDto;
import name.abuchen.portfolio.ui.api.service.AccountManagementService.AccountDeletionException;

public class AccountManagementServiceTest
{
    @Test
    public void createsAccountWithClientBaseCurrencyByDefault()
    {
        var client = new Client();
        client.setBaseCurrency(CurrencyUnit.USD);

        var request = new AccountMutationDto();
        request.setName("  Savings  ");
        request.setNote("  Emergency fund  ");

        var account = AccountManagementService.createAccount(client, request);

        assertThat(client.getAccounts().size(), is(1));
        assertThat(client.getAccounts().get(0), is(account));
        assertThat(account.getName(), is("Savings"));
        assertThat(account.getCurrencyCode(), is(CurrencyUnit.USD));
        assertThat(account.getNote(), is("Emergency fund"));
        assertThat(account.isRetired(), is(false));
    }

    @Test
    public void updatesEditableAccountFields()
    {
        var client = new Client();
        var account = new Account("Cash");
        account.setCurrencyCode(CurrencyUnit.EUR);
        client.addAccount(account);

        var request = new AccountMutationDto();
        request.setName("Broker Cash");
        request.setCurrencyCode("usd");
        request.setNote("");
        request.setRetired(Boolean.TRUE);

        var updated = AccountManagementService.updateAccount(client, account.getUUID(), request);

        assertThat(updated, is(account));
        assertThat(account.getName(), is("Broker Cash"));
        assertThat(account.getCurrencyCode(), is(CurrencyUnit.USD));
        assertThat(account.getNote(), nullValue());
        assertThat(account.isRetired(), is(true));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsCurrencyChangesAfterTransactionsExist()
    {
        var client = new Client();
        var account = new Account("Cash");
        account.setCurrencyCode(CurrencyUnit.EUR);
        account.addTransaction(new AccountTransaction(LocalDateTime.of(2026, 5, 29, 12, 0), CurrencyUnit.EUR,
                        1_000, null, AccountTransaction.Type.DEPOSIT));
        client.addAccount(account);

        var request = new AccountMutationDto();
        request.setCurrencyCode(CurrencyUnit.USD);

        AccountManagementService.updateAccount(client, account.getUUID(), request);
    }

    @Test
    public void deletesEmptyAccount()
    {
        var client = new Client();
        var account = new Account("Cash");
        client.addAccount(account);

        var deleted = AccountManagementService.deleteAccount(client, account.getUUID());

        assertThat(deleted, is(account));
        assertThat(client.getAccounts().isEmpty(), is(true));
    }

    @Test(expected = AccountDeletionException.class)
    public void rejectsDeletingAccountWithTransactions()
    {
        var client = new Client();
        var account = new Account("Cash");
        account.setCurrencyCode(CurrencyUnit.EUR);
        account.addTransaction(new AccountTransaction(LocalDateTime.of(2026, 5, 29, 12, 0), CurrencyUnit.EUR,
                        1_000, null, AccountTransaction.Type.DEPOSIT));
        client.addAccount(account);

        AccountManagementService.deleteAccount(client, account.getUUID());
    }
}
