package name.abuchen.portfolio.ui.api.controller;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.Dashboard;
import name.abuchen.portfolio.ui.api.dto.DashboardDto;
import name.abuchen.portfolio.ui.api.util.DashboardConverter;
import name.abuchen.portfolio.ui.api.util.SystemDashboard;

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
            
            Client client = portfolioFileService.getPortfolio(portfolioId);
            if (client == null) {
                logger.warn("No cached client found for portfolio: {}", portfolioId);
                return createPreconditionRequiredResponse(
                    "PORTFOLIO_NOT_LOADED", 
                    "Portfolio must be opened first before accessing dashboards");
            }
            
            List<DashboardDto> dashboards = listDashboardsForPortfolio(client);
            
            // Create response
            Map<String, Object> response = new HashMap<>();
            response.put("portfolioId", portfolioId);
            response.put("count", dashboards.size());
            response.put("dashboards", dashboards);
            
            logger.info("Returning {} dashboards (including Main) for portfolio {}", dashboards.size(), portfolioId);
            
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
            logger.info("Downloading dashboard configurations for portfolio: {}", portfolioId);

            Client client = portfolioFileService.getPortfolio(portfolioId);
            if (client == null) {
                logger.warn("No cached client found for portfolio: {}", portfolioId);
                return createPreconditionRequiredResponse(
                    "PORTFOLIO_NOT_LOADED",
                    "Portfolio must be opened first before downloading dashboard configurations");
            }

            List<DashboardDto> dashboards = listPortfolioDashboardsForDownload(client);

            Map<String, Object> export = new HashMap<>();
            export.put("portfolioId", portfolioId);
            export.put("count", dashboards.size());
            export.put("dashboards", dashboards);

            var mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            byte[] jsonBytes = mapper.writeValueAsString(export).getBytes(StandardCharsets.UTF_8);

            String filename = buildDashboardsDownloadFilename(portfolioId);

            return Response.ok(jsonBytes, MediaType.APPLICATION_JSON)
                            .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                            .header("X-Content-Type-Options", "nosniff")
                            .build();

        } catch (FileNotFoundException e) {
            logger.warn("Portfolio not found for dashboard download: {} - {}", portfolioId, e.getMessage());
            return createErrorResponse(Response.Status.NOT_FOUND,
                "PORTFOLIO_NOT_FOUND",
                e.getMessage());

        } catch (IOException e) {
            logger.error("Failed to serialize dashboard configurations for portfolio {}: {}", portfolioId, e.getMessage(), e);
            return createErrorResponse(Response.Status.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "Failed to serialize dashboard configurations: " + e.getMessage());

        } catch (Exception e) {
            logger.error("Unexpected error downloading dashboard configurations for portfolio {}: {}",
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
            
            DashboardDto dashboardDto;
            if (SystemDashboard.isSystemDashboard(dashboardId))
            {
                dashboardDto = SystemDashboard.toDto();
            }
            else
            {
                Dashboard dashboard = client.getDashboards()
                    .filter(d -> dashboardId.equals(d.getId()))
                    .findFirst()
                    .orElse(null);

                if (dashboard == null)
                {
                    logger.warn("Dashboard not found: {} in portfolio: {}", dashboardId, portfolioId);
                    return createErrorResponse(Response.Status.NOT_FOUND,
                        "Dashboard not found",
                        "Dashboard with ID " + dashboardId + " not found in portfolio");
                }

                dashboardDto = DashboardConverter.toDto(dashboard);
            }
            
            // Create response
            Map<String, Object> response = new HashMap<>();
            response.put("portfolioId", portfolioId);
            response.put("dashboard", dashboardDto);
            
            logger.info("Returning dashboard {} ({}) for portfolio {}",
                dashboardDto.getName(), dashboardId, portfolioId);
            
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

    private List<DashboardDto> listDashboardsForPortfolio(Client client)
    {
        List<DashboardDto> dashboards = new ArrayList<>();
        dashboards.add(SystemDashboard.toDto());
        client.getDashboards()
            .filter(d -> !SystemDashboard.isSystemDashboard(d.getId()))
            .map(DashboardConverter::toDto)
            .forEach(dashboards::add);
        return dashboards;
    }

    private List<DashboardDto> listPortfolioDashboardsForDownload(Client client)
    {
        return client.getDashboards()
            .filter(d -> !SystemDashboard.isSystemDashboard(d.getId()))
            .map(DashboardConverter::toDto)
            .collect(Collectors.toList());
    }

    private String buildDashboardsDownloadFilename(String portfolioId) throws IOException {
        java.nio.file.Path filePath = portfolioFileService.getPortfolioFilePath(portfolioId);
        String basename = filePath.getFileName().toString();
        int dotIndex = basename.lastIndexOf('.');
        if (dotIndex > 0) {
            basename = basename.substring(0, dotIndex);
        }
        return sanitizeAttachmentFilename(basename + "-dashboards.json");
    }
    
}

