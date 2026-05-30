package name.abuchen.portfolio.ui.api.factory;

import java.util.Map;
import java.util.HashMap;
import java.util.function.BiFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.MessageFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.OptionalDouble;
import java.util.stream.LongStream;

import name.abuchen.portfolio.ui.api.data.ChartWidgetData;
import name.abuchen.portfolio.ui.api.data.IndicatorWidgetData;
import name.abuchen.portfolio.ui.api.data.PerformanceCalculationWidgetData;
import name.abuchen.portfolio.ui.api.data.RebalancingChartWidgetData;
import name.abuchen.portfolio.ui.api.data.TaxonomyChartWidgetData;
import name.abuchen.portfolio.ui.api.data.Widget;
import name.abuchen.portfolio.math.Risk.Drawdown;
import name.abuchen.portfolio.money.Money;
import name.abuchen.portfolio.money.Values;
import name.abuchen.portfolio.snapshot.PerformanceIndex;
import name.abuchen.portfolio.ui.views.dashboard.DashboardData;
import name.abuchen.portfolio.ui.views.dataseries.DataSeries;
import name.abuchen.portfolio.ui.views.dataseries.DataSeries.UseCase;
import name.abuchen.portfolio.ui.Messages;
import name.abuchen.portfolio.model.ClientProperties;
import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.math.AllTimeHigh;
import name.abuchen.portfolio.ui.views.dashboard.RiskFreeRateOfReturnConfig;
import name.abuchen.portfolio.math.Risk.Volatility;

/**
 * Factory for generating widget data without UI components.
 * 
 * This factory provides data-only implementations of all widgets
 * defined in the UI WidgetFactory, allowing the API to serve
 * widget data without UI dependencies.
 */
public enum WidgetDataFactory {
    
    HEADING(Messages.LabelHeading, Messages.LabelCommon, (widget, data) -> createEmptyWidgetData(widget.getId(), "heading")),
    
    DESCRIPTION(Messages.LabelDescription, Messages.LabelCommon, (widget, data) -> createEmptyWidgetData(widget.getId(), "description")),
    
    TOTAL_SUM(Messages.LabelTotalSum, Messages.LabelStatementOfAssets, //
                    (widget, data) -> IndicatorWidgetData.<Money>create(widget, data) //
                                    .with(Values.Money) //
                                    .with((ds, period) -> {
                                        PerformanceIndex index = data.calculate(ds, period);
                                        int length = index.getTotals().length;
                                        return Money.of(index.getCurrency(), index.getTotals()[length - 1]);
                                    }) //
                                    .withBenchmarkDataSeries(false) //
                                    .build()),
    
    TTWROR(Messages.LabelTTWROR, Messages.ClientEditorLabelPerformance, // cumulative
                    (widget, data) -> IndicatorWidgetData.<Double>create(widget, data) //
                                    .with(Values.Percent2) //
                                    .with((ds, period) -> {
                                        PerformanceIndex index = data.calculate(ds, period);
                                        return index.getFinalAccumulatedPercentage();
                                    }).build()),
    
    TTWROR_ANNUALIZED(Messages.LabelTTWROR_Annualized, Messages.ClientEditorLabelPerformance, //
                    (widget, data) -> IndicatorWidgetData.<Double>create(widget, data) //
                                    .with(Values.AnnualizedPercent2) //
                                    .with((ds, period) -> {
                                        PerformanceIndex index = data.calculate(ds, period);
                                        return index.getFinalAccumulatedAnnualizedPercentage();
                                    }).build()),
    
    IRR(Messages.LabelIRR, Messages.ClientEditorLabelPerformance, //
                    (widget, data) -> IndicatorWidgetData.<Double>create(widget, data) //
                                    .with(Values.AnnualizedPercent2) //
                                    .with((ds, period) -> data.calculate(ds, period).getPerformanceIRR()) //
                                    .build()),
    
    ABSOLUTE_CHANGE(Messages.LabelAbsoluteChange, Messages.LabelStatementOfAssets, //
                    (widget, data) -> IndicatorWidgetData.<Money>create(widget, data) //
                                    .with(Values.Money) //
                                    .with((ds, period) -> {
                                        PerformanceIndex index = data.calculate(ds, period);
                                        int length = index.getTotals().length;
                                        return Money.of(index.getCurrency(),
                                                        index.getTotals()[length - 1] - index.getTotals()[0]);
                                    }) //
                                    .withBenchmarkDataSeries(false) //
                                    .build()),
    
    DELTA(Messages.LabelDelta, Messages.LabelStatementOfAssets, //
                    (widget, data) -> IndicatorWidgetData.<Money>create(widget, data) //
                                    .with(Values.Money) //
                                    .with((ds, period) -> {
                                        long[] d = data.calculate(ds, period).calculateDelta();
                                        return Money.of(data.getTermCurrency(), d.length > 0 ? d[d.length - 1] : 0L);
                                    }) //
                                    .withBenchmarkDataSeries(false) //
                                    .build()),
    
    ABSOLUTE_DELTA(Messages.LabelAbsoluteDelta, Messages.LabelStatementOfAssets, //
                    (widget, data) -> IndicatorWidgetData.<Money>create(widget, data) //
                                    .with(Values.Money) //
                                    .with((ds, period) -> {
                                        long[] d = data.calculate(ds, period).calculateAbsoluteDelta();
                                        return Money.of(data.getTermCurrency(), d.length > 0 ? d[d.length - 1] : 0L);
                                    }) //
                                    .withBenchmarkDataSeries(false) //
                                    .build()),
    
    SAVINGS(Messages.LabelPNTransfers, Messages.LabelStatementOfAssets, //
                    (widget, data) -> IndicatorWidgetData.<Money>create(widget, data) //
                                    .with(Values.Money) //
                                    .with((ds, period) -> {
                                        long[] d = data.calculate(ds, period).getTransferals();
                                        // skip d[0] because it refers to the
                                        // day before start
                                        return Money.of(data.getTermCurrency(),
                                                        d.length > 1 ? LongStream.of(d).skip(1).sum() : 0L);
                                    }) //
                                    .withBenchmarkDataSeries(false) //
                                    .build()),
    
    INVESTED_CAPITAL(Messages.LabelInvestedCapital, Messages.LabelStatementOfAssets, //
                    (widget, data) -> IndicatorWidgetData.<Money>create(widget, data) //
                                    .with(Values.Money) //
                                    .with((ds, period) -> {
                                        long[] d = data.calculate(ds, period).calculateInvestedCapital();
                                        return Money.of(data.getTermCurrency(), d.length > 0 ? d[d.length - 1] : 0L);
                                    }) //
                                    .withBenchmarkDataSeries(false) //
                                    .build()),
    
    ABSOLUTE_INVESTED_CAPITAL(Messages.LabelAbsoluteInvestedCapital, Messages.LabelStatementOfAssets, //
                    (widget, data) -> IndicatorWidgetData.<Money>create(widget, data) //
                                    .with(Values.Money) //
                                    .with((ds, period) -> {
                                        long[] d = data.calculate(ds, period).calculateAbsoluteInvestedCapital();
                                        return Money.of(data.getTermCurrency(), d.length > 0 ? d[d.length - 1] : 0L);
                                    }) //
                                    .withBenchmarkDataSeries(false) //
                                    .build()),
    
    RATIO(Messages.LabelRatio, Messages.LabelStatementOfAssets, (widget, data) -> createEmptyWidgetData(widget.getId(), "ratio")),
    
    MAXDRAWDOWN(Messages.LabelMaxDrawdown, Messages.LabelRiskIndicators, //
                    (widget, data) -> IndicatorWidgetData.<Double>create(widget, data) //
                                    .with(Values.Percent2) //
                                    .with((ds, period) -> {
                                        PerformanceIndex index = data.calculate(ds, period);
                                        return index.getDrawdown().getMaxDrawdown();
                                    }) //
                                    .withTooltip((ds, period) -> {
                                        DateTimeFormatter formatter = DateTimeFormatter
                                                        .ofLocalizedDate(FormatStyle.LONG)
                                                        .withZone(ZoneId.systemDefault());
                                        PerformanceIndex index = data.calculate(ds, period);
                                        Drawdown drawdown = index.getDrawdown();
                                        return MessageFormat.format(Messages.TooltipMaxDrawdown,
                                                        formatter.format(
                                                                        drawdown.getIntervalOfMaxDrawdown().getStart()),
                                                        formatter.format(drawdown.getIntervalOfMaxDrawdown().getEnd()));
                                    }) //
                                    .withColoredValues(false) //
                                    .build()),
    
    CURRENT_DRAWDOWN(Messages.LabelCurrentDrawdown, Messages.LabelRiskIndicators, //
                    (widget, data) -> IndicatorWidgetData.<Double>create(widget, data) //
                                    .with(Values.Percent2) //
                                    .with((ds, period) -> {
                                        PerformanceIndex index = data.calculate(ds, period);
                                        double[] accumulated = index.getAccumulatedPercentage();
                                        if (accumulated.length == 0)
                                            return 0.0;

                                        // Find the high watermark in
                                        // accumulated performance
                                        double highWatermark = Double.NEGATIVE_INFINITY;
                                        for (int i = 0; i < accumulated.length; i++)
                                        {
                                            if (accumulated[i] > highWatermark)
                                                highWatermark = accumulated[i];
                                        }

                                        // If no positive performance, no
                                        // drawdown
                                        if (highWatermark <= 0)
                                            return 0.0;

                                        // Current accumulated performance
                                        double currentValue = accumulated[accumulated.length - 1];

                                        // Calculate drawdown as percentage from
                                        // high watermark
                                        // This matches how the Drawdown class
                                        // calculates drawdown in Risk.java
                                        // Only return a drawdown if current
                                        // value is less than high watermark
                                        if (currentValue < highWatermark)
                                        {
                                            // Convert accumulated percentages
                                            // to values relative to 1
                                            // e.g., 0.25 (25% gain) becomes
                                            // 1.25
                                            double peakValue = 1 + highWatermark;
                                            double currentVal = 1 + currentValue;

                                            // Calculate drawdown using the same
                                            // formula as in Risk.Drawdown
                                            return -(peakValue - currentVal) / peakValue;
                                        }
                                        else
                                        {
                                            return 0.0;
                                        }
                                    }) //
                                    .withTooltip((ds, period) -> {
                                        PerformanceIndex index = data.calculate(ds, period);
                                        double[] accumulated = index.getAccumulatedPercentage();
                                        if (accumulated.length == 0)
                                            return Messages.TooltipCurrentDrawdown;

                                        // Find the high watermark date
                                        double highWatermark = Double.NEGATIVE_INFINITY;
                                        int highWatermarkIndex = 0;
                                        for (int i = 0; i < accumulated.length; i++)
                                        {
                                            if (accumulated[i] > highWatermark)
                                            {
                                                highWatermark = accumulated[i];
                                                highWatermarkIndex = i;
                                            }
                                        }

                                        DateTimeFormatter formatter = DateTimeFormatter
                                                        .ofLocalizedDate(FormatStyle.LONG)
                                                        .withZone(ZoneId.systemDefault());

                                        LocalDate highWatermarkDate = period.getStart().plusDays(highWatermarkIndex);
                                        return MessageFormat.format(Messages.TooltipCurrentDrawdown,
                                                        formatter.format(highWatermarkDate));
                                    }) //
                                    .withColoredValues(true) //
                                    .build()),
    
    MAXDRAWDOWNDURATION(Messages.LabelMaxDrawdownDuration, Messages.LabelRiskIndicators, (widget, data) -> createEmptyWidgetData(widget.getId(), "max_drawdown_duration")),
    
    DRAWDOWN_CHART(Messages.LabelMaxDrawdownChart, Messages.LabelRiskIndicators, (widget, data) -> createEmptyWidgetData(widget.getId(), "drawdown_chart")),
    
    VOLATILITY(Messages.LabelVolatility, Messages.LabelRiskIndicators, //
                    (widget, data) -> IndicatorWidgetData.<Double>create(widget, data) //
                                    .with(Values.Percent2) //
                                    .with((ds, period) -> {
                                        PerformanceIndex index = data.calculate(ds, period);
                                        return index.getVolatility().getStandardDeviation();
                                    }) //
                                    .withTooltip((ds, period) -> Messages.TooltipVolatility) //
                                    .withColoredValues(false) //
                                    .build()),
    
    SHARPE_RATIO(Messages.LabelSharpeRatio, Messages.LabelRiskIndicators, //
                    (widget, data) -> IndicatorWidgetData.<Double>create(widget, data) //
                                    .with(Values.PercentPlain) //
                                    .withColoredValues(false) //
                                    .with((ds, period) -> {
                                        PerformanceIndex index = data.calculate(ds, period);
                                        double r = index.getPerformanceIRR();
                                        double rf = new ClientProperties(data.getClient()).getRiskFreeRateOfReturn();
                                        double volatility = index.getVolatility().getStandardDeviation();

                                        // handle invalid rf value
                                        if (Double.isNaN(rf))
                                            return Double.NaN;

                                        double excessReturn = r - rf;
                                        return excessReturn / volatility;
                                    }) //
                                    .withTooltip((ds, period) -> {
                                        PerformanceIndex index = data.calculate(ds, period);
                                        double r = index.getPerformanceIRR();
                                        double rf = new ClientProperties(data.getClient()).getRiskFreeRateOfReturn();
                                        double volatility = index.getVolatility().getStandardDeviation();
                                        double sharpeRatio = (r - rf) / volatility;
                                        return MessageFormat.format(Messages.TooltipSharpeRatio,
                                                        Values.Percent5.format(r), Values.Percent2.format(rf),
                                                        volatility, sharpeRatio);
                                    }) //
                                    .build()),
    
    SEMIVOLATILITY(Messages.LabelSemiVolatility, Messages.LabelRiskIndicators, //
                    (widget, data) -> IndicatorWidgetData.<Double>create(widget, data) //
                                    .with(Values.Percent2) //
                                    .with((ds, period) -> {
                                        PerformanceIndex index = data.calculate(ds, period);
                                        return index.getVolatility().getSemiDeviation();
                                    }) //
                                    .withTooltip((ds, period) -> {
                                        PerformanceIndex index = data.calculate(ds, period);
                                        Volatility vola = index.getVolatility();
                                        return MessageFormat.format(Messages.TooltipSemiVolatility,
                                                        Values.Percent5.format(vola.getExpectedSemiDeviation()),
                                                        vola.getNormalizedSemiDeviationComparison(),
                                                        Values.Percent5.format(vola.getStandardDeviation()),
                                                        Values.Percent5.format(vola.getSemiDeviation()));
                                    }) //
                                    .withColoredValues(false) //
                                    .build()),
    
    CALCULATION(Messages.LabelPerformanceCalculation, Messages.ClientEditorLabelPerformance, 
                    (widget, data) -> new PerformanceCalculationWidgetData(widget, data)),
    
    CHART(Messages.LabelPerformanceChart, Messages.ClientEditorLabelPerformance, 
                    (widget, data) -> new ChartWidgetData(widget, data, UseCase.PERFORMANCE)),
    
    ASSET_CHART(Messages.LabelAssetChart, Messages.LabelStatementOfAssets, 
                    (widget, data) -> new ChartWidgetData(widget, data, UseCase.STATEMENT_OF_ASSETS)),
    
    HOLDINGS_CHART(Messages.LabelStatementOfAssetsHoldings, Messages.LabelStatementOfAssets, (widget, data) -> createEmptyWidgetData(widget.getId(), "holdings_chart")),
    
    CLIENT_DATA_SERIES_CHART(Messages.LabelStatementOfAssetsDerivedDataSeries, Messages.LabelStatementOfAssets, (widget, data) -> createEmptyWidgetData(widget.getId(), "client_data_series_chart")),
    
    TAXONOMY_CHART(Messages.LabelTaxonomies, Messages.LabelStatementOfAssets, 
                    (widget, data) -> new TaxonomyChartWidgetData(widget, data)),
    
    HEATMAP(Messages.LabelHeatmap, Messages.ClientEditorLabelPerformance, (widget, data) -> createEmptyWidgetData(widget.getId(), "heatmap")),
    
    HEATMAP_YEARLY(Messages.LabelYearlyHeatmap, Messages.ClientEditorLabelPerformance, (widget, data) -> createEmptyWidgetData(widget.getId(), "heatmap_yearly")),
    
    EARNINGS(Messages.LabelAccumulatedEarnings, Messages.LabelEarnings, (widget, data) -> createEmptyWidgetData(widget.getId(), "earnings")),
    
    HEATMAP_EARNINGS(Messages.LabelHeatmapEarnings, Messages.LabelEarnings, (widget, data) -> createEmptyWidgetData(widget.getId(), "heatmap_earnings")),
    
    EARNINGS_PER_YEAR_CHART(Messages.LabelEarningsPerYear, Messages.LabelEarnings, (widget, data) -> createEmptyWidgetData(widget.getId(), "earnings_per_year_chart")),
    
    EARNINGS_PER_QUARTER_CHART(Messages.LabelEarningsPerQuarter, Messages.LabelEarnings, (widget, data) -> createEmptyWidgetData(widget.getId(), "earnings_per_quarter_chart")),
    
    EARNINGS_PER_MONTH_CHART(Messages.LabelEarningsPerMonth, Messages.LabelEarnings, (widget, data) -> createEmptyWidgetData(widget.getId(), "earnings_per_month_chart")),
    
    EARNINGS_BY_TAXONOMY(Messages.LabelEarningsByTaxonomy, Messages.LabelEarnings, (widget, data) -> createEmptyWidgetData(widget.getId(), "earnings_by_taxonomy")),
    
    TRADES_BASIC_STATISTICS(Messages.LabelTradesBasicStatistics, Messages.LabelTrades, (widget, data) -> createEmptyWidgetData(widget.getId(), "trades_basic_statistics")),
    
    TRADES_PROFIT_LOSS(Messages.LabelTradesProfitLoss, Messages.LabelTrades, (widget, data) -> createEmptyWidgetData(widget.getId(), "trades_profit_loss")),
    
    TRADES_AVERAGE_HOLDING_PERIOD(Messages.TooltipAverageHoldingPeriod, Messages.LabelTrades, (widget, data) -> createEmptyWidgetData(widget.getId(), "trades_average_holding_period")),
    
    TRADES_TURNOVER_RATIO(Messages.LabelTradesTurnoverRate, Messages.LabelTrades, //
                    (widget, data) -> IndicatorWidgetData.<Double>create(widget, data) //
                                    .with(Values.Percent2) //
                                    .with((ds, period) -> {
                                        PerformanceIndex index = data.calculate(ds, period);
                                        OptionalDouble average = LongStream.of(index.getTotals()).average();
                                        if (!average.isPresent() || average.getAsDouble() <= 0)
                                            return 0.0;
                                        long buy = LongStream.of(index.getBuys()).sum();
                                        long sell = LongStream.of(index.getSells()).sum();
                                        return Long.min(buy, sell) / average.getAsDouble();
                                    }) //
                                    .withBenchmarkDataSeries(false) //
                                    .withTooltip((ds, period) -> {
                                        PerformanceIndex index = data.calculate(ds, period);
                                        String currency = data.getCurrencyConverter().getTermCurrency();
                                        OptionalDouble average = LongStream.of(index.getTotals()).average();
                                        long buy = LongStream.of(index.getBuys()).sum();
                                        long sell = LongStream.of(index.getSells()).sum();
                                        return MessageFormat.format(Messages.TooltipTurnoverRate,
                                                        Values.Money.format(Money.of(currency, buy)),
                                                        Values.Money.format(Money.of(currency, sell)),
                                                        Values.Money.format(
                                                                        Money.of(currency, (long) average.orElse(0))),
                                                        Values.Percent2.format(
                                                                        average.isPresent() && average.getAsDouble() > 0
                                                                                        ? Long.min(buy, sell) / average
                                                                                                        .getAsDouble()
                                                                                        : 0));
                                    }) //
                                    .withColoredValues(false).build()),
    
    HEATMAP_INVESTMENTS(Messages.LabelHeatmapInvestments, Messages.LabelTrades, (widget, data) -> createEmptyWidgetData(widget.getId(), "heatmap_investments")),
    
    HEATMAP_TAXES(Messages.LabelHeatmapTaxes, Messages.LabelTrades, (widget, data) -> createEmptyWidgetData(widget.getId(), "heatmap_taxes")),
    
    HEATMAP_FEES(Messages.LabelHeatmapFees, Messages.LabelTrades, (widget, data) -> createEmptyWidgetData(widget.getId(), "heatmap_fees")),
    
    PORTFOLIO_TAX_RATE(Messages.TooltipPortfolioTaxRate, Messages.ClientEditorLabelPerformance, (widget, data) -> createEmptyWidgetData(widget.getId(), "portfolio_tax_rate")),
    
    PORTFOLIO_FEE_RATE(Messages.TooltipPortfolioFeeRate, Messages.ClientEditorLabelPerformance, (widget, data) -> createEmptyWidgetData(widget.getId(), "portfolio_fee_rate")),
    
    CURRENT_DATE(Messages.LabelToday, Messages.LabelCommon, (widget, data) -> createEmptyWidgetData(widget.getId(), "current_date")),
    
    EXCHANGE_RATE(Messages.LabelExchangeRate, Messages.LabelCommon, (widget, data) -> createEmptyWidgetData(widget.getId(), "exchange_rate")),
    
    ACTIVITY_CHART(Messages.LabelTradingActivityChart, Messages.LabelCommon, (widget, data) -> createEmptyWidgetData(widget.getId(), "activity_chart")),
    
    LIMIT_EXCEEDED(Messages.SecurityListFilterLimitPriceExceeded, Messages.LabelCommon, (widget, data) -> createEmptyWidgetData(widget.getId(), "limit_exceeded")),
    
    FOLLOW_UP(Messages.SecurityListFilterDateReached, Messages.LabelCommon, (widget, data) -> createEmptyWidgetData(widget.getId(), "follow_up")),
    
    EVENT_LIST(Messages.EventListWidgetTitle, Messages.LabelCommon, (widget, data) -> createEmptyWidgetData(widget.getId(), "event_list")),
    
    LATEST_SECURITY_PRICE(Messages.LabelSecurityLatestPrice, Messages.LabelCommon, //
                    (widget, data) -> IndicatorWidgetData.<Long>create(widget, data) //
                                    .with(Values.Quote) //
                                    .with((ds, period) -> {
                                        if (!(ds.getInstance() instanceof Security))
                                            return 0L;

                                        Security security = (Security) ds.getInstance();
                                        return security.getSecurityPrice(LocalDate.now()).getValue();
                                    }) //
                                    .withBenchmarkDataSeries(false) //
                                    .with(ds -> ds.getInstance() instanceof Security) //
                                    .withColoredValues(false) //
                                    .withTooltip((ds, period) -> {
                                        if (!(ds.getInstance() instanceof Security))
                                            return ""; //$NON-NLS-1$

                                        Security security = (Security) ds.getInstance();

                                        return MessageFormat.format(Messages.TooltipSecurityLatestPrice,
                                                        security.getName(), Values.Date.format(security
                                                                        .getSecurityPrice(LocalDate.now()).getDate()));
                                    }) //
                                    .build()),
    
    DISTANCE_TO_ATH(Messages.SecurityListFilterDistanceFromAth, Messages.LabelCommon, //
                    (widget, data) -> IndicatorWidgetData.<Double>create(widget, data) //
                                    .with(Values.Percent2) //
                                    .with((ds, period) -> {
                                        if (!(ds.getInstance() instanceof Security))
                                            return (double) 0;

                                        Security security = (Security) ds.getInstance();

                                        Double distance = new AllTimeHigh(security, period).getDistance();
                                        if (distance == null)
                                            return (double) 0;

                                        return distance;
                                    }) //
                                    .withBenchmarkDataSeries(false) //
                                    .with(ds -> ds.getInstance() instanceof Security) //
                                    .withColoredValues(false) //
                                    .withTooltip((ds, period) -> {
                                        if (!(ds.getInstance() instanceof Security))
                                            return null;

                                        Security security = (Security) ds.getInstance();
                                        AllTimeHigh ath = new AllTimeHigh(security, period);
                                        if (ath.getValue() == null)
                                            return null;

                                        return MessageFormat.format(Messages.TooltipAllTimeHigh, period.getDays(),
                                                        Values.Date.format(ath.getDate()),
                                                        ath.getValue() / Values.Quote.divider(),
                                                        security.getSecurityPrice(LocalDate.now()).getValue()
                                                                        / Values.Quote.divider(),
                                                        Values.Date.format(security.getSecurityPrice(LocalDate.now())
                                                                        .getDate()));
                                    }) //
                                    .build()),
    
    WEBSITE(Messages.Website, Messages.LabelCommon, (widget, data) -> createEmptyWidgetData(widget.getId(), "website")),
    
    REBALANCING_TARGET_CHART(MessageFormat.format(Messages.LabelColonSeparated, Messages.LabelTaxonomies,
                                    Messages.ColumnTargetValue), Messages.LabelStatementOfAssets, (widget, data) -> createEmptyWidgetData(widget.getId(), "rebalancing_target_chart")),
    
    REBALANCING_CHART(Messages.RebalancingChartActualVsTarget, Messages.LabelStatementOfAssets, 
                    (widget, data) -> new RebalancingChartWidgetData(widget, data)),
    
    VERTICAL_SPACEER(Messages.LabelVerticalSpacer, Messages.LabelCommon, (widget, data) -> createEmptyWidgetData(widget.getId(), "vertical_spacer")),
    
    ALL_TIME_HIGH(Messages.LabelAllTimeHigh, Messages.LabelStatementOfAssets, //
                    (widget, data) -> IndicatorWidgetData.<Money>create(widget, data) //
                                    .with(Values.Money) //
                                    .with((ds, period) -> {
                                        PerformanceIndex index = data.calculate(ds, period);
                                        long[] totals = index.getTotals();
                                        long maxValue = 0;
                                        for (long value : totals)
                                        {
                                            maxValue = Math.max(maxValue, value);
                                        }
                                        return Money.of(index.getCurrency(), maxValue);
                                    }) //
                                    .withBenchmarkDataSeries(false) //
                                    .withTooltip((ds, period) -> {
                                        PerformanceIndex index = data.calculate(ds, period);
                                        long[] totals = index.getTotals();
                                        long maxValue = 0;
                                        int maxIndex = 0;

                                        for (int i = 0; i < totals.length; i++)
                                        {
                                            if (totals[i] > maxValue)
                                            {
                                                maxValue = totals[i];
                                                maxIndex = i;
                                            }
                                        }

                                        DateTimeFormatter formatter = DateTimeFormatter
                                                        .ofLocalizedDate(FormatStyle.LONG)
                                                        .withZone(ZoneId.systemDefault());

                                        LocalDate date = period.getStart().plusDays(maxIndex);
                                        return MessageFormat.format(Messages.TooltipAllTimeHighWidget,
                                                        formatter.format(date));
                                    }) //
                                    .build());
    
    private static final Logger logger = LoggerFactory.getLogger(WidgetDataFactory.class);
    
    private final String label;
    private final String category;
    private final BiFunction<Widget, DashboardData, Object> dataGenerator;
    
    WidgetDataFactory(String label, String category, BiFunction<Widget, DashboardData, Object> dataGenerator) {
        this.label = label;
        this.category = category;
        this.dataGenerator = dataGenerator;
    }
    
    /**
     * Get the label for this widget type.
     * 
     * @return The widget label
     */
    public String getLabel() {
        return label;
    }
    
    /**
     * Get the category for this widget type.
     * 
     * @return The widget category
     */
    public String getCategory() {
        return category;
    }
    
    /**
     * Generate widget data for the given widget and dashboard data.
     * 
     * @param widget The widget
     * @param data The dashboard data
     * @return Widget data as a Map
     */
    public Map<String, Object> generateData(Widget widget, DashboardData data) {
        logger.debug("Generating data for widget type: {} with ID: {}", this.name(), widget.getId());
        Object result = dataGenerator.apply(widget, data);
        
        // If the result is an IndicatorWidgetData, call generateData() on it
        if (result instanceof IndicatorWidgetData) {
            return ((IndicatorWidgetData<?>) result).generateData();
        }
        
        // If the result is a PerformanceCalculationWidgetData, call generateData() on it
        if (result instanceof PerformanceCalculationWidgetData) {
            return ((PerformanceCalculationWidgetData) result).generateData();
        }
        
        // If the result is a ChartWidgetData, call generateData() on it
        if (result instanceof ChartWidgetData) {
            return ((ChartWidgetData) result).generateData();
        }
        
        // If the result is a TaxonomyChartWidgetData, call generateData() on it
        if (result instanceof TaxonomyChartWidgetData) {
            return ((TaxonomyChartWidgetData) result).generateData();
        }
        
        // If the result is a RebalancingChartWidgetData, call generateData() on it
        if (result instanceof RebalancingChartWidgetData) {
            return ((RebalancingChartWidgetData) result).generateData();
        }
        
        // Otherwise, assume it's already a Map
        return (Map<String, Object>) result;
    }
    
    /**
     * Find a widget factory by name (case-insensitive).
     * 
     * @param name The widget name
     * @return The widget factory or null if not found
     */
    public static WidgetDataFactory findByName(String name) {
        if (name == null) {
            return null;
        }
        
        for (WidgetDataFactory factory : values()) {
            if (factory.name().equalsIgnoreCase(name)) {
                return factory;
            }
        }
        return null;
    }
    
    /**
     * Create empty widget data with basic structure.
     * 
     * @param widgetId The widget identifier
     * @param type The widget type
     * @return Empty widget data
     */
    private static Map<String, Object> createEmptyWidgetData(String widgetId, String type) {
        Map<String, Object> data = new HashMap<>();
        data.put("widgetId", widgetId);
        data.put("type", type);
        data.put("data", new HashMap<>());
        data.put("message", "Widget data implementation pending");
        data.put("timestamp", java.time.LocalDateTime.now().toString());
        return data;
    }
    
}
