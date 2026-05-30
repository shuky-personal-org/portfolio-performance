package name.abuchen.portfolio.ui.api.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.Taxonomy;
import name.abuchen.portfolio.money.Money;
import name.abuchen.portfolio.money.Values;
import name.abuchen.portfolio.snapshot.filter.ClientFilter;
import name.abuchen.portfolio.ui.util.ClientFilterMenu;
import name.abuchen.portfolio.ui.views.dashboard.DashboardData;
import name.abuchen.portfolio.ui.views.taxonomy.DonutChartBuilder;
import name.abuchen.portfolio.ui.views.taxonomy.TaxonomyModel;
import name.abuchen.portfolio.ui.views.taxonomy.TaxonomyNode;
import name.abuchen.portfolio.util.ColorConversion;
import name.abuchen.portfolio.util.Pair;

/**
 * Data-only implementation of TaxonomyChartWidget for API usage.
 * 
 * This class provides the same data calculation logic as TaxonomyChartWidget but
 * generates only data output without UI components.
 */
public class TaxonomyChartWidgetData {
    
    private static final Logger logger = LoggerFactory.getLogger(TaxonomyChartWidgetData.class);
    
    private final String widgetId;
    private final DashboardData dashboardData;
    private final Map<String, String> config;

    public TaxonomyChartWidgetData(Widget widget, DashboardData dashboardData) {
        this.widgetId = widget.getId();
        this.dashboardData = dashboardData;
        this.config = widget.getConfiguration();
    }

    /**
     * Generate widget data based on the configuration.
     * 
     * @return Map containing the widget data
     */
    public Map<String, Object> generateData() {
        try {
            logger.debug("Generating data for taxonomy chart widget: {}", widgetId);
            
            // Get taxonomy from configuration
            Taxonomy taxonomy = getTaxonomyFromConfig();
            if (taxonomy == null) {
                logger.warn("No taxonomy found for widget: {}", widgetId);
                return createErrorResponse("No taxonomy configured or available");
            }
            
            // Create taxonomy model
            TaxonomyModel model = new TaxonomyModel(
                    dashboardData.getExchangeRateProviderFactory(),
                    dashboardData.getClient(),
                    taxonomy);
            
            // Apply client filter if applicable
            ClientFilter clientFilter = getClientFilterFromConfig();
            Client filteredClient = clientFilter.filter(dashboardData.getClient());
            if (filteredClient != dashboardData.getClient()) {
                model.updateClientSnapshot(filteredClient);
            }
            
            // Apply configuration flags
            boolean includeUnassigned = getIncludeUnassignedFromConfig();
            model.setExcludeUnassignedCategoryInCharts(!includeUnassigned);
            
            boolean includeSecurities = getIncludeSecuritiesFromConfig();
            model.setExcludeSecuritiesInPieChart(!includeSecurities);
            
            // Extract chart data using DonutChartBuilder logic
            DonutChartBuilder builder = new DonutChartBuilder();
            List<Pair<TaxonomyNode, TaxonomyNode>> nodeList = builder.computeNodeList(model);
            
            // Create response
            Map<String, Object> response = new HashMap<>();
            response.put("widgetId", widgetId);
            response.put("type", "taxonomyChart");
            response.put("taxonomyName", taxonomy.getName());
            response.put("taxonomyId", taxonomy.getId());
            response.put("includeUnassigned", includeUnassigned);
            response.put("includeSecurities", includeSecurities);
            response.put("clientFilter", getClientFilterLabel(clientFilter));
            
            // Convert node list to data format
            List<Map<String, Object>> slices = new ArrayList<>();
            double totalValue = 0.0;
            
            for (Pair<TaxonomyNode, TaxonomyNode> pair : nodeList) {
                TaxonomyNode parent = pair.getLeft();
                TaxonomyNode child = pair.getRight();
                
                Money actual = child.getActual();
                if (!actual.isZero()) {
                    Map<String, Object> slice = new HashMap<>();
                    
                    // Create unique ID
                    String id = parent.getId() + child.getId();
                    slice.put("id", id);
                    
                    // Add labels
                    slice.put("label", child.getName());
                    slice.put("parentLabel", parent.getName());
                    
                    // Add values
                    double value = actual.getAmount() / Values.Amount.divider();
                    slice.put("value", value);
                    slice.put("formattedValue", Values.Money.format(actual));
                    slice.put("currency", actual.getCurrencyCode());
                    totalValue += value;
                    
                    // Add color
                    String colorHex = parent.getColor();
                    if (colorHex != null) {
                        slice.put("color", colorHex);
                    }
                    
                    // Add classification info
                    slice.put("isAssignment", child.isAssignment());
                    slice.put("isClassification", child.isClassification());
                    
                    // Add security info if it's an assignment
                    if (child.isAssignment() && child.getBackingInvestmentVehicle() != null) {
                        Map<String, Object> vehicleInfo = new HashMap<>();
                        vehicleInfo.put("name", child.getBackingInvestmentVehicle().getName());
                        vehicleInfo.put("uuid", child.getBackingInvestmentVehicle().getUUID());
                        slice.put("investmentVehicle", vehicleInfo);
                    }
                    
                    slices.add(slice);
                }
            }
            
            // Add total value
            response.put("totalValue", totalValue);
            response.put("slices", slices);
            response.put("sliceCount", slices.size());
            
            // Calculate percentages
            if (totalValue > 0) {
                for (Map<String, Object> slice : slices) {
                    double value = (double) slice.get("value");
                    double percentage = (value / totalValue) * 100.0;
                    slice.put("percentage", percentage);
                    slice.put("formattedPercentage", Values.Percent2.format(percentage / 100.0));
                }
            }
            
            response.put("timestamp", java.time.LocalDateTime.now().toString());
            
            return response;
            
        } catch (Exception e) {
            logger.error("Error generating data for widget: {}", widgetId, e);
            return createErrorResponse("Error generating widget data: " + e.getMessage());
        }
    }
    
    private Taxonomy getTaxonomyFromConfig() {
        String uuid = (config != null) ? config.get("TAXONOMY") : null;
        String taxonomyName = (config != null) ? config.get("TAXONOMY_NAME") : null;
        
        Taxonomy taxonomy = null;
        
        if (uuid != null) {
            taxonomy = dashboardData.getClient().getTaxonomies().stream()
                    .filter(t -> uuid.equals(t.getId()))
                    .findFirst()
                    .orElse(null);
        }
        
        if (taxonomy == null && taxonomyName != null) {
            var lowerName = taxonomyName.toLowerCase(java.util.Locale.US);
            taxonomy = dashboardData.getClient().getTaxonomies().stream()
                    .filter(t -> t.getName().toLowerCase(java.util.Locale.US).contains(lowerName))
                    .findFirst()
                    .orElse(null);
        }
        
        if (taxonomy == null && !dashboardData.getClient().getTaxonomies().isEmpty()) {
            taxonomy = dashboardData.getClient().getTaxonomies().get(0);
        }
        
        return taxonomy;
    }
    
    private ClientFilter getClientFilterFromConfig() {
        String storedIdent = (config != null) ? config.get("CLIENT_FILTER") : null;
        
        ClientFilterMenu menu = new ClientFilterMenu(
                dashboardData.getClient(),
                dashboardData.getPreferences(),
                f -> {});
        
        // Select stored filter if available
        if (storedIdent != null) {
            menu.getAllItems()
                    .filter(item -> item.getId().equals(storedIdent))
                    .findAny()
                    .ifPresent(item -> menu.select(item));
        }
        
        return menu.getSelectedFilter();
    }
    
    private String getClientFilterLabel(ClientFilter filter) {
        String storedIdent = (config != null) ? config.get("CLIENT_FILTER") : null;
        
        ClientFilterMenu menu = new ClientFilterMenu(
                dashboardData.getClient(),
                dashboardData.getPreferences(),
                f -> {});
        
        // Select stored filter if available
        if (storedIdent != null) {
            menu.getAllItems()
                    .filter(item -> item.getId().equals(storedIdent))
                    .findAny()
                    .ifPresent(item -> menu.select(item));
        }
        
        return menu.getSelectedItem().getLabel();
    }
    
    private boolean getIncludeUnassignedFromConfig() {
        String code = (config != null) ? config.get("FLAG_INCLUDE_UNASSIGNED") : null;
        // Default is true as per the widget constructor
        return code != null ? Boolean.parseBoolean(code) : true;
    }
    
    private boolean getIncludeSecuritiesFromConfig() {
        String code = (config != null) ? config.get("FLAG_INCLUDE_SECURITIES") : null;
        // Default is true as per the widget constructor
        return code != null ? Boolean.parseBoolean(code) : true;
    }
    
    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("widgetId", widgetId);
        response.put("type", "taxonomyChart");
        response.put("error", message);
        response.put("timestamp", java.time.LocalDateTime.now().toString());
        return response;
    }
}

