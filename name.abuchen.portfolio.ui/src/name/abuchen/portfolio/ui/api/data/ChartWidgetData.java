package name.abuchen.portfolio.ui.api.data;

import static name.abuchen.portfolio.util.ArraysUtil.accumulateAndToDouble;
import static name.abuchen.portfolio.util.ArraysUtil.add;
import static name.abuchen.portfolio.util.ArraysUtil.toDouble;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import name.abuchen.portfolio.model.ConfigurationSet;
import name.abuchen.portfolio.money.Values;
import name.abuchen.portfolio.snapshot.Aggregation;
import name.abuchen.portfolio.snapshot.PerformanceIndex;
import name.abuchen.portfolio.ui.Messages;
import name.abuchen.portfolio.ui.views.PerformanceChartView;
import name.abuchen.portfolio.ui.views.StatementOfAssetsHistoryView;
import name.abuchen.portfolio.ui.views.dashboard.DashboardData;
import name.abuchen.portfolio.ui.views.dataseries.BasicDataSeriesConfigurator;
import name.abuchen.portfolio.ui.views.dataseries.DataSeries;
import name.abuchen.portfolio.ui.views.dataseries.DataSeriesCache;
import name.abuchen.portfolio.ui.views.dataseries.DataSeriesSerializer;
import name.abuchen.portfolio.ui.views.dataseries.DataSeriesSet;
import name.abuchen.portfolio.ui.views.dataseries.DataSeries.ClientDataSeries;
import name.abuchen.portfolio.ui.views.dataseries.DerivedDataSeries;
import name.abuchen.portfolio.util.Interval;

/**
 * Data-only implementation of ChartWidget for API usage.
 * 
 * This class provides the same data calculation logic as ChartWidget but
 * generates only data output without UI components.
 */
public class ChartWidgetData {
    
    private static final Logger logger = LoggerFactory.getLogger(ChartWidgetData.class);
    
    private final String widgetId;
    private final DashboardData dashboardData;
    private final DataSeries.UseCase useCase;
    private final DataSeriesSet dataSeriesSet;
    private final Map<String, String> config;

    public ChartWidgetData(Widget widget, DashboardData dashboardData, DataSeries.UseCase useCase) {
        this.widgetId = widget.getId();
        this.dashboardData = dashboardData;
        this.useCase = useCase;
        this.config = widget.getConfiguration();
        this.dataSeriesSet = new DataSeriesSet(dashboardData.getClient(), dashboardData.getPreferences(), useCase);
    }

    /**
     * Generate widget data based on the configuration.
     * 
     * @return Map containing the widget data
     */
    public Map<String, Object> generateData() {
        try {
            logger.debug("Generating data for chart widget: {}, useCase: {}", widgetId, useCase);
            
            // Get chart configuration (null/empty falls back to default series in serializer)
            String chartConfigData = getChartConfigData();
            
            // Get reporting period
            Interval reportingPeriod = getReportingPeriodFromConfig();
            
            // Get aggregation (for performance charts only)
            Aggregation.Period aggregation = null;
            if (useCase == DataSeries.UseCase.PERFORMANCE) {
                aggregation = getAggregationFromConfig();
            }
            
            // Parse data series from configuration, using defaults when none is stored
            List<DataSeries> series = new DataSeriesSerializer().fromString(dataSeriesSet,
                            (chartConfigData == null || chartConfigData.isEmpty()) ? null : chartConfigData);

            if (series.isEmpty()) {
                logger.warn("No chart series available for widget: {}", widgetId);
                return createErrorResponse("No chart series available");
            }
            
            // Calculate data for each series
            List<Map<String, Object>> seriesData = new ArrayList<>();
            DataSeriesCache cache = dashboardData.getDataSeriesCache();
            
            for (DataSeries ds : series) {
                if (!ds.isVisible()) {
                    continue;
                }
                
                Map<String, Object> seriesInfo = buildSeriesData(ds, reportingPeriod, aggregation, cache);
                if (seriesInfo != null) {
                    seriesData.add(seriesInfo);
                }
            }
            
            // Create response
            Map<String, Object> response = new HashMap<>();
            response.put("widgetId", widgetId);
            response.put("type", "chart");
            response.put("useCase", useCase.name());
            response.put("reportingPeriod", formatReportingPeriod(reportingPeriod));
            if (aggregation != null) {
                response.put("aggregation", aggregation.name());
            }
            response.put("series", seriesData);
            response.put("timestamp", java.time.LocalDateTime.now().toString());
            
            return response;
            
        } catch (Exception e) {
            logger.error("Error generating data for widget: {}", widgetId, e);
            return createErrorResponse("Error generating widget data: " + e.getMessage());
        }
    }
    
    private Map<String, Object> buildSeriesData(DataSeries series, Interval reportingPeriod, 
                                                  Aggregation.Period aggregationPeriod, 
                                                  DataSeriesCache cache) {
        try {
            PerformanceIndex index = cache.lookup(series, reportingPeriod);
            
            Map<String, Object> seriesInfo = new HashMap<>();
            seriesInfo.put("uuid", series.getUUID());
            seriesInfo.put("label", series.getLabel());
            seriesInfo.put("type", series.getType().name());
            seriesInfo.put("isLineChart", series.isLineChart());
            seriesInfo.put("isBenchmark", series.isBenchmark());
            
            // Add color information
            if (series.getColor() != null) {
                seriesInfo.put("color", String.format("#%02x%02x%02x", 
                    series.getColor().red, series.getColor().green, series.getColor().blue));
            }
            
            // Calculate data points based on use case
            switch (useCase) {
                case PERFORMANCE:
                    buildPerformanceSeriesData(seriesInfo, series, index, aggregationPeriod);
                    break;
                case STATEMENT_OF_ASSETS:
                    buildAssetSeriesData(seriesInfo, series, index);
                    break;
                default:
                    logger.warn("Unsupported use case: {}", useCase);
                    return null;
            }
            
            return seriesInfo;
            
        } catch (Exception e) {
            logger.error("Error building series data for: {}", series.getLabel(), e);
            return null;
        }
    }
    
    private void buildPerformanceSeriesData(Map<String, Object> seriesInfo, DataSeries series, 
                                             PerformanceIndex index, Aggregation.Period aggregationPeriod) {
        PerformanceIndex workingIndex = index;
        
        if (series.getType() == DataSeries.Type.CLIENT) {
            // Handle client data series
            if (aggregationPeriod != null) {
                workingIndex = Aggregation.aggregate(index, aggregationPeriod);
            }
            
            ClientDataSeries clientSeries = (ClientDataSeries) series.getInstance();
            switch (clientSeries) {
                case TOTALS:
                    addDataPoints(seriesInfo, workingIndex.getDates(), workingIndex.getAccumulatedPercentage());
                    break;
                case DELTA_PERCENTAGE:
                    String label = aggregationPeriod != null ? aggregationPeriod.toString() : Messages.LabelAggregationDaily;
                    seriesInfo.put("label", label);
                    addDataPoints(seriesInfo, workingIndex.getDates(), workingIndex.getDeltaPercentage());
                    break;
                default:
                    logger.warn("Unsupported client data series: {}", clientSeries);
                    break;
            }
        } else {
            // Handle security benchmarks and other series
            if (aggregationPeriod != null) {
                workingIndex = Aggregation.aggregate(index, aggregationPeriod);
            }
            addDataPoints(seriesInfo, workingIndex.getDates(), workingIndex.getAccumulatedPercentage());
        }
    }
    
    private void buildAssetSeriesData(Map<String, Object> seriesInfo, DataSeries series, PerformanceIndex index) {
        if (series.getType() == DataSeries.Type.CLIENT || series.getType() == DataSeries.Type.DERIVED_DATA_SERIES) {
            // Handle client data series with various aspects
            double[] values = extractClientAssetValues(series, index);
            if (values != null) {
                addDataPoints(seriesInfo, index.getDates(), values);
            }
        } else {
            // Handle security and other series
            double[] values = toDouble(index.getTotals(), Values.Amount.divider());
            addDataPoints(seriesInfo, index.getDates(), values);
        }
    }
    
    private double[] extractClientAssetValues(DataSeries series, PerformanceIndex index) {
        ClientDataSeries aspect = series.getInstance() instanceof DerivedDataSeries derived 
                ? derived.getAspect() 
                : (ClientDataSeries) series.getInstance();
        
        switch (aspect) {
            case TOTALS:
                return toDouble(index.getTotals(), Values.Amount.divider());
            case TRANSFERALS:
                return toDouble(index.getTransferals(), Values.Amount.divider());
            case TRANSFERALS_ACCUMULATED:
                return accumulateAndToDouble(index.getTransferals(), Values.Amount.divider());
            case INVESTED_CAPITAL:
                return toDouble(index.calculateInvestedCapital(), Values.Amount.divider());
            case ABSOLUTE_INVESTED_CAPITAL:
                return toDouble(index.calculateAbsoluteInvestedCapital(), Values.Amount.divider());
            case ABSOLUTE_DELTA:
                return toDouble(index.calculateDelta(), Values.Amount.divider());
            case ABSOLUTE_DELTA_ALL_RECORDS:
                return toDouble(index.calculateAbsoluteDelta(), Values.Amount.divider());
            case TAXES:
                return toDouble(index.getTaxes(), Values.Amount.divider());
            case TAXES_ACCUMULATED:
                return accumulateAndToDouble(index.getTaxes(), Values.Amount.divider());
            case DIVIDENDS:
                return toDouble(index.getDividends(), Values.Amount.divider());
            case DIVIDENDS_ACCUMULATED:
                return accumulateAndToDouble(index.getDividends(), Values.Amount.divider());
            case INTEREST:
                return toDouble(index.getInterest(), Values.Amount.divider());
            case INTEREST_ACCUMULATED:
                return accumulateAndToDouble(index.getInterest(), Values.Amount.divider());
            case INTEREST_CHARGE:
                return toDouble(index.getInterestCharge(), Values.Amount.divider());
            case INTEREST_CHARGE_ACCUMULATED:
                return accumulateAndToDouble(index.getInterestCharge(), Values.Amount.divider());
            case EARNINGS:
                return toDouble(add(index.getDividends(), index.getInterest()), Values.Amount.divider());
            case EARNINGS_ACCUMULATED:
                return accumulateAndToDouble(add(index.getDividends(), index.getInterest()), Values.Amount.divider());
            case FEES:
                return toDouble(index.getFees(), Values.Amount.divider());
            case FEES_ACCUMULATED:
                return accumulateAndToDouble(index.getFees(), Values.Amount.divider());
            case CAPITAL_GAINS:
                return toDouble(index.getCapitalGains(), Values.Amount.divider());
            case CAPITAL_GAINS_ACCUMULATED:
                return accumulateAndToDouble(index.getCapitalGains(), Values.Amount.divider());
            case REALIZED_CAPITAL_GAINS:
                return toDouble(index.getRealizedCapitalGains(), Values.Amount.divider());
            case REALIZED_CAPITAL_GAINS_ACCUMULATED:
                return accumulateAndToDouble(index.getRealizedCapitalGains(), Values.Amount.divider());
            case UNREALIZED_CAPITAL_GAINS:
                return toDouble(index.getUnrealizedCapitalGains(), Values.Amount.divider());
            case UNREALIZED_CAPITAL_GAINS_ACCUMULATED:
                return accumulateAndToDouble(index.getUnrealizedCapitalGains(), Values.Amount.divider());
            default:
                logger.warn("Unsupported client data series aspect: {}", aspect);
                return null;
        }
    }
    
    private void addDataPoints(Map<String, Object> seriesInfo, LocalDate[] dates, double[] values) {
        List<Map<String, Object>> dataPoints = new ArrayList<>();
        
        for (int i = 0; i < Math.min(dates.length, values.length); i++) {
            Map<String, Object> point = new HashMap<>();
            point.put("date", dates[i].toString());
            point.put("value", values[i]);
            dataPoints.add(point);
        }
        
        seriesInfo.put("data", dataPoints);
        seriesInfo.put("dataPointCount", dataPoints.size());
    }
    
    private String getChartConfigData() {
        String directDataSeries = (config != null) ? config.get("DATA_SERIES") : null;
        if (directDataSeries != null && !directDataSeries.isEmpty()) {
            return directDataSeries;
        }
        
        String configName = (useCase == DataSeries.UseCase.STATEMENT_OF_ASSETS 
                ? StatementOfAssetsHistoryView.class 
                : PerformanceChartView.class).getSimpleName() + BasicDataSeriesConfigurator.IDENTIFIER_POSTFIX;
        
        ConfigurationSet configSet = dashboardData.getClient().getSettings().getConfigurationSet(configName);
        String uuid = (config != null) ? config.get("CONFIG_UUID") : null;
        
        ConfigurationSet.Configuration chartConfig = configSet.lookup(uuid)
                .orElseGet(() -> configSet.getConfigurations().findFirst()
                .orElse(null));
        
        return chartConfig != null ? chartConfig.getData() : null;
    }
    
    private Interval getReportingPeriodFromConfig() {
        String reportingPeriodCode = (config != null) ? config.get("REPORTING_PERIOD") : null;
        
        if (reportingPeriodCode != null && !reportingPeriodCode.isEmpty()) {
            try {
                name.abuchen.portfolio.snapshot.ReportingPeriod reportingPeriod = 
                    name.abuchen.portfolio.snapshot.ReportingPeriod.from(reportingPeriodCode);
                return reportingPeriod.toInterval(LocalDate.now());
            } catch (Exception e) {
                logger.warn("Failed to parse reporting period code: {}", reportingPeriodCode, e);
            }
        }
        
        if (dashboardData.getDefaultReportingPeriod() != null) {
            return dashboardData.getDefaultReportingPeriod().toInterval(LocalDate.now());
        }
        
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusYears(1);
        return Interval.of(start, end);
    }
    
    private Aggregation.Period getAggregationFromConfig() {
        String aggregationCode = (config != null) ? config.get("AGGREGATION") : null;
        
        if (aggregationCode != null && !aggregationCode.isEmpty()) {
            try {
                return Aggregation.Period.valueOf(aggregationCode);
            } catch (IllegalArgumentException e) {
                logger.warn("Failed to parse aggregation code: {}", aggregationCode, e);
            }
        }
        
        return null; // null means daily aggregation
    }
    
    private Map<String, String> formatReportingPeriod(Interval interval) {
        Map<String, String> period = new HashMap<>();
        period.put("start", interval.getStart().toString());
        period.put("end", interval.getEnd().toString());
        return period;
    }
    
    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("widgetId", widgetId);
        response.put("type", "chart");
        response.put("useCase", useCase.name());
        response.put("error", message);
        response.put("timestamp", java.time.LocalDateTime.now().toString());
        return response;
    }
}
