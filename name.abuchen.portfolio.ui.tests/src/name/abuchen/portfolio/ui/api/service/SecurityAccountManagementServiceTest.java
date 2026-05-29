package name.abuchen.portfolio.ui.api.service;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;

import java.time.LocalDateTime;

import org.junit.Test;

import name.abuchen.portfolio.model.Account;
import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.Portfolio;
import name.abuchen.portfolio.model.PortfolioTransaction;
import name.abuchen.portfolio.money.CurrencyUnit;
import name.abuchen.portfolio.ui.api.dto.SecurityAccountMutationDto;
import name.abuchen.portfolio.ui.api.service.SecurityAccountManagementService.SecurityAccountDeletionException;

public class SecurityAccountManagementServiceTest
{
    @Test
    public void createsSecurityAccountWithExistingReferenceAccount()
    {
        var client = new Client();
        var account = new Account("Cash");
        client.addAccount(account);

        var request = new SecurityAccountMutationDto();
        request.setName("  Broker Depot  ");
        request.setNote("  Long-term holdings  ");
        request.setReferenceAccountUuid(account.getUUID());

        var portfolio = SecurityAccountManagementService.createSecurityAccount(client, request);

        assertThat(client.getPortfolios().size(), is(1));
        assertThat(client.getPortfolios().get(0), is(portfolio));
        assertThat(portfolio.getName(), is("Broker Depot"));
        assertThat(portfolio.getNote(), is("Long-term holdings"));
        assertThat(portfolio.isRetired(), is(false));
        assertThat(portfolio.getReferenceAccount(), is(account));
    }

    @Test
    public void createsReferenceAccountWhenNoneExists()
    {
        var client = new Client();
        client.setBaseCurrency(CurrencyUnit.USD);

        var request = new SecurityAccountMutationDto();
        request.setName("Broker Depot");

        var portfolio = SecurityAccountManagementService.createSecurityAccount(client, request);

        assertThat(client.getAccounts().size(), is(1));
        assertThat(portfolio.getReferenceAccount(), is(client.getAccounts().get(0)));
        assertThat(portfolio.getReferenceAccount().getName(), is("Reference Account"));
        assertThat(portfolio.getReferenceAccount().getCurrencyCode(), is(CurrencyUnit.USD));
    }

    @Test
    public void updatesEditableSecurityAccountFields()
    {
        var client = new Client();
        var originalAccount = new Account("Cash");
        var newAccount = new Account("Broker Cash");
        client.addAccount(originalAccount);
        client.addAccount(newAccount);

        var portfolio = new Portfolio("Depot");
        portfolio.setReferenceAccount(originalAccount);
        client.addPortfolio(portfolio);

        var request = new SecurityAccountMutationDto();
        request.setName("Main Depot");
        request.setNote("");
        request.setRetired(Boolean.TRUE);
        request.setReferenceAccountUuid(newAccount.getUUID());

        var updated = SecurityAccountManagementService.updateSecurityAccount(client, portfolio.getUUID(), request);

        assertThat(updated, is(portfolio));
        assertThat(portfolio.getName(), is("Main Depot"));
        assertThat(portfolio.getNote(), nullValue());
        assertThat(portfolio.isRetired(), is(true));
        assertThat(portfolio.getReferenceAccount(), is(newAccount));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsBlankCreateName()
    {
        var request = new SecurityAccountMutationDto();
        request.setName(" ");

        SecurityAccountManagementService.createSecurityAccount(new Client(), request);
    }

    @Test
    public void rejectedReferenceAccountUpdateDoesNotPartiallyRenameSecurityAccount()
    {
        var client = new Client();
        var account = new Account("Cash");
        client.addAccount(account);

        var portfolio = new Portfolio("Depot");
        portfolio.setReferenceAccount(account);
        client.addPortfolio(portfolio);

        var request = new SecurityAccountMutationDto();
        request.setName("Partially Applied");
        request.setReferenceAccountUuid("missing");

        try
        {
            SecurityAccountManagementService.updateSecurityAccount(client, portfolio.getUUID(), request);
        }
        catch (RuntimeException e)
        {
            assertThat(portfolio.getName(), is("Depot"));
            assertThat(portfolio.getReferenceAccount(), is(account));
            return;
        }

        throw new AssertionError("Reference account change should have been rejected");
    }

    @Test
    public void deletesEmptySecurityAccount()
    {
        var client = new Client();
        var portfolio = new Portfolio("Depot");
        client.addPortfolio(portfolio);

        var deleted = SecurityAccountManagementService.deleteSecurityAccount(client, portfolio.getUUID());

        assertThat(deleted, is(portfolio));
        assertThat(client.getPortfolios().isEmpty(), is(true));
    }

    @Test(expected = SecurityAccountDeletionException.class)
    public void rejectsDeletingSecurityAccountWithTransactions()
    {
        var client = new Client();
        var portfolio = new Portfolio("Depot");
        portfolio.addTransaction(new PortfolioTransaction(LocalDateTime.of(2026, 5, 29, 12, 0), CurrencyUnit.EUR,
                        1_000, null, 1_000, PortfolioTransaction.Type.DELIVERY_INBOUND, 0, 0));
        client.addPortfolio(portfolio);

        SecurityAccountManagementService.deleteSecurityAccount(client, portfolio.getUUID());
    }
}
