package name.abuchen.portfolio.snapshot.security;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import java.time.LocalDate;

import org.junit.Test;

import name.abuchen.portfolio.junit.PortfolioBuilder;
import name.abuchen.portfolio.junit.AccountBuilder;
import name.abuchen.portfolio.junit.SecurityBuilder;
import name.abuchen.portfolio.junit.TestCurrencyConverter;
import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.money.CurrencyUnit;
import name.abuchen.portfolio.money.Money;
import name.abuchen.portfolio.money.Quote;
import name.abuchen.portfolio.money.Values;
import name.abuchen.portfolio.snapshot.ClientSnapshot;
import name.abuchen.portfolio.snapshot.SecurityPosition;
import name.abuchen.portfolio.util.Interval;

@SuppressWarnings("nls")
public class SecurityPerformanceSnapshotTest
{

    @Test
    public void testBigPurchases()
    {
        Client client = new Client();

        Security security = new SecurityBuilder().addTo(client);

        new PortfolioBuilder()
                        .buy(security, "2018-05-01", Values.Share.factorize(500000), Values.Amount.factorize(450000))
                        .sell(security, "2018-05-08", Values.Share.factorize(500000), Values.Amount.factorize(494500))
                        .addTo(client);

        final Interval interval = Interval.of(LocalDate.parse("2018-04-01"), LocalDate.parse("2018-06-01"));
        SecurityPerformanceSnapshot snapshot = SecurityPerformanceSnapshot.create(client, new TestCurrencyConverter(),
                        interval);

        new SecurityPerformanceSnapshotComparator(snapshot,
                        LazySecurityPerformanceSnapshot.create(client, new TestCurrencyConverter(), interval))
                                        .compare();

        assertThat(snapshot.getRecords(), hasSize(1));

        SecurityPerformanceRecord record = snapshot.getRecords().get(0);
        assertThat(record.getSecurity(), is(security));

        assertThat(record.getSharesHeld(), is(0L));

        assertThat(record.getFifoCost(), is(Money.of(CurrencyUnit.EUR, 0)));
        assertThat(record.getFifoCostPerSharesHeld(), is(Quote.of(CurrencyUnit.EUR, 0)));

        assertThat(record.getMovingAverageCost(), is(Money.of(CurrencyUnit.EUR, 0)));
        assertThat(record.getMovingAverageCostPerSharesHeld(), is(Quote.of(CurrencyUnit.EUR, 0)));
    }

    @Test
    public void testBigPurchases2()
    {
        Client client = new Client();

        Security security = new SecurityBuilder().addTo(client);

        new PortfolioBuilder()
                        .buy(security, "2018-05-01", Values.Share.factorize(500000), Values.Amount.factorize(450000))
                        .sell(security, "2018-05-08", Values.Share.factorize(1), Values.Amount.factorize(0.989))
                        .addTo(client);

        Interval reportingPeriod = Interval.of(LocalDate.parse("2018-04-01"), LocalDate.parse("2018-06-01"));
        SecurityPerformanceSnapshot snapshot = SecurityPerformanceSnapshot.create(client, new TestCurrencyConverter(),
                        reportingPeriod);

        new SecurityPerformanceSnapshotComparator(snapshot,
                        LazySecurityPerformanceSnapshot.create(client, new TestCurrencyConverter(), reportingPeriod))
                                        .compare();

        assertThat(snapshot.getRecords(), hasSize(1));

        SecurityPerformanceRecord record = snapshot.getRecords().get(0);
        assertThat(record.getSecurity(), is(security));

        assertThat(record.getSharesHeld(), is(Values.Share.factorize(499999)));

        assertThat(record.getFifoCost(), is(Money.of(CurrencyUnit.EUR, Values.Amount.factorize(450000 - 0.9))));
        assertThat(record.getFifoCostPerSharesHeld(), is(Quote.of(CurrencyUnit.EUR, Values.Quote.factorize(0.9))));

        assertThat(record.getMovingAverageCost(),
                        is(Money.of(CurrencyUnit.EUR, Values.Amount.factorize(450000 - 0.9))));
        assertThat(record.getMovingAverageCostPerSharesHeld(),
                        is(Quote.of(CurrencyUnit.EUR, Values.Quote.factorize(0.9))));

        SecurityPosition position = ClientSnapshot.create(client, new TestCurrencyConverter(), reportingPeriod.getEnd())
                        .getPositionsByVehicle().get(security).getPosition();

        assertThat(position.getShares(), is(record.getSharesHeld()));
        assertThat(position.calculateValue(), is(record.getMarketValue()));
    }

    @Test
    public void testHoldingsIndicatorsSkipRateOfReturn()
    {
        Client client = new Client();

        Security security = new SecurityBuilder().addTo(client);

        new PortfolioBuilder()
                        .buy(security, "2024-01-10", Values.Share.factorize(10), Values.Amount.factorize(1000))
                        .buy(security, "2024-06-01", Values.Share.factorize(5), Values.Amount.factorize(600))
                        .addTo(client);

        new AccountBuilder().dividend("2024-03-01", Values.Amount.factorize(25), security).addTo(client);

        Interval interval = Interval.of(LocalDate.parse("2024-01-01"), LocalDate.parse("2024-12-31"));
        SecurityPerformanceSnapshot fullSnapshot = SecurityPerformanceSnapshot.create(client, new TestCurrencyConverter(),
                        interval);
        SecurityPerformanceSnapshot holdingsSnapshot = SecurityPerformanceSnapshot.create(client,
                        new TestCurrencyConverter(), interval, SecurityPerformanceIndicator.Costs.class,
                        SecurityPerformanceIndicator.Dividends.class);

        SecurityPerformanceRecord fullRecord = fullSnapshot.getRecords().get(0);
        SecurityPerformanceRecord holdingsRecord = holdingsSnapshot.getRecords().get(0);

        assertThat(holdingsRecord.getSharesHeld(), is(fullRecord.getSharesHeld()));
        assertThat(holdingsRecord.getMarketValue(), is(fullRecord.getMarketValue()));
        assertThat(holdingsRecord.getCapitalGainsOnHoldings(), is(fullRecord.getCapitalGainsOnHoldings()));
        assertThat(holdingsRecord.getSumOfDividends(), is(fullRecord.getSumOfDividends()));
        assertThat(holdingsRecord.getTrueTimeWeightedRateOfReturn(), is(0d));
    }
}
