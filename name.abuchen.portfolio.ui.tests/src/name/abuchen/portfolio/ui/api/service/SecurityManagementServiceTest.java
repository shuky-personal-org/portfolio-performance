package name.abuchen.portfolio.ui.api.service;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;

import org.junit.Test;

import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.PortfolioTransaction;
import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.money.CurrencyUnit;
import name.abuchen.portfolio.online.QuoteFeed;
import name.abuchen.portfolio.ui.api.dto.SecurityMutationDto;
import name.abuchen.portfolio.ui.api.service.SecurityManagementService.SecurityDeletionException;

public class SecurityManagementServiceTest
{
    @Test
    public void createsSecurityWithClientBaseCurrencyByDefault()
    {
        var client = new Client();
        client.setBaseCurrency(CurrencyUnit.USD);

        var request = new SecurityMutationDto();
        request.setName("  Apple Inc.  ");
        request.setTickerSymbol(" AAPL ");
        request.setNote("  Tech stock  ");

        var security = SecurityManagementService.createSecurity(client, request);

        assertThat(client.getSecurities().size(), is(1));
        assertThat(client.getSecurities().get(0), is(security));
        assertThat(security.getName(), is("Apple Inc."));
        assertThat(security.getCurrencyCode(), is(CurrencyUnit.USD));
        assertThat(security.getTickerSymbol(), is("AAPL"));
        assertThat(security.getNote(), is("Tech stock"));
        assertThat(security.isRetired(), is(false));
        assertThat(security.getFeed(), is(QuoteFeed.MANUAL));
    }

    @Test
    public void updatesEditableSecurityFields()
    {
        var client = new Client();
        var security = new Security("Apple", CurrencyUnit.EUR);
        client.addSecurity(security);

        var request = new SecurityMutationDto();
        request.setName("Apple Inc.");
        request.setCurrencyCode("usd");
        request.setIsin("US0378331005");
        request.setNote("");
        request.setRetired(Boolean.TRUE);

        var updated = SecurityManagementService.updateSecurity(client, security.getUUID(), request);

        assertThat(updated, is(security));
        assertThat(security.getName(), is("Apple Inc."));
        assertThat(security.getCurrencyCode(), is(CurrencyUnit.USD));
        assertThat(security.getIsin(), is("US0378331005"));
        assertThat(security.getNote(), nullValue());
        assertThat(security.isRetired(), is(true));
    }

    @Test
    public void deletesSecurityWithoutTransactions()
    {
        var client = new Client();
        var security = new Security("Apple", CurrencyUnit.EUR);
        client.addSecurity(security);

        var deleted = SecurityManagementService.deleteSecurity(client, security.getUUID());

        assertThat(deleted, is(security));
        assertThat(client.getSecurities().isEmpty(), is(true));
    }

    @Test(expected = SecurityDeletionException.class)
    public void rejectsDeletingSecurityWithTransactions()
    {
        var client = new Client();
        var security = new Security("Apple", CurrencyUnit.EUR);
        var portfolio = new name.abuchen.portfolio.model.Portfolio();
        portfolio.setReferenceAccount(new name.abuchen.portfolio.model.Account("Cash"));
        client.addSecurity(security);
        client.addPortfolio(portfolio);

        var transaction = new PortfolioTransaction();
        transaction.setSecurity(security);
        transaction.setType(PortfolioTransaction.Type.DELIVERY_INBOUND);
        transaction.setShares(10_000_000L);
        transaction.setAmount(1_000_00L);
        transaction.setCurrencyCode(CurrencyUnit.EUR);
        portfolio.addTransaction(transaction);

        SecurityManagementService.deleteSecurity(client, security.getUUID());
    }
}
