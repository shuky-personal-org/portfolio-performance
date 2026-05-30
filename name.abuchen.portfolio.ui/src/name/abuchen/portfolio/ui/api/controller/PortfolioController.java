package name.abuchen.portfolio.ui.api.controller;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PATCH;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.money.CurrencyConverter;
import name.abuchen.portfolio.money.CurrencyConverterImpl;
import name.abuchen.portfolio.money.ExchangeRateProviderFactory;
import name.abuchen.portfolio.money.Money;
import name.abuchen.portfolio.money.Values;
import name.abuchen.portfolio.snapshot.ClientPerformanceSnapshot;
import name.abuchen.portfolio.snapshot.ClientPerformanceSnapshot.CategoryType;
import name.abuchen.portfolio.snapshot.ClientSnapshot;
import name.abuchen.portfolio.snapshot.PerformanceIndex;
import name.abuchen.portfolio.snapshot.ReportingPeriod;
import name.abuchen.portfolio.ui.api.dto.BaseCurrencyUpdateRequest;
import name.abuchen.portfolio.ui.api.dto.PerformanceCalculationDto;
import name.abuchen.portfolio.ui.api.dto.PerformanceCalculationDto.MoneyValueDto;
import name.abuchen.portfolio.ui.api.dto.PortfolioFileInfo;
import name.abuchen.portfolio.ui.api.dto.PortfolioFileRequest;
import name.abuchen.portfolio.ui.api.service.QuoteFeedApiKeyService;
import name.abuchen.portfolio.util.Interval;

/**
 * REST Controller for portfolio file operations.
 * 
 * This controller provides endpoints to list, open, and manage portfolio files.
 * Specialized operations for securities, accounts, dashboards, widgets, earnings, 
 * and options have been moved to their respective controllers.
 */
@Path("/api/v1/portfolios")
public class PortfolioController extends BaseController {
    
    /**
     * List all portfolios in the portfolio directory.
     *
     * @return List of portfolios
     */
    @GET
    @Path("")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listPortfolios() {
        try {
            logger.info("Listing portfolios");
            
            List<name.abuchen.portfolio.ui.api.dto.PortfolioFileInfo> files = portfolioFileService.listPortfolioFiles();
            
            return Response.ok(files).build();
            
        } catch (IOException e) {
            logger.error("Failed to list portfolios", e);
            return createErrorResponse(Response.Status.INTERNAL_SERVER_ERROR, 
                "INTERNAL_ERROR", 
                "Failed to list portfolios: " + e.getMessage());
        }
    }

    /**
     * Create a new empty portfolio file.
     * 
     * @param request The requested portfolio file name or path
     * @return Created portfolio file information
     */
    @POST
    @Path("")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createPortfolio(PortfolioFileRequest request) {
        try {
            logger.info("Creating portfolio file");

            PortfolioFileInfo fileInfo = portfolioFileService.createPortfolioFile(getRequestedPath(request));

            return Response.status(Response.Status.CREATED).entity(fileInfo).build();

        } catch (FileAlreadyExistsException e) {
            logger.warn("Portfolio file already exists: {}", e.getMessage());
            return createErrorResponse(Response.Status.CONFLICT,
                "PORTFOLIO_ALREADY_EXISTS",
                e.getMessage());

        } catch (IllegalArgumentException | SecurityException e) {
            logger.warn("Invalid portfolio create request: {}", e.getMessage());
            return createErrorResponse(Response.Status.BAD_REQUEST,
                "INVALID_REQUEST",
                e.getMessage());

        } catch (IOException e) {
            logger.error("Failed to create portfolio", e);
            return createErrorResponse(Response.Status.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "Failed to create portfolio: " + e.getMessage());
        }
    }

    /**
     * Duplicate an existing portfolio file.
     * 
     * @param portfolioId The source portfolio ID
     * @param request Optional requested portfolio file name or path
     * @return Duplicated portfolio file information
     */
    @POST
    @Path("/{portfolioId}/duplicate")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response duplicatePortfolio(@PathParam("portfolioId") String portfolioId, PortfolioFileRequest request) {
        try {
            logger.info("Duplicating portfolio file: {}", portfolioId);

            PortfolioFileInfo fileInfo = portfolioFileService.duplicatePortfolioFile(portfolioId, getOptionalRequestedPath(request));

            return Response.status(Response.Status.CREATED).entity(fileInfo).build();

        } catch (FileAlreadyExistsException e) {
            logger.warn("Portfolio duplicate target already exists: {}", e.getMessage());
            return createErrorResponse(Response.Status.CONFLICT,
                "PORTFOLIO_ALREADY_EXISTS",
                e.getMessage());

        } catch (FileNotFoundException e) {
            logger.warn("Portfolio not found for duplication: {} - {}", portfolioId, e.getMessage());
            return createErrorResponse(Response.Status.NOT_FOUND,
                "PORTFOLIO_NOT_FOUND",
                e.getMessage());

        } catch (IllegalArgumentException | SecurityException e) {
            logger.warn("Invalid portfolio duplicate request: {}", e.getMessage());
            return createErrorResponse(Response.Status.BAD_REQUEST,
                "INVALID_REQUEST",
                e.getMessage());

        } catch (IOException e) {
            logger.error("Failed to duplicate portfolio: {}", portfolioId, e);
            return createErrorResponse(Response.Status.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "Failed to duplicate portfolio: " + e.getMessage());
        }
    }

    /**
     * Rename an existing portfolio file.
     *
     * @param portfolioId The portfolio ID
     * @param request Requested portfolio file name or path
     * @return Renamed portfolio file information
     */
    @POST
    @Path("/{portfolioId}/rename")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response renamePortfolio(@PathParam("portfolioId") String portfolioId, PortfolioFileRequest request) {
        try {
            logger.info("Renaming portfolio file: {}", portfolioId);

            PortfolioFileInfo fileInfo = portfolioFileService.renamePortfolioFile(portfolioId, getRequestedPath(request));

            return Response.ok(fileInfo).build();

        } catch (FileAlreadyExistsException e) {
            logger.warn("Portfolio rename target already exists: {}", e.getMessage());
            return createErrorResponse(Response.Status.CONFLICT,
                "PORTFOLIO_ALREADY_EXISTS",
                e.getMessage());

        } catch (FileNotFoundException e) {
            logger.warn("Portfolio not found for rename: {} - {}", portfolioId, e.getMessage());
            return createErrorResponse(Response.Status.NOT_FOUND,
                "PORTFOLIO_NOT_FOUND",
                e.getMessage());

        } catch (IllegalArgumentException | SecurityException e) {
            logger.warn("Invalid portfolio rename request: {}", e.getMessage());
            return createErrorResponse(Response.Status.BAD_REQUEST,
                "INVALID_REQUEST",
                e.getMessage());

        } catch (IOException e) {
            logger.error("Failed to rename portfolio: {}", portfolioId, e);
            return createErrorResponse(Response.Status.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "Failed to rename portfolio: " + e.getMessage());
        }
    }

    /**
     * Update the base (reporting) currency of a portfolio.
     *
     * @param portfolioId The portfolio ID
     * @param request Currency code and optional password for encrypted files
     * @return Updated portfolio file information
     */
    @PATCH
    @Path("/{portfolioId}/baseCurrency")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateBaseCurrency(@PathParam("portfolioId") String portfolioId,
            BaseCurrencyUpdateRequest request) {
        try {
            logger.info("Updating base currency for portfolio: {}", portfolioId);

            if (request == null || request.getCurrencyCode() == null || request.getCurrencyCode().trim().isEmpty()) {
                return createErrorResponse(Response.Status.BAD_REQUEST,
                    "INVALID_REQUEST",
                    "Request body must contain a currencyCode");
            }

            char[] passwordChars = null;
            if (request.getPassword() != null && !request.getPassword().trim().isEmpty()) {
                passwordChars = request.getPassword().toCharArray();
            }

            PortfolioFileInfo fileInfo = portfolioFileService.updateBaseCurrency(
                portfolioId,
                request.getCurrencyCode(),
                passwordChars);

            return Response.ok(fileInfo).build();

        } catch (FileNotFoundException e) {
            logger.warn("Portfolio not found for base currency update: {} - {}", portfolioId, e.getMessage());
            return createErrorResponse(Response.Status.NOT_FOUND,
                "PORTFOLIO_NOT_FOUND",
                e.getMessage());

        } catch (IllegalArgumentException e) {
            logger.warn("Invalid base currency update request: {}", e.getMessage());
            return createErrorResponse(Response.Status.BAD_REQUEST,
                "INVALID_REQUEST",
                e.getMessage());

        } catch (IOException e) {
            logger.error("Failed to update base currency for portfolio: {}", portfolioId, e);
            if (e.getMessage() != null && e.getMessage().contains("Password required")) {
                return createErrorResponse(Response.Status.UNAUTHORIZED,
                    "PASSWORD_REQUIRED",
                    e.getMessage());
            }
            return createErrorResponse(Response.Status.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "Failed to update base currency: " + e.getMessage());
        }
    }

    /**
     * Remove a portfolio file by moving it to the deleted folder.
     * 
     * @param portfolioId The portfolio ID
     * @return Information about the moved file
     */
    @DELETE
    @Path("/{portfolioId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response removePortfolio(@PathParam("portfolioId") String portfolioId) {
        try {
            logger.info("Removing portfolio file: {}", portfolioId);

            PortfolioFileInfo deletedFileInfo = portfolioFileService.removePortfolioFile(portfolioId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("portfolioId", portfolioId);
            response.put("deletedPath", deletedFileInfo.getPath());

            return Response.ok(response).build();

        } catch (FileNotFoundException e) {
            logger.warn("Portfolio not found for removal: {} - {}", portfolioId, e.getMessage());
            return createErrorResponse(Response.Status.NOT_FOUND,
                "PORTFOLIO_NOT_FOUND",
                e.getMessage());

        } catch (IllegalArgumentException | SecurityException e) {
            logger.warn("Invalid portfolio removal request: {}", e.getMessage());
            return createErrorResponse(Response.Status.BAD_REQUEST,
                "INVALID_REQUEST",
                e.getMessage());

        } catch (IOException e) {
            logger.error("Failed to remove portfolio: {}", portfolioId, e);
            return createErrorResponse(Response.Status.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "Failed to remove portfolio: " + e.getMessage());
        }
    }

    /**
     * Download a portfolio file.
     * 
     * @param portfolioId The portfolio ID
     * @return Raw portfolio file bytes
     */
    @GET
    @Path("/{portfolioId}/download")
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_OCTET_STREAM })
    public Response downloadPortfolio(@PathParam("portfolioId") String portfolioId) {
        try {
            logger.info("Downloading portfolio file: {}", portfolioId);

            java.nio.file.Path filePath = portfolioFileService.getPortfolioFilePath(portfolioId);
            String filename = sanitizeAttachmentFilename(filePath.getFileName().toString());

            return Response.ok(filePath.toFile(), MediaType.APPLICATION_OCTET_STREAM)
                            .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                            .header("X-Content-Type-Options", "nosniff")
                            .build();

        } catch (FileNotFoundException e) {
            logger.warn("Portfolio not found for download: {} - {}", portfolioId, e.getMessage());
            return createErrorResponse(Response.Status.NOT_FOUND,
                "PORTFOLIO_NOT_FOUND",
                e.getMessage());

        } catch (IllegalArgumentException | SecurityException e) {
            logger.warn("Invalid portfolio download request: {}", e.getMessage());
            return createErrorResponse(Response.Status.BAD_REQUEST,
                "INVALID_REQUEST",
                e.getMessage());

        } catch (IOException e) {
            logger.error("Failed to download portfolio: {}", portfolioId, e);
            return createErrorResponse(Response.Status.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "Failed to download portfolio: " + e.getMessage());
        }
    }
    
    
    
    
    
    /**
     * Health check endpoint for file operations.
     * 
     * @return Health status
     */
    @GET
    @Path("/health")
    @Produces(MediaType.APPLICATION_JSON)
    public Response health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "PortfolioFileService");
        health.put("timestamp", java.time.LocalDateTime.now().toString());
        health.put("timezone", ZoneId.systemDefault().getId());
        health.put("cacheStats", portfolioFileService.getCacheStats());
        
        return Response.ok(health).build();
    }
    
    // ===== RESTful ID-based endpoints =====
    
    /**
     * Open a portfolio by its ID.
     * 
     * @param portfolioId The portfolio ID
     * @param password Optional password for encrypted portfolios
     * @param allowCache Whether to allow using cached version (default: true)
     * @return Portfolio information
     */
    @GET
    @Path("/{portfolioId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPortfolioById(@PathParam("portfolioId") String portfolioId, 
                                   @QueryParam("password") String password,
                                   @QueryParam("allowCache") Boolean allowCache) {
        try {
            // Default allowCache to true if not specified
            boolean useCache = allowCache == null || allowCache;
            
            logger.info("Opening portfolio by ID: " + portfolioId + " (allowCache: " + useCache + ", cache size: " + portfolioFileService.getCacheStats().get("cachedClients") + ")");
            
            PortfolioFileInfo fileInfo;
            
            // If cache is allowed, try to get from cache first
            if (useCache) {
                Client client = portfolioFileService.getPortfolio(portfolioId);
                if (client != null) {
                    logger.info("Using cached portfolio for ID: " + portfolioId);
                    fileInfo = portfolioFileService.getFullPortfolioInfo(portfolioId);
                    return Response.ok(fileInfo).build();
                }
                logger.info("Portfolio not in cache, loading from disk");
            } else {
                logger.info("Cache disabled, forcing reload from disk");
            }
            
            // Cache miss or cache disabled - load from disk
            char[] passwordChars = null;
            if (password != null && !password.trim().isEmpty()) {
                passwordChars = password.toCharArray();
            }
            
            fileInfo = portfolioFileService.openFileById(
                portfolioId,
                passwordChars
            );
            
            logger.info("Portfolio loaded successfully (cache size after: " + portfolioFileService.getCacheStats().get("cachedClients") + ")");
            
            return Response.ok(fileInfo).build();
            
        } catch (FileNotFoundException e) {
            logger.error("Portfolio not found: " + portfolioId + " - " + e.getMessage());
            return createErrorResponse(Response.Status.NOT_FOUND, 
                "PORTFOLIO_NOT_FOUND", 
                e.getMessage());
                
        } catch (IOException e) {
            logger.error("Failed to get portfolio info: " + portfolioId + " - " + e.getMessage());
            return createErrorResponse(Response.Status.BAD_REQUEST, 
                "INVALID_REQUEST", 
                e.getMessage());
                
        } catch (Exception e) {
            logger.error("Unexpected error getting portfolio info: " + portfolioId + " - " + e.getMessage());
            return createErrorResponse(Response.Status.INTERNAL_SERVER_ERROR, 
                "INTERNAL_ERROR", 
                e.getMessage());
        }
    }
    
    /**
     * Update prices and exchange rates for the portfolio.
     * 
     * This endpoint triggers a price update job that fetches both latest and historic quotes
     * for all securities in the portfolio that are not marked as retired. After updating 
     * security prices, it also updates exchange rates from all registered exchange rate 
     * providers, similar to the scheduled exchange rate update service.
     * 
     * @param portfolioId The portfolio ID
     * @return Response with updated portfolio information
     */
    @POST
    @Path("/{portfolioId}/updatePrices")
    @Produces(MediaType.APPLICATION_JSON)
    public Response updatePrices(@PathParam("portfolioId") String portfolioId) {
        try {
            logger.info("Manual price update requested for portfolio: " + portfolioId);
            
            // Get the cached Client for this portfolio
            Client client = portfolioFileService.getPortfolio(portfolioId);
            
            if (client == null) {
                logger.warn("No cached client found for portfolio: " + portfolioId);
                return createPreconditionRequiredResponse(
                    "PORTFOLIO_NOT_LOADED", 
                    "Portfolio must be opened first before updating prices");
            }
            
            // Initialize API keys from preferences before running the update job
            // This is necessary because the E4 dependency injection (Preference2EnvAddon)
            // may not have been triggered in the REST API context
            logger.info("Initializing API keys from preferences");
            QuoteFeedApiKeyService.initializeApiKeys();
            
            // Use the shared update logic from ScheduledPriceUpdateService
            // This updates exchange rates, prices, and sets lastPriceUpdateTime
            priceUpdateService.updatePortfolioPricesAndExchangeRates(portfolioId);
            
            logger.info("Price and exchange rate update completed successfully");
            
            // Get the updated portfolio info with full client data from cache
            PortfolioFileInfo fileInfo = portfolioFileService.getFullPortfolioInfo(portfolioId);
            
            return Response.ok(fileInfo).build();
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Price update job was interrupted for portfolio " + portfolioId + ": " + e.getMessage(), e);
            return createErrorResponse(Response.Status.INTERNAL_SERVER_ERROR, 
                "Price update interrupted", 
                e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error updating prices for portfolio " + portfolioId + ": " + e.getMessage(), e);
            return createErrorResponse(Response.Status.INTERNAL_SERVER_ERROR, 
                "Internal server error", 
                e.getMessage());
        }
    }
    
    /**
     * Get the last price update timestamp for a portfolio.
     * 
     * This endpoint returns the timestamp of the last time prices were updated,
     * either through the scheduled update service or manual update.
     * 
     * @param portfolioId The portfolio ID
     * @return Response containing the last price update timestamp
     */
    @GET
    @Path("/{portfolioId}/lastPriceUpdateTime")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getLastPriceUpdateTime(@PathParam("portfolioId") String portfolioId) {
        try {
            logger.debug("Getting last price update time for portfolio: " + portfolioId);
            
            // Get the cached Client for this portfolio
            Client client = portfolioFileService.getPortfolio(portfolioId);
            
            if (client == null) {
                logger.warn("No cached client found for portfolio: " + portfolioId);
                return createPreconditionRequiredResponse(
                    "PORTFOLIO_NOT_LOADED", 
                    "Portfolio must be opened first before accessing last price update time");
            }
            
            // Get the lastPriceUpdateTime property
            String lastUpdateTime = client.getProperty("lastPriceUpdateTime");
            
            // Create response
            Map<String, Object> response = new HashMap<>();
            response.put("portfolioId", portfolioId);
            
            if (lastUpdateTime != null && !lastUpdateTime.isEmpty()) {
                response.put("lastPriceUpdateTime", lastUpdateTime);
                response.put("hasBeenUpdated", true);
            } else {
                response.put("lastPriceUpdateTime", null);
                response.put("hasBeenUpdated", false);
                response.put("message", "Prices have not been updated yet");
            }
            
            return Response.ok(response).build();
            
        } catch (Exception e) {
            logger.error("Unexpected error getting last price update time for portfolio " + portfolioId + ": " + e.getMessage(), e);
            return createErrorResponse(Response.Status.INTERNAL_SERVER_ERROR, 
                "Internal server error", 
                e.getMessage());
        }
    }
    
    /**
     * Get the total portfolio value (current valuation).
     * 
     * This endpoint returns the current total value of the portfolio,
     * calculated as the sum of all account balances and security positions.
     * 
     * @param portfolioId The portfolio ID
     * @return Response containing the total portfolio value
     */
    @GET
    @Path("/{portfolioId}/totalValue")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getTotalValue(@PathParam("portfolioId") String portfolioId) {
        try {
            logger.info("Getting total value for portfolio: {}", portfolioId);
            
            // Get the cached Client for this portfolio
            Client client = portfolioFileService.getPortfolio(portfolioId);
            
            if (client == null) {
                logger.warn("No cached client found for portfolio: {}", portfolioId);
                return createPreconditionRequiredResponse(
                    "PORTFOLIO_NOT_LOADED", 
                    "Portfolio must be opened first before accessing total value");
            }
            
            // Create currency converter
            ExchangeRateProviderFactory factory = new ExchangeRateProviderFactory(client);
            CurrencyConverter converter = new CurrencyConverterImpl(factory, client.getBaseCurrency());
            java.time.LocalDate today = java.time.LocalDate.now();
            
            // Create client snapshot to get total value
            ClientSnapshot snapshot = ClientSnapshot.create(client, converter, today);
            Money totalValue = snapshot.getMonetaryAssets();
            
            // Convert from internal representation (multiplied by 100) to decimal
            double totalValueDecimal = totalValue.getAmount() / Values.Money.factor();
            
            // Create response
            Map<String, Object> response = new HashMap<>();
            response.put("portfolioId", portfolioId);
            response.put("totalValue", totalValueDecimal);
            response.put("currencyCode", totalValue.getCurrencyCode());
            response.put("date", today.toString());
            
            logger.info("Total value for portfolio {}: {} {}", portfolioId, totalValueDecimal, totalValue.getCurrencyCode());
            
            return Response.ok(response).build();
            
        } catch (Exception e) {
            logger.error("Unexpected error getting total value for portfolio {}: {}", 
                portfolioId, e.getMessage(), e);
            return createErrorResponse(Response.Status.INTERNAL_SERVER_ERROR, 
                "Internal server error", 
                e.getMessage());
        }
    }
    
    /**
     * Get portfolio performance calculation breakdown.
     * 
     * This endpoint returns a detailed breakdown of portfolio performance over a specified period,
     * including initial value, capital gains, earnings, fees, taxes, transfers, and final value.
     * 
     * @param portfolioId The portfolio ID
     * @param reportingPeriodCode Optional reporting period code (e.g., "X" for Year-to-Date, "F2024-01-01_2024-12-31" for custom range).
     *                            If not provided, defaults to Year-to-Date (YTD).
     * @param startDate Optional start date (ISO format: YYYY-MM-DD). Used if reportingPeriodCode is not provided.
     * @param endDate Optional end date (ISO format: YYYY-MM-DD). Used if reportingPeriodCode is not provided.
     * @param useFifo Optional cost method flag. true for FIFO (default), false for Moving Average.
     * @return Response containing performance calculation breakdown
     */
    @GET
    @Path("/{portfolioId}/performanceCalculation")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPerformanceCalculation(
            @PathParam("portfolioId") String portfolioId,
            @QueryParam("reportingPeriodCode") String reportingPeriodCode,
            @QueryParam("startDate") String startDate,
            @QueryParam("endDate") String endDate,
            @QueryParam("useFifo") Boolean useFifo) {
        try {
            logger.info("Getting performance calculation for portfolio: {}", portfolioId);
            
            // Get the cached Client for this portfolio
            Client client = portfolioFileService.getPortfolio(portfolioId);
            
            if (client == null) {
                logger.warn("No cached client found for portfolio: {}", portfolioId);
                return createPreconditionRequiredResponse(
                    "PORTFOLIO_NOT_LOADED", 
                    "Portfolio must be opened first before accessing performance calculation");
            }
            
            // Determine reporting period
            Interval reportingPeriod;
            if (reportingPeriodCode != null && !reportingPeriodCode.trim().isEmpty()) {
                try {
                    ReportingPeriod period = ReportingPeriod.from(reportingPeriodCode);
                    reportingPeriod = period.toInterval(LocalDate.now());
                } catch (Exception e) {
                    logger.warn("Failed to parse reporting period code: {}", reportingPeriodCode, e);
                    return createErrorResponse(Response.Status.BAD_REQUEST,
                        "INVALID_REPORTING_PERIOD",
                        "Invalid reporting period code: " + reportingPeriodCode);
                }
            } else if (startDate != null && endDate != null) {
                try {
                    LocalDate start = LocalDate.parse(startDate);
                    LocalDate end = LocalDate.parse(endDate);
                    reportingPeriod = Interval.of(start, end);
                } catch (Exception e) {
                    logger.warn("Failed to parse dates: startDate={}, endDate={}", startDate, endDate, e);
                    return createErrorResponse(Response.Status.BAD_REQUEST,
                        "INVALID_DATE_FORMAT",
                        "Invalid date format. Use ISO format: YYYY-MM-DD");
                }
            } else {
                // Default to Year-to-Date (YTD)
                try {
                    ReportingPeriod ytdPeriod = ReportingPeriod.from("X");
                    reportingPeriod = ytdPeriod.toInterval(LocalDate.now());
                } catch (Exception e) {
                    logger.warn("Failed to create YTD period, falling back to last year", e);
                    // Fallback to last year if YTD fails
                    LocalDate end = LocalDate.now();
                    LocalDate start = end.minusYears(1);
                    reportingPeriod = Interval.of(start, end);
                }
            }
            
            // Determine cost method (default to FIFO)
            boolean useFifoMethod = useFifo == null || useFifo;
            
            // Create currency converter
            ExchangeRateProviderFactory factory = new ExchangeRateProviderFactory(client);
            CurrencyConverter converter = new CurrencyConverterImpl(factory, client.getBaseCurrency());
            
            // Calculate performance index
            PerformanceIndex index = PerformanceIndex.forClient(client, converter, reportingPeriod, new ArrayList<>());
            
            // Get performance snapshot
            ClientPerformanceSnapshot snapshot = index.getClientPerformanceSnapshot(useFifoMethod)
                    .orElseThrow(() -> new IllegalStateException("Unable to calculate performance snapshot"));
            
            // Build DTO
            PerformanceCalculationDto dto = new PerformanceCalculationDto();
            dto.setCurrencyCode(client.getBaseCurrency());
            dto.setCostMethod(useFifoMethod ? "FIFO" : "MOVING_AVERAGE");
            
            // Extract values from categories
            Money initialValue = snapshot.getValue(CategoryType.INITIAL_VALUE);
            Money capitalGains = snapshot.getValue(CategoryType.CAPITAL_GAINS);
            Money forexCapitalGains = snapshot.getValue(CategoryType.FOREX_CAPITAL_GAINS);
            Money realizedCapitalGains = snapshot.getValue(CategoryType.REALIZED_CAPITAL_GAINS);
            Money earnings = snapshot.getValue(CategoryType.EARNINGS);
            Money fees = snapshot.getValue(CategoryType.FEES);
            Money taxes = snapshot.getValue(CategoryType.TAXES);
            Money currencyGains = snapshot.getValue(CategoryType.CURRENCY_GAINS);
            Money transfers = snapshot.getValue(CategoryType.TRANSFERS);
            Money finalValue = snapshot.getValue(CategoryType.FINAL_VALUE);
            
            // Set initial value
            dto.setInitialValue(createMoneyValueDto(initialValue, client.getBaseCurrency()));
            dto.setInitialValueDate(snapshot.getStartClientSnapshot().getTime());
            
            // Set gains and earnings
            dto.setCapitalGains(createMoneyValueDto(capitalGains, client.getBaseCurrency()));
            dto.setForexCapitalGains(createMoneyValueDto(forexCapitalGains, client.getBaseCurrency()));
            dto.setRealizedCapitalGains(createMoneyValueDto(realizedCapitalGains, client.getBaseCurrency()));
            dto.setEarnings(createMoneyValueDto(earnings, client.getBaseCurrency()));
            
            // Set fees and taxes (negative values)
            dto.setFees(createMoneyValueDto(fees, client.getBaseCurrency()));
            dto.setTaxes(createMoneyValueDto(taxes, client.getBaseCurrency()));
            
            // Set currency gains and transfers
            dto.setCashCurrencyGains(createMoneyValueDto(currencyGains, client.getBaseCurrency()));
            dto.setPerformanceNeutralTransfers(createMoneyValueDto(transfers, client.getBaseCurrency()));
            
            // Set final value
            dto.setFinalValue(createMoneyValueDto(finalValue, client.getBaseCurrency()));
            dto.setFinalValueDate(snapshot.getEndClientSnapshot().getTime());
            
            // Set accumulated performance percentages
            PerformanceCalculationDto.AccumulatedPerformanceDto accumulatedPerformance = 
                new PerformanceCalculationDto.AccumulatedPerformanceDto();
            accumulatedPerformance.setTotal(index.getFinalAccumulatedPercentage());
            accumulatedPerformance.setTotalAnnualized(index.getFinalAccumulatedAnnualizedPercentage());
            accumulatedPerformance.setUnrealizedCapitalGains(index.getFinalAccumulatedUnrealizedCapitalGainsPercentage());
            accumulatedPerformance.setUnrealizedCapitalGainsAnnualized(index.getFinalAccumulatedUnrealizedCapitalGainsAnnualizedPercentage());
            accumulatedPerformance.setRealizedCapitalGains(index.getFinalAccumulatedRealizedCapitalGainsPercentage());
            accumulatedPerformance.setRealizedCapitalGainsAnnualized(index.getFinalAccumulatedRealizedCapitalGainsAnnualizedPercentage());
            accumulatedPerformance.setForexGains(index.getFinalAccumulatedForexGainsPercentage());
            accumulatedPerformance.setForexGainsAnnualized(index.getFinalAccumulatedForexGainsAnnualizedPercentage());
            accumulatedPerformance.setEarnings(index.getFinalAccumulatedEarningsPercentage());
            accumulatedPerformance.setEarningsAnnualized(index.getFinalAccumulatedEarningsAnnualizedPercentage());
            dto.setAccumulatedPerformance(accumulatedPerformance);
            
            logger.info("Performance calculation completed for portfolio: {}", portfolioId);
            
            return Response.ok(dto).build();
            
        } catch (IllegalStateException e) {
            logger.error("Failed to calculate performance snapshot for portfolio {}: {}", 
                portfolioId, e.getMessage(), e);
            return createErrorResponse(Response.Status.INTERNAL_SERVER_ERROR,
                "CALCULATION_ERROR",
                "Failed to calculate performance snapshot: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error getting performance calculation for portfolio {}: {}", 
                portfolioId, e.getMessage(), e);
            return createErrorResponse(Response.Status.INTERNAL_SERVER_ERROR, 
                "Internal server error", 
                e.getMessage());
        }
    }
    
    /**
     * Resolve the required file path/name from a request.
     */
    private String getRequestedPath(PortfolioFileRequest request) {
        String requestedPath = getOptionalRequestedPath(request);
        if (requestedPath == null) {
            throw new IllegalArgumentException("Portfolio file name or path is required");
        }

        return requestedPath;
    }

    /**
     * Resolve the optional file path/name from a request.
     */
    private String getOptionalRequestedPath(PortfolioFileRequest request) {
        if (request == null) {
            return null;
        }

        if (request.getPath() != null && !request.getPath().trim().isEmpty()) {
            return request.getPath();
        }

        if (request.getName() != null && !request.getName().trim().isEmpty()) {
            return request.getName();
        }

        return null;
    }
    
    /**
     * Helper method to create MoneyValueDto from Money.
     */
    private MoneyValueDto createMoneyValueDto(Money money, String baseCurrency) {
        double rawValue = money.getAmount() / Values.Money.factor();
        String formatted = Values.Money.format(money, baseCurrency);
        return new MoneyValueDto(formatted, rawValue);
    }
    
}
