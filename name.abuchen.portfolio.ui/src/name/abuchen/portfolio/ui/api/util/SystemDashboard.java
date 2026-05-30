package name.abuchen.portfolio.ui.api.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import name.abuchen.portfolio.model.Dashboard;
import name.abuchen.portfolio.ui.api.dto.DashboardDto;

/**
 * Hard-coded "Main" dashboard served for every portfolio via the web API.
 * Portfolio-specific dashboard configurations are ignored for listing and lookup.
 */
public final class SystemDashboard
{
    public static final String ID = "__main__"; //$NON-NLS-1$
    public static final String NAME = "Main"; //$NON-NLS-1$

    private static final Dashboard DASHBOARD = buildDashboard();

    private SystemDashboard()
    {
    }

    public static boolean isSystemDashboard(String dashboardId)
    {
        return ID.equals(dashboardId);
    }

    public static Dashboard getDashboard()
    {
        return DASHBOARD;
    }

    public static DashboardDto toDto()
    {
        return DashboardConverter.toDto(DASHBOARD);
    }

    private static Dashboard buildDashboard()
    {
        Dashboard dashboard = new Dashboard(ID);
        dashboard.setName(NAME);

        List<Dashboard.Column> columns = new ArrayList<>();

        columns.add(buildColumn(
            widget("CALCULATION", "Performance Calculation, Entire portfolio", //$NON-NLS-1$ //$NON-NLS-2$
                Map.of("DATA_SERIES", "Client-totals", "LAYOUT", "FULL")), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            widget("VERTICAL_SPACEER", "Vertical Spacer"), //$NON-NLS-1$ //$NON-NLS-2$
            widget("TOTAL_SUM", "Total"), //$NON-NLS-1$ //$NON-NLS-2$
            widget("TTWROR_ANNUALIZED", "True Time-Weighted Rate of Return (annualized)"), //$NON-NLS-1$ //$NON-NLS-2$
            widget("DELTA", "Delta (for reporting period)"), //$NON-NLS-1$ //$NON-NLS-2$
            widget("DELTA", "Delta (1 Day)", Map.of("REPORTING_PERIOD", "D1")) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        ));

        columns.add(buildColumn(
            widget("CHART", "Performance vs S&P500", //$NON-NLS-1$ //$NON-NLS-2$
                Map.of("HEIGHT", "420", "SHOW_Y_AXIS", "true")), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            widget("TAXONOMY_CHART", "Asset Class", //$NON-NLS-1$ //$NON-NLS-2$
                Map.of("FLAG_INCLUDE_SECURITIES", "false", "TAXONOMY_NAME", "Asset")), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            widget("TAXONOMY_CHART", "Currency", //$NON-NLS-1$ //$NON-NLS-2$
                Map.of("FLAG_INCLUDE_SECURITIES", "false", "TAXONOMY_NAME", "Currency")) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        ));

        columns.add(buildColumn(
            widget("ASSET_CHART", "Statement of Assets - Chart"), //$NON-NLS-1$ //$NON-NLS-2$
            widget("REBALANCING_CHART", "Actual vs. Target Allocation"), //$NON-NLS-1$ //$NON-NLS-2$
            widget("ASSET_CHART", "Earnings", //$NON-NLS-1$ //$NON-NLS-2$
                Map.of("DATA_SERIES", "Client-earnings_accumulated")) //$NON-NLS-1$ //$NON-NLS-2$
        ));

        dashboard.setColumns(columns);
        return dashboard;
    }

    private static Dashboard.Column buildColumn(Dashboard.Widget... widgets)
    {
        Dashboard.Column column = new Dashboard.Column();
        column.setWeight(1);
        column.setWidgets(List.of(widgets));
        return column;
    }

    private static Dashboard.Widget widget(String type, String label)
    {
        return widget(type, label, Map.of());
    }

    private static Dashboard.Widget widget(String type, String label, Map<String, String> configuration)
    {
        Dashboard.Widget widget = new Dashboard.Widget();
        widget.setType(type);
        widget.setLabel(label);
        widget.getConfiguration().putAll(new HashMap<>(configuration));
        return widget;
    }
}
