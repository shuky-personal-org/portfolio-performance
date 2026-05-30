package name.abuchen.portfolio.ui.api.service;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.time.LocalDateTime;

import org.junit.Test;

import name.abuchen.portfolio.model.Account;
import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.Portfolio;
import name.abuchen.portfolio.model.PortfolioTransaction;
import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.money.CurrencyUnit;
import name.abuchen.portfolio.ui.api.dto.TransactionMutationDto;

public class TransactionManagementServiceTest
{
    @Test
    public void createsBuyTransactionWithLinkedAccountTransaction()
    {
        var client = new Client();
        var security = new Security("Apple", CurrencyUnit.EUR);
        var account = new Account("Cash");
        account.setCurrencyCode(CurrencyUnit.EUR);
        var portfolio = new Portfolio("Depot");
        portfolio.setReferenceAccount(account);
        client.addSecurity(security);
        client.addAccount(account);
        client.addPortfolio(portfolio);

        var request = new TransactionMutationDto();
        request.setType("BUY");
        request.setSecurityUuid(security.getUUID());
        request.setSecurityAccountUuid(portfolio.getUUID());
        request.setAccountUuid(account.getUUID());
        request.setDateTime(LocalDateTime.of(2026, 5, 30, 12, 0));
        request.setShares(10d);
        request.setAmount(1000d);

        var pair = TransactionManagementService.createTransaction(client, request);
        var portfolioTransaction = (PortfolioTransaction) pair.getTransaction();

        assertThat(portfolioTransaction.getType(), is(PortfolioTransaction.Type.BUY));
        assertThat(portfolioTransaction.getShares(), is(1_000_000_000L));
        assertThat(account.getTransactions().size(), is(1));
        assertThat(portfolio.getTransactions().size(), is(1));
        assertThat(account.getTransactions().get(0).getCrossEntry(), is(portfolio.getTransactions().get(0).getCrossEntry()));
    }

    @Test
    public void createsDeliveryInboundTransaction()
    {
        var client = new Client();
        var security = new Security("Apple", CurrencyUnit.EUR);
        var account = new Account("Cash");
        account.setCurrencyCode(CurrencyUnit.EUR);
        var portfolio = new Portfolio("Depot");
        portfolio.setReferenceAccount(account);
        client.addSecurity(security);
        client.addAccount(account);
        client.addPortfolio(portfolio);

        var request = new TransactionMutationDto();
        request.setType("DELIVERY_INBOUND");
        request.setSecurityUuid(security.getUUID());
        request.setSecurityAccountUuid(portfolio.getUUID());
        request.setDateTime(LocalDateTime.of(2026, 5, 30, 12, 0));
        request.setShares(5d);
        request.setAmount(500d);

        var pair = TransactionManagementService.createTransaction(client, request);
        var portfolioTransaction = (PortfolioTransaction) pair.getTransaction();

        assertThat(portfolioTransaction.getType(), is(PortfolioTransaction.Type.DELIVERY_INBOUND));
        assertThat(account.getTransactions().isEmpty(), is(true));
        assertThat(portfolio.getTransactions().size(), is(1));
    }

    @Test
    public void deletesBuyTransactionAndLinkedAccountTransaction()
    {
        var client = new Client();
        var security = new Security("Apple", CurrencyUnit.EUR);
        var account = new Account("Cash");
        account.setCurrencyCode(CurrencyUnit.EUR);
        var portfolio = new Portfolio("Depot");
        portfolio.setReferenceAccount(account);
        client.addSecurity(security);
        client.addAccount(account);
        client.addPortfolio(portfolio);

        var request = new TransactionMutationDto();
        request.setType("BUY");
        request.setSecurityUuid(security.getUUID());
        request.setSecurityAccountUuid(portfolio.getUUID());
        request.setAccountUuid(account.getUUID());
        request.setDateTime(LocalDateTime.of(2026, 5, 30, 12, 0));
        request.setShares(10d);
        request.setAmount(1000d);

        var pair = TransactionManagementService.createTransaction(client, request);
        TransactionManagementService.deleteTransaction(client, pair.getTransaction().getUUID());

        assertThat(account.getTransactions().isEmpty(), is(true));
        assertThat(portfolio.getTransactions().isEmpty(), is(true));
    }
}
