package name.abuchen.portfolio.ui.api.controller;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.Dashboard;
import name.abuchen.portfolio.ui.api.dto.DashboardDto;
import name.abuchen.portfolio.ui.api.util.DashboardConverter;

/**
 * REST Controller for dashboard operations.
 * 
 * This controller provides endpoints to manage dashboards within a portfolio.
 */
@Path("/api/v1/portfolios/{portfolioId}/dashboards")
public class DashboardController extends BaseController {
    
    /**
     * Get all dashboards in a portfolio.
     * 
     * @param portfolioId The portfolio ID
     * @return List of all dashboards
     */
    @GET
    @Path("")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllDashboards(@PathParam("portfolioId") String portfolioId) {
        try {
            logger.info("Getting all dashboards for portfolio: {}", portfolioId);
            
            // Get the cached Client for this portfolio
            Client client = portfolioFileService.getPortfolio(portfolioId);
            
            if (client == null) {
                logger.warn("No cached client found for portfolio: {}", portfolioId);
                return createPreconditionRequiredResponse(
                    "PORTFOLIO_NOT_LOADED", 
                    "Portfolio must be opened first before accessing dashboards");
            }
            
            // Get all dashboards and convert using DashboardConverter
            List<DashboardDto> dashboards = client.getDashboards()
                .map(DashboardConverter::toDto)
                .collect(Collectors.toList());
            
            // Create response
            Map<String, Object> response = new HashMap<>();
            response.put("portfolioId", portfolioId);
            response.put("count", dashboards.size());
            response.put("dashboards", dashboards);
            
            logger.info("Returning {} dashboards for portfolio {}", dashboards.size(), portfolioId);
            
            return Response.ok(response).build();
            
        } catch (Exception e) {
            logger.error("Unexpected error getting dashboards for portfolio {}: {}", 
                portfolioId, e.getMessage(), e);
            return createErrorResponse(Response.Status.INTERNAL_SERVER_ERROR, 
                "Internal server error", 
                e.getMessage());
        }
    }

    /**
     * Download all dashboard configurations for a portfolio as a JSON file.
     *
     * @param portfolioId The portfolio ID
     * @return JSON attachment containing dashboard configurations
     */
    @GET
    @Path("/download")
    @Produces(MediaType.APPLICATION_JSON)
    public Response downloadDashboardsConfiguration(@PathParam("portfolioId") String portfolioId) {
        try {
            logger.info("Downloading dashboard configuration for portfolio: {}", portfolioId);

            Client client = portfolioFileService.getPortfolio(portfolioId);

            if (client == null) {
                logger.warn("No cached client found for portfolio: {}", portfolioId);
                return createPreconditionRequiredResponse(
                    "PORTFOLIO_NOT_LOADED",
                    "Portfolio must be opened first before downloading dashboard configuration");
            }

            List<DashboardDto> dashboards = client.getDashboards()
                .map(DashboardConverter::toDto)
                .collect(Collectors.toList());

            Map<String, Object> export = new LinkedHashMap<>();
            export.put("portfolioId", portfolioId);
            export.put("exportedAt", Instant.now().toString());
            export.put("count", dashboards.size());
            export.put("dashboards", dashboards);

            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            byte[] jsonBytes = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(export);

            String filename = sanitizeAttachmentFilename(portfolioId + "-dashboards.json");

            logger.info("Returning dashboard configuration download for portfolio {} ({} dashboards)",
                portfolioId, dashboards.size());

            return Response.ok(jsonBytes)
                .type(MediaType.APPLICATION_JSON)
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .build();

        } catch (Exception e) {
            logger.error("Unexpected error downloading dashboard configuration for portfolio {}: {}",
                portfolioId, e.getMessage(), e);
            return createErrorResponse(Response.Status.INTERNAL_SERVER_ERROR,
                "Internal server error",
                e.getMessage());
        }
    }
    
    /**
     * Get a specific dashboard by ID.
     * 
     * @param portfolioId The portfolio ID
     * @param dashboardId The dashboard ID
     * @return Dashboard details
     */
    @GET
    @Path("/{dashboardId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getDashboardById(@PathParam("portfolioId") String portfolioId,
                                    @PathParam("dashboardId") String dashboardId) {
        try {
            logger.info("Getting dashboard {} for portfolio {}", dashboardId, portfolioId);
            
            // Get the cached Client for this portfolio
            Client client = portfolioFileService.getPortfolio(portfolioId);
            
            if (client == null) {
                logger.warn("No cached client found for portfolio: {}", portfolioId);
                return createPreconditionRequiredResponse(
                    "PORTFOLIO_NOT_LOADED", 
                    "Portfolio must be opened first before accessing dashboards");
            }
            
            // Find the dashboard by ID
            Dashboard dashboard = client.getDashboards()
                .filter(d -> dashboardId.equals(d.getId()))
                .findFirst()
                .orElse(null);
            
            if (dashboard == null) {
                logger.warn("Dashboard not found: {} in portfolio: {}", dashboardId, portfolioId);
                return createErrorResponse(Response.Status.NOT_FOUND, 
                    "Dashboard not found", 
                    "Dashboard with ID " + dashboardId + " not found in portfolio");
            }
            
            // Convert to DTO using DashboardConverter
            DashboardDto dashboardDto = DashboardConverter.toDto(dashboard);
            
            // Create response
            Map<String, Object> response = new HashMap<>();
            response.put("portfolioId", portfolioId);
            response.put("dashboard", dashboardDto);
            
            logger.info("Returning dashboard {} ({}) for portfolio {}", 
                dashboard.getName(), dashboardId, portfolioId);
            
            return Response.ok(response).build();
            
        } catch (Exception e) {
            logger.error("Unexpected error getting dashboard {} for portfolio {}: {}", 
                dashboardId, portfolioId, e.getMessage(), e);
            return createErrorResponse(Response.Status.INTERNAL_SERVER_ERROR, 
                "Internal server error", 
                e.getMessage());
        }
    }
    
    /**
     * Create a new dashboard.
     * TODO: Implement dashboard creation
     * 
     * @param portfolioId The portfolio ID
     * @param dashboardData Dashboard data
     * @return Created dashboard
     */
    @POST
    @Path("")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createDashboard(@PathParam("portfolioId") String portfolioId,
                                   Map<String, Object> dashboardData) {
        // TODO: Implement dashboard creation
        return createErrorResponse(Response.Status.NOT_IMPLEMENTED, 
            "Not implemented", 
            "Dashboard creation not yet implemented");
    }
    
    /**
     * Update an existing dashboard.
     * TODO: Implement dashboard update
     * 
     * @param portfolioId The portfolio ID
     * @param dashboardId The dashboard ID
     * @param dashboardData Updated dashboard data
     * @return Updated dashboard
     */
    @PUT
    @Path("/{dashboardId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateDashboard(@PathParam("portfolioId") String portfolioId,
                                   @PathParam("dashboardId") String dashboardId,
                                   Map<String, Object> dashboardData) {
        // TODO: Implement dashboard update
        return createErrorResponse(Response.Status.NOT_IMPLEMENTED, 
            "Not implemented", 
            "Dashboard update not yet implemented");
    }
    
    /**
     * Delete a dashboard.
     * TODO: Implement dashboard deletion
     * 
     * @param portfolioId The portfolio ID
     * @param dashboardId The dashboard ID
     * @return Deletion confirmation
     */
    @DELETE
    @Path("/{dashboardId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteDashboard(@PathParam("portfolioId") String portfolioId,
                                   @PathParam("dashboardId") String dashboardId) {
        // TODO: Implement dashboard deletion
        return createErrorResponse(Response.Status.NOT_IMPLEMENTED, 
            "Not implemented", 
            "Dashboard deletion not yet implemented");
    }
    
}

