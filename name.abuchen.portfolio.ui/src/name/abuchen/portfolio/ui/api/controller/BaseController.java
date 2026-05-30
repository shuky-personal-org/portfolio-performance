package name.abuchen.portfolio.ui.api.controller;

import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import name.abuchen.portfolio.ui.api.service.ScheduledPriceUpdateService;
import name.abuchen.portfolio.ui.api.service.PortfolioFileService;
import name.abuchen.portfolio.ui.api.service.SecurityPerformanceSnapshotCacheService;
import name.abuchen.portfolio.ui.api.service.WidgetDataService;

/**
 * Base controller class providing shared functionality for all API controllers.
 * 
 * This class provides common methods for error handling, service access,
 * and response creation that can be used by all specialized controllers.
 */
public abstract class BaseController {
    
    protected static final Logger logger = LoggerFactory.getLogger(BaseController.class);
    
    // Shared service instances across all controllers
    protected static final PortfolioFileService portfolioFileService = PortfolioFileService.getInstance();
    protected static final WidgetDataService widgetDataService = new WidgetDataService();
    protected static final ScheduledPriceUpdateService priceUpdateService = new ScheduledPriceUpdateService(portfolioFileService);
    protected static final SecurityPerformanceSnapshotCacheService securitySnapshotCacheService = SecurityPerformanceSnapshotCacheService
                    .getInstance();
    
    /**
     * Helper method to create error responses with consistent structure.
     * 
     * @param status HTTP status code
     * @param error Error type/category
     * @param message Detailed error message
     * @return Response with error details
     */
    protected Response createErrorResponse(Response.Status status, String error, String message) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("success", false);
        errorResponse.put("error", error);
        errorResponse.put("message", message);
        return Response.status(status).entity(errorResponse).build();
    }
    
    /**
     * Helper method to create 428 Precondition Required responses.
     * Used when a portfolio must be opened first before accessing a resource.
     * 
     * @param error Error type/category
     * @param message Detailed error message
     * @return Response with 428 status and error details
     */
    protected Response createPreconditionRequiredResponse(String error, String message) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("success", false);
        errorResponse.put("error", error);
        errorResponse.put("message", message);
        return Response.status(428).entity(errorResponse).build();
    }

    /**
     * Sanitize a filename for use in Content-Disposition header.
     * Escapes backslashes and double quotes to prevent header injection.
     * 
     * @param filename The filename to sanitize
     * @return Sanitized filename safe for use in attachment headers
     */
    protected String sanitizeAttachmentFilename(String filename) {
        return filename.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

