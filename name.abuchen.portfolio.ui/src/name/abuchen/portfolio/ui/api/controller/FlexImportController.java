package name.abuchen.portfolio.ui.api.controller;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.ui.api.dto.FlexImportRequest;
import name.abuchen.portfolio.ui.api.dto.FlexImportResponse;
import name.abuchen.portfolio.ui.api.dto.FlexImportPreviewResponse;
import name.abuchen.portfolio.ui.api.service.FlexImportService;
import name.abuchen.portfolio.ui.api.service.PortfolioFileService;
import name.abuchen.portfolio.ui.util.FlexReportsDirectory;

/**
 * REST Controller for IB Flex report imports.
 * 
 * This controller provides an endpoint to programmatically import IB Flex reports
 * without using the UI wizard.
 * 
 * The endpoint accepts JSON with a file path (relative to Flex reports directory) and configuration.
 * The Flex reports directory is configured via the FLEX_REPORTS_DIR environment variable
 * or flex.reports.dir system property. If neither is set, defaults to the current working directory.
 */
@javax.ws.rs.Path("/api/v1/portfolios/{portfolioId}/flex-import")
public class FlexImportController extends BaseController
{
    private static final Logger logger = LoggerFactory.getLogger(FlexImportController.class);

    /**
     * Get the Flex reports directory from environment variable or system property.
     * 
     * @return Flex reports directory path
     */
    private static Path getFlexReportsDirectory()
    {
        Path path = FlexReportsDirectory.resolveOrCreate();
        if (!Files.exists(path))
            logger.warn("Flex reports directory does not exist: {}", path);
        else if (!Files.isDirectory(path))
            logger.warn("Flex reports path is not a directory: {}", path);
        return path;
    }

    /**
     * Import IB Flex report.
     * 
     * This endpoint accepts JSON with:
     * - filePath: Relative file path to Flex report XML file (relative to Flex reports directory)
     * - currencyAccountMap: Map of currency code to primary account UUID
     * - currencySecondaryAccountMap: Map of currency code to secondary account UUID (optional)
     * - portfolioUUID: Primary portfolio UUID
     * - secondaryPortfolioUUID: Secondary portfolio UUID (optional)
     * - convertBuySellToDelivery: Whether to convert BuySell transactions to Delivery
     * - removeDividends: Whether to remove dividends
     * - importNotes: Whether to import notes from source
     * 
     * @param portfolioId The portfolio ID (used to get the client)
     * @param request The Flex import request containing file path and configuration
     * @return Import result with success status, errors, and count of imported items
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response importFlexReports(@PathParam("portfolioId") String portfolioId, FlexImportRequest request)
    {
        logger.info("Starting Flex import request for portfolio: {}, file: {}", portfolioId, request.getFilePath());
        
        try
        {
            // Get client
            logger.debug("Loading client for portfolio: {}", portfolioId);
            Client client = portfolioFileService.getPortfolio(portfolioId);
            if (client == null)
            {
                logger.warn("Portfolio not found or not opened: {}", portfolioId);
                return createPreconditionRequiredResponse("PORTFOLIO_NOT_FOUND",
                                "Portfolio not found or not opened: " + portfolioId);
            }
            logger.debug("Client loaded successfully for portfolio: {}", portfolioId);

            // Validate request
            if (request.getCurrencyAccountMap() == null || request.getCurrencyAccountMap().isEmpty())
            {
                logger.warn("Flex import request missing currencyAccountMap for portfolio: {}", portfolioId);
                return createErrorResponse(Response.Status.BAD_REQUEST, "MISSING_CONFIG",
                                "currencyAccountMap is required");
            }

            if (request.getPortfolioUUID() == null || request.getPortfolioUUID().isEmpty())
            {
                logger.warn("Flex import request missing portfolioUUID for portfolio: {}", portfolioId);
                return createErrorResponse(Response.Status.BAD_REQUEST, "MISSING_CONFIG",
                                "portfolioUUID is required");
            }

            if (request.getFilePath() == null || request.getFilePath().isEmpty())
            {
                logger.warn("Flex import request missing filePath for portfolio: {}", portfolioId);
                return createErrorResponse(Response.Status.BAD_REQUEST, "MISSING_FILE",
                                "filePath is required");
            }

            logger.debug("Flex import request validated - portfolioUUID: {}, currencies: {}, options: convertBuySellToDelivery={}, removeDividends={}, importNotes={}",
                            request.getPortfolioUUID(), request.getCurrencyAccountMap().keySet(),
                            request.isConvertBuySellToDelivery(), request.isRemoveDividends(), request.isImportNotes());

            // Resolve file path relative to Flex reports directory
            File file;
            try
            {
                Path flexReportsDir = getFlexReportsDirectory();
                logger.debug("Resolving Flex report file path: {} (base directory: {})", request.getFilePath(), flexReportsDir);
                Path resolvedPath = flexReportsDir.resolve(request.getFilePath()).normalize();
                if (!resolvedPath.startsWith(flexReportsDir))
                {
                    logger.warn("File path outside Flex reports directory: {} (resolved: {})", request.getFilePath(), resolvedPath);
                    return createErrorResponse(Response.Status.BAD_REQUEST, "INVALID_PATH",
                                    "File path outside Flex reports directory: " + request.getFilePath());
                }
                if (!Files.exists(resolvedPath))
                {
                    logger.warn("Flex report file not found: {} (resolved: {})", request.getFilePath(), resolvedPath);
                    return createErrorResponse(Response.Status.BAD_REQUEST, "FILE_NOT_FOUND",
                                    "File not found: " + request.getFilePath());
                }
                file = resolvedPath.toFile();
                logger.info("Flex report file resolved successfully: {} (size: {} bytes)", resolvedPath, file.length());
            }
            catch (Exception e)
            {
                logger.error("Failed to resolve Flex report file path: {}", request.getFilePath(), e);
                return createErrorResponse(Response.Status.BAD_REQUEST, "INVALID_PATH",
                                "Failed to resolve file path: " + e.getMessage());
            }

            // Prepare account mappings (default empty map for secondary if not provided)
            Map<String, String> currencyAccountMap = request.getCurrencyAccountMap() != null
                            ? request.getCurrencyAccountMap()
                            : new HashMap<>();
            Map<String, String> currencySecondaryAccountMap = request.getCurrencySecondaryAccountMap() != null
                            ? request.getCurrencySecondaryAccountMap()
                            : new HashMap<>();

            logger.debug("Account mappings - primary: {} currencies, secondary: {} currencies",
                            currencyAccountMap.size(), currencySecondaryAccountMap.size());

            // Perform import
            logger.info("Starting Flex report import - file: {}, portfolioUUID: {}", file.getName(), request.getPortfolioUUID());
            FlexImportService service = new FlexImportService();
            FlexImportService.ImportResult result = service.importFlexReport(client, file,
                            currencyAccountMap, currencySecondaryAccountMap, request.getPortfolioUUID(),
                            request.getSecondaryPortfolioUUID(), request.isConvertBuySellToDelivery(),
                            request.isRemoveDividends(), request.isImportNotes());

            // Save client if any items were imported
            if (result.getItemsImported() > 0)
            {
                try
                {
                    portfolioFileService.saveFile(portfolioId);
                    logger.info("Portfolio file saved after Flex import - portfolio: {}", portfolioId);
                }
                catch (Exception e)
                {
                    logger.error("Failed to save portfolio file after Flex import - portfolio: {}", portfolioId, e);
                    // Continue - the import was successful, saving failure is logged but doesn't fail the request
                }
            }

            // Build response
            FlexImportResponse response = new FlexImportResponse(result.isSuccess(), result.getErrors(),
                            result.getItemsImported());

            if (result.isSuccess())
            {
                logger.info("Flex import completed successfully - portfolio: {}, file: {}, items imported: {}",
                                portfolioId, request.getFilePath(), result.getItemsImported());
            }
            else
            {
                logger.warn("Flex import failed - portfolio: {}, file: {}, errors: {}",
                                portfolioId, request.getFilePath(), result.getErrors().size());
                if (logger.isDebugEnabled())
                {
                    result.getErrors().forEach(error -> logger.debug("Flex import error: {}", error));
                }
            }
            
            return Response.ok(response).build();
        }
        catch (Exception e)
        {
            logger.error("Unexpected error during Flex import - portfolio: {}, file: {}",
                            portfolioId, request.getFilePath(), e);
            return createErrorResponse(Response.Status.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                            "Unexpected error: " + e.getMessage());
        }
    }

    /**
     * Preview Flex report extraction and validation without importing.
     * 
     * This endpoint extracts and validates all items from a Flex report but does not
     * perform the actual import. It returns all extracted entries with their validation
     * status (OK, WARNING, ERROR) and whether they would be imported.
     * 
     * @param portfolioId The portfolio ID (used to get the client)
     * @param request The Flex import request containing file path and configuration
     * @return Preview response with all extracted entries and their status
     */
    @POST
    @javax.ws.rs.Path("/preview")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response previewFlexReport(@PathParam("portfolioId") String portfolioId, FlexImportRequest request)
    {
        logger.info("Starting Flex import preview for portfolio: {}, file: {}", portfolioId, request.getFilePath());
        
        try
        {
            // Get client
            logger.debug("Loading client for portfolio: {}", portfolioId);
            Client client = portfolioFileService.getPortfolio(portfolioId);
            if (client == null)
            {
                logger.warn("Portfolio not found or not opened: {}", portfolioId);
                return createPreconditionRequiredResponse("PORTFOLIO_NOT_FOUND",
                                "Portfolio not found or not opened: " + portfolioId);
            }
            logger.debug("Client loaded successfully for portfolio: {}", portfolioId);

            // Validate request
            if (request.getCurrencyAccountMap() == null || request.getCurrencyAccountMap().isEmpty())
            {
                logger.warn("Flex import preview request missing currencyAccountMap for portfolio: {}", portfolioId);
                return createErrorResponse(Response.Status.BAD_REQUEST, "MISSING_CONFIG",
                                "currencyAccountMap is required");
            }

            if (request.getPortfolioUUID() == null || request.getPortfolioUUID().isEmpty())
            {
                logger.warn("Flex import preview request missing portfolioUUID for portfolio: {}", portfolioId);
                return createErrorResponse(Response.Status.BAD_REQUEST, "MISSING_CONFIG",
                                "portfolioUUID is required");
            }

            if (request.getFilePath() == null || request.getFilePath().isEmpty())
            {
                logger.warn("Flex import preview request missing filePath for portfolio: {}", portfolioId);
                return createErrorResponse(Response.Status.BAD_REQUEST, "MISSING_FILE",
                                "filePath is required");
            }

            // Resolve file path relative to Flex reports directory
            File file;
            try
            {
                Path flexReportsDir = getFlexReportsDirectory();
                logger.debug("Resolving Flex report file path for preview: {} (base directory: {})", request.getFilePath(), flexReportsDir);
                Path resolvedPath = flexReportsDir.resolve(request.getFilePath()).normalize();
                if (!resolvedPath.startsWith(flexReportsDir))
                {
                    logger.warn("File path outside Flex reports directory: {} (resolved: {})", request.getFilePath(), resolvedPath);
                    return createErrorResponse(Response.Status.BAD_REQUEST, "INVALID_PATH",
                                    "File path outside Flex reports directory: " + request.getFilePath());
                }
                if (!Files.exists(resolvedPath))
                {
                    logger.warn("Flex report file not found: {} (resolved: {})", request.getFilePath(), resolvedPath);
                    return createErrorResponse(Response.Status.BAD_REQUEST, "FILE_NOT_FOUND",
                                    "File not found: " + request.getFilePath());
                }
                file = resolvedPath.toFile();
                logger.info("Flex report file resolved successfully for preview: {} (size: {} bytes)", resolvedPath, file.length());
            }
            catch (Exception e)
            {
                logger.error("Failed to resolve Flex report file path: {}", request.getFilePath(), e);
                return createErrorResponse(Response.Status.BAD_REQUEST, "INVALID_PATH",
                                "Failed to resolve file path: " + e.getMessage());
            }

            // Prepare account mappings (default empty map for secondary if not provided)
            Map<String, String> currencyAccountMap = request.getCurrencyAccountMap() != null
                            ? request.getCurrencyAccountMap()
                            : new HashMap<>();
            Map<String, String> currencySecondaryAccountMap = request.getCurrencySecondaryAccountMap() != null
                            ? request.getCurrencySecondaryAccountMap()
                            : new HashMap<>();

            logger.debug("Account mappings for preview - primary: {} currencies, secondary: {} currencies",
                            currencyAccountMap.size(), currencySecondaryAccountMap.size());

            // Perform preview
            logger.info("Starting Flex report preview - file: {}, portfolioUUID: {}", file.getName(), request.getPortfolioUUID());
            FlexImportService service = new FlexImportService();
            FlexImportPreviewResponse preview = service.previewFlexReport(client, file,
                            currencyAccountMap, currencySecondaryAccountMap, request.getPortfolioUUID(),
                            request.getSecondaryPortfolioUUID());

            logger.info("Flex import preview completed - portfolio: {}, file: {}, total entries: {}, errors: {}, warnings: {}, ok: {}",
                            portfolioId, request.getFilePath(), preview.getTotalEntries(),
                            preview.getEntriesWithErrors(), preview.getEntriesWithWarnings(), preview.getEntriesOk());

            return Response.ok(preview).build();
        }
        catch (Exception e)
        {
            logger.error("Unexpected error during Flex import preview - portfolio: {}, file: {}",
                            portfolioId, request.getFilePath(), e);
            return createErrorResponse(Response.Status.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                            "Unexpected error: " + e.getMessage());
        }
    }
}

