package name.abuchen.portfolio.ui.api.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.Dashboard;
import name.abuchen.portfolio.ui.api.dto.ColumnDto;
import name.abuchen.portfolio.ui.api.dto.DashboardDto;
import name.abuchen.portfolio.ui.api.dto.WidgetDto;
import name.abuchen.portfolio.ui.api.util.DashboardConverter;
import name.abuchen.portfolio.ui.api.util.SystemDashboard;

/**
 * REST Controller for widget operations.
 * 
 * This controller provides endpoints to manage widgets within dashboards.
 */
@Path("/api/v1/portfolios/{portfolioId}/dashboards/{dashboardId}/widgets")
public class WidgetController extends BaseController {
    
    /**
     * Get all widgets in a dashboard.
     * 
     * @param portfolioId The portfolio ID
     * @param dashboardId The dashboard ID
     * @return List of all widgets
     */
    @GET
    @Path("")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllWidgets(@PathParam("portfolioId") String portfolioId,
                                  @PathParam("dashboardId") String dashboardId) {
        try {
            logger.info("Getting all widgets for dashboard {} in portfolio {}", dashboardId, portfolioId);
            
            // Get the cached Client for this portfolio
            Client client = portfolioFileService.getPortfolio(portfolioId);
            
            if (client == null) {
                logger.warn("No cached client found for portfolio: {}", portfolioId);
                return createPreconditionRequiredResponse(
                    "PORTFOLIO_NOT_LOADED", 
                    "Portfolio must be opened first before accessing widgets");
            }
            
            Dashboard dashboard;
            if (SystemDashboard.isSystemDashboard(dashboardId))
            {
                dashboard = SystemDashboard.getDashboard();
            }
            else
            {
                dashboard = client.getDashboards()
                    .filter(d -> dashboardId.equals(d.getId()))
                    .findFirst()
                    .orElse(null);

                if (dashboard == null)
                {
                    logger.warn("Dashboard not found: {} in portfolio: {}", dashboardId, portfolioId);
                    return createErrorResponse(Response.Status.NOT_FOUND,
                        "Dashboard not found",
                        "Dashboard with ID " + dashboardId + " not found");
                }
            }
            
            // Convert dashboard to DTO to get all widgets
            DashboardDto dashboardDto = DashboardConverter.toDto(dashboard);
            
            // Get all widgets from all columns
            List<WidgetDto> widgets = dashboardDto.getColumns().stream()
                .flatMap(column -> column.getWidgets().stream())
                .collect(Collectors.toList());
            
            // Create response
            Map<String, Object> response = new HashMap<>();
            response.put("portfolioId", portfolioId);
            response.put("dashboardId", dashboardId);
            response.put("count", widgets.size());
            response.put("widgets", widgets);
            
            logger.info("Returning {} widgets for dashboard {} in portfolio {}", 
                widgets.size(), dashboardId, portfolioId);
            
            return Response.ok(response).build();
            
        } catch (Exception e) {
            logger.error("Unexpected error getting widgets for dashboard {} in portfolio {}: {}", 
                dashboardId, portfolioId, e.getMessage(), e);
            return createErrorResponse(Response.Status.INTERNAL_SERVER_ERROR, 
                "Internal server error", 
                e.getMessage());
        }
    }
    
    /**
     * Get widget data for a specific widget.
     * 
     * @param portfolioId The portfolio ID
     * @param dashboardId The dashboard ID
     * @param columnIndex The column index
     * @param widgetIndex The widget index within the column
     * @param reportingPeriodCode Optional reporting period code to override widget configuration
     * @return Widget data
     */
    @GET
    @Path("/data")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getWidgetData(@PathParam("portfolioId") String portfolioId,
                                  @PathParam("dashboardId") String dashboardId,
                                  @QueryParam("columnIndex") Integer columnIndex,
                                  @QueryParam("widgetIndex") Integer widgetIndex,
                                  @QueryParam("reportingPeriodCode") String reportingPeriodCode) {
        try {
            logger.info("Getting widget data for portfolio {}, dashboard {}, column {}, widget {}", 
                portfolioId, dashboardId, columnIndex, widgetIndex);
            
            // Validate required query parameters
            if (columnIndex == null) {
                logger.warn("Missing required parameter: columnIndex");
                return createErrorResponse(Response.Status.BAD_REQUEST, 
                    "Missing required parameter", 
                    "columnIndex query parameter is required");
            }
            
            if (widgetIndex == null) {
                logger.warn("Missing required parameter: widgetIndex");
                return createErrorResponse(Response.Status.BAD_REQUEST, 
                    "Missing required parameter", 
                    "widgetIndex query parameter is required");
            }
            
            // Get the cached Client for this portfolio
            Client client = portfolioFileService.getPortfolio(portfolioId);
            
            if (client == null) {
                logger.warn("No cached client found for portfolio: {}", portfolioId);
                return createPreconditionRequiredResponse(
                    "PORTFOLIO_NOT_LOADED", 
                    "Portfolio must be opened first before accessing widgets");
            }
            
            Dashboard dashboard;
            if (SystemDashboard.isSystemDashboard(dashboardId))
            {
                dashboard = SystemDashboard.getDashboard();
            }
            else
            {
                dashboard = client.getDashboards()
                    .filter(d -> dashboardId.equals(d.getId()))
                    .findFirst()
                    .orElse(null);

                if (dashboard == null)
                {
                    logger.warn("Dashboard not found: {} in portfolio: {}", dashboardId, portfolioId);
                    return createErrorResponse(Response.Status.NOT_FOUND,
                        "Dashboard not found",
                        "Dashboard with ID " + dashboardId + " not found");
                }
            }
            
            // Get the columns from the dashboard
            List<Dashboard.Column> columns = dashboard.getColumns();
            
            if (columnIndex < 0 || columnIndex >= columns.size()) {
                logger.warn("Column index out of bounds: {} (dashboard has {} columns)", 
                    columnIndex, columns.size());
                return createErrorResponse(Response.Status.BAD_REQUEST, 
                    "Column index out of bounds", 
                    "Column index " + columnIndex + " is out of bounds (0-" + (columns.size() - 1) + ")");
            }
            
            Dashboard.Column column = columns.get(columnIndex);
            List<Dashboard.Widget> widgets = column.getWidgets();
            
            if (widgetIndex < 0 || widgetIndex >= widgets.size()) {
                logger.warn("Widget index out of bounds: {} (column has {} widgets)", 
                    widgetIndex, widgets.size());
                return createErrorResponse(Response.Status.BAD_REQUEST, 
                    "Widget index out of bounds", 
                    "Widget index " + widgetIndex + " is out of bounds (0-" + (widgets.size() - 1) + ")");
            }
            
            Dashboard.Widget widget = widgets.get(widgetIndex);
            
            // Use WidgetDataService to get widget data
            Map<String, Object> widgetData = widgetDataService.getWidgetData(widget, client, reportingPeriodCode);
            
            // Add portfolio context to the response
            Map<String, Object> response = new HashMap<>();
            response.put("portfolioId", portfolioId);
            response.put("dashboardId", dashboardId);
            response.put("columnIndex", columnIndex);
            response.put("widgetIndex", widgetIndex);
            response.putAll(widgetData);
            
            return Response.ok(response).build();
            
        } catch (Exception e) {
            logger.error("Unexpected error getting widget data for portfolio {}, dashboard {}, column {}, widget {}: {}", 
                portfolioId, dashboardId, columnIndex, widgetIndex, e.getMessage(), e);
            return createErrorResponse(Response.Status.INTERNAL_SERVER_ERROR, 
                "Internal server error", 
                e.getMessage());
        }
    }
    
    /**
     * Create a new widget.
     * TODO: Implement widget creation
     * 
     * @param portfolioId The portfolio ID
     * @param dashboardId The dashboard ID
     * @param widgetData Widget data
     * @return Created widget
     */
    @POST
    @Path("")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createWidget(@PathParam("portfolioId") String portfolioId,
                                 @PathParam("dashboardId") String dashboardId,
                                 Map<String, Object> widgetData) {
        // TODO: Implement widget creation
        return createErrorResponse(Response.Status.NOT_IMPLEMENTED, 
            "Not implemented", 
            "Widget creation not yet implemented");
    }
    
    /**
     * Update an existing widget.
     * TODO: Implement widget update
     * 
     * @param portfolioId The portfolio ID
     * @param dashboardId The dashboard ID
     * @param columnIndex The column index
     * @param widgetIndex The widget index
     * @param widgetData Updated widget data
     * @return Updated widget
     */
    @PUT
    @Path("/{columnIndex}/{widgetIndex}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateWidget(@PathParam("portfolioId") String portfolioId,
                                 @PathParam("dashboardId") String dashboardId,
                                 @PathParam("columnIndex") Integer columnIndex,
                                 @PathParam("widgetIndex") Integer widgetIndex,
                                 Map<String, Object> widgetData) {
        // TODO: Implement widget update
        return createErrorResponse(Response.Status.NOT_IMPLEMENTED, 
            "Not implemented", 
            "Widget update not yet implemented");
    }
    
    /**
     * Delete a widget.
     * TODO: Implement widget deletion
     * 
     * @param portfolioId The portfolio ID
     * @param dashboardId The dashboard ID
     * @param columnIndex The column index
     * @param widgetIndex The widget index
     * @return Deletion confirmation
     */
    @DELETE
    @Path("/{columnIndex}/{widgetIndex}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteWidget(@PathParam("portfolioId") String portfolioId,
                                 @PathParam("dashboardId") String dashboardId,
                                 @PathParam("columnIndex") Integer columnIndex,
                                 @PathParam("widgetIndex") Integer widgetIndex) {
        // TODO: Implement widget deletion
        return createErrorResponse(Response.Status.NOT_IMPLEMENTED, 
            "Not implemented", 
            "Widget deletion not yet implemented");
    }
}

