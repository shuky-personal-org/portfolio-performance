package name.abuchen.portfolio.ui.api.service;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.ClientFactory;
import name.abuchen.portfolio.model.ClientFileType;
import name.abuchen.portfolio.model.ClientProperties;
import name.abuchen.portfolio.model.ConfigurationSet.WellKnownConfigurationSets;
import name.abuchen.portfolio.model.SaveFlag;
import name.abuchen.portfolio.money.CurrencyConverter;
import name.abuchen.portfolio.money.CurrencyConverterImpl;
import name.abuchen.portfolio.money.CurrencyUnit;
import name.abuchen.portfolio.money.ExchangeRate;
import name.abuchen.portfolio.money.ExchangeRateProviderFactory;
import name.abuchen.portfolio.snapshot.ReportingPeriod;
import name.abuchen.portfolio.ui.api.dto.CurrencyConversionDto;
import name.abuchen.portfolio.ui.api.dto.PortfolioFileInfo;
import name.abuchen.portfolio.ui.api.dto.ReportingPeriodDto;

import org.eclipse.core.runtime.IProgressMonitor;

/**
 * Service for handling portfolio file operations.
 * 
 * This service provides functionality to open, load, and manage portfolio files
 * using the Portfolio Performance core modules.
 */
public class PortfolioFileService {
    
    private static final Logger logger = LoggerFactory.getLogger(PortfolioFileService.class);
    private static final String DEFAULT_PORTFOLIO_EXTENSION = ".portfolio";
    private static final String XML_EXTENSION = ".xml";
    private static final String DELETED_DIRECTORY_NAME = "deleted";
    private static final Set<String> SUPPORTED_TWS_INSTANCE_IDS = Set.of(
                    ClientProperties.DEFAULT_TWS_INSTANCE_ID, "primary", "secondary");
    
    // Singleton instance
    private static PortfolioFileService instance;
    
    // Portfolio directory configuration
    private final Path portfolioDirectory;
    
    // Cache for loaded clients to avoid reloading the same file
    private final Map<String, Client> clientCache = new ConcurrentHashMap<>();
    
    /**
     * Get the singleton instance of PortfolioFileService.
     *
     * @return The singleton instance
     */
    public static synchronized PortfolioFileService getInstance() {
        if (instance == null) {
            instance = new PortfolioFileService();
        }
        return instance;
    }
    
    /**
     * Constructor that initializes the portfolio directory from environment variable or system property.
     */
    public PortfolioFileService() {
        this(getDefaultPortfolioDirectory());
    }
    
    /**
     * Constructor with explicit portfolio directory.
     * 
     * @param portfolioDir Portfolio directory path
     */
    public PortfolioFileService(String portfolioDir) {
        this.portfolioDirectory = Paths.get(portfolioDir).toAbsolutePath().normalize();
        logger.info("Portfolio directory set to: {}", this.portfolioDirectory);
        
        // Ensure directory exists
        if (!Files.exists(this.portfolioDirectory)) {
            try {
                Files.createDirectories(this.portfolioDirectory);
                logger.info("Created portfolio directory: {}", this.portfolioDirectory);
            } catch (IOException e) {
                logger.error("Failed to create portfolio directory: {}", this.portfolioDirectory, e);
            }
        }
    }
    
    /**
     * Get the default portfolio directory from environment variable or system property.
     * 
     * @return Default portfolio directory path
     */
    private static String getDefaultPortfolioDirectory() {
        String portfolioDir = System.getenv("PORTFOLIO_DIR");
        if (portfolioDir == null) {
            portfolioDir = System.getProperty("portfolio.dir");
        }
        if (portfolioDir == null) {
            // Default to current working directory
            portfolioDir = System.getProperty("user.dir");
        }
        return portfolioDir;
    }
    
    /**
     * Open and load a portfolio file from the given relative path.
     * The path is resolved against the portfolio directory.
     * 
     * @param relativePath The relative path to the portfolio file (relative to portfolio directory)
     * @param password Optional password for encrypted files
     * @return PortfolioFileInfo containing file information
     * @throws IOException if the file cannot be read
     * @throws FileNotFoundException if the file does not exist
     */
    public PortfolioFileInfo openFile(String relativePath, char[] password) throws IOException {
        logger.info("Opening portfolio file: {} (relative to: {})", relativePath, portfolioDirectory);
        
        File file = validateAndResolveFilePath(relativePath);
        validateFileAccess(file, relativePath, password);
        
        String fileId = generateFileId(relativePath);
        Client client = loadClient(file, fileId, relativePath, password);
        
        PortfolioFileInfo fileInfo = createBasicFileInfo(file, relativePath, fileId);
        populateClientData(fileInfo, client, file);
        
        return fileInfo;
    }
    
    /**
     * Validates and resolves the file path.
     * 
     * @param relativePath The relative path to validate
     * @return The resolved File object
     * @throws IOException if validation fails
     */
    private File validateAndResolveFilePath(String relativePath) throws IOException {
        Path path = resolveRelativePath(relativePath);
        
        if (!Files.exists(path)) {
            throw new FileNotFoundException("Portfolio file not found: " + relativePath + " (resolved to: " + path + ")");
        }
        
        return path.toFile();
    }
    
    /**
     * Validates file access requirements (encryption, password).
     * 
     * @param file The file to validate
     * @param relativePath The relative path (for error messages)
     * @param password The provided password
     * @throws IOException if access validation fails
     */
    private void validateFileAccess(File file, String relativePath, char[] password) throws IOException {
        if (ClientFactory.isEncrypted(file) && password == null) {
            throw new IOException("Password required for encrypted file: " + relativePath);
        }
    }
    
    /**
     * Loads the client from cache or from file.
     * 
     * @param file The file to load
     * @param fileId The file ID for caching
     * @param relativePath The relative path (for logging)
     * @param password The password for encrypted files
     * @return The loaded Client
     * @throws IOException if loading fails
     */
    private Client loadClient(File file, String fileId, String relativePath, char[] password) throws IOException {
        try {
            logger.info("Loading portfolio file: {} with ClientFactory", file.getAbsolutePath());
            
            MinimalProgressMonitor monitor = new MinimalProgressMonitor();
            Client client = ClientFactory.load(file, password, monitor);
            
            clientCache.put(fileId, client);
            logger.info("Successfully loaded portfolio file: {}", relativePath);
            
            return client;
        } catch (Exception e) {
            logger.error("Failed to load portfolio file: {}", relativePath, e);
            throw new IOException("Failed to process portfolio file: " + e.getMessage(), e);
        }
    }
    
    /**
     * Creates basic file information.
     * 
     * @param file The file
     * @param relativePath The relative path
     * @param fileId The file ID
     * @return PortfolioFileInfo with basic information
     */
    private PortfolioFileInfo createBasicFileInfo(File file, String relativePath, String fileId) {
        PortfolioFileInfo fileInfo = new PortfolioFileInfo();
        fileInfo.setId(fileId);
        fileInfo.setName(extractName(relativePath));
        fileInfo.setPath(relativePath);
        fileInfo.setLastModified(LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(file.lastModified()),
            ZoneId.systemDefault()
        ));
        fileInfo.setEncrypted(ClientFactory.isEncrypted(file));
        fileInfo.setTwsInstanceId(ClientProperties.DEFAULT_TWS_INSTANCE_ID);
        return fileInfo;
    }
    
    /**
     * Sets base currency on file info from cache or a lightweight read of the file header.
     */
    private void enrichBaseCurrencyFromFile(PortfolioFileInfo fileInfo, File file) {
        Client cached = clientCache.get(fileInfo.getId());
        if (cached != null) {
            fileInfo.setBaseCurrency(cached.getBaseCurrency());
            fileInfo.setTwsInstanceId(new ClientProperties(cached).getTwsInstanceId());
            return;
        }

        // Only attempt to read XML files - binary .portfolio files are compressed
        // and cannot be read as text
        String fileName = file.getName().toLowerCase();
        if (!fileName.endsWith(XML_EXTENSION)) {
            return;
        }

        // Read only the first few KB to find the baseCurrency tag near the header
        int bufferSize = 4096;
        try (var reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            char[] buffer = new char[bufferSize];
            int charsRead = reader.read(buffer, 0, bufferSize);
            if (charsRead <= 0)
                return;

            String content = new String(buffer, 0, charsRead);
            String openTag = "<baseCurrency>";
            int start = content.indexOf(openTag);
            if (start < 0)
                return;

            int valueStart = start + openTag.length();
            int end = content.indexOf("</baseCurrency>", valueStart);
            if (end <= valueStart)
                return;

            String currency = content.substring(valueStart, end).trim();
            if (!currency.isEmpty())
                fileInfo.setBaseCurrency(currency);
        } catch (IOException e) {
            logger.debug("Could not read base currency from {}", file.getName(), e);
        }
    }

    /**
     * Populates the file info with client-specific data.
     * 
     * @param fileInfo The file info to populate
     * @param client The loaded client (may be null)
     * @param file The file
     */
    private void populateClientData(PortfolioFileInfo fileInfo, Client client, File file) {
        if (client == null) {
            setDefaultClientData(fileInfo);
            return;
        }
        
        fileInfo.setBaseCurrency(client.getBaseCurrency());
        fileInfo.setTimezone(ZoneId.systemDefault().getId());
        fileInfo.setVersion(client.getFileVersionAfterRead());
        fileInfo.setClientLoaded(true);
        fileInfo.setClientInfo("Client loaded successfully");
        fileInfo.setTwsInstanceId(new ClientProperties(client).getTwsInstanceId());
        
        // Set counts only (detailed data available via specialized controllers)
        setCountsOnly(fileInfo, client);
        
        // Load only utility data (not available via other controllers)
        loadReportingPeriods(fileInfo, client, file);
        loadCurrencyConversions(fileInfo, client);
    }
    
    /**
     * Sets default values when client is not available.
     * 
     * @param fileInfo The file info to populate with defaults
     */
    private void setDefaultClientData(PortfolioFileInfo fileInfo) {
        fileInfo.setBaseCurrency("EUR");
        fileInfo.setVersion(0);
        fileInfo.setSecuritiesCount(0);
        fileInfo.setAccountsCount(0);
        fileInfo.setPortfoliosCount(0);
        fileInfo.setTransactionsCount(0);
        fileInfo.setTaxonomiesCount(0);
        fileInfo.setClientLoaded(false);
        fileInfo.setClientInfo("Client creation failed - OSGi dependencies not available");
    }
    
    /**
     * Sets only count properties from client data.
     * Detailed data should be retrieved via specialized controllers.
     * 
     * @param fileInfo The file info to populate with counts
     * @param client The loaded client
     */
    private void setCountsOnly(PortfolioFileInfo fileInfo, Client client) {
        // Count securities (excluding retired and options contracts to match SecurityController)
        long securitiesCount = client.getSecurities().stream()
            .filter(s -> !s.isRetired()) // Skip retired securities
            .filter(s -> {
                // Skip options contracts (ticker symbol matching pattern: 6 digits + C/P + 8 digits)
                String tickerSymbol = s.getTickerSymbol();
                return tickerSymbol == null || !tickerSymbol.replaceAll("\\s+", "").matches(".*\\d{6}[CP]\\d{8}");
            })
            .count();
        
        fileInfo.setSecuritiesCount((int) securitiesCount);
        fileInfo.setAccountsCount(client.getAccounts().size());
        fileInfo.setPortfoliosCount(client.getPortfolios().size());
        fileInfo.setTransactionsCount(client.getAllTransactions().size());
        fileInfo.setTaxonomiesCount(client.getTaxonomies().size());
        
        logger.info("Set counts: {} securities, {} accounts, {} portfolios, {} transactions, {} taxonomies",
            securitiesCount, client.getAccounts().size(), client.getPortfolios().size(), 
            client.getAllTransactions().size(), client.getTaxonomies().size());
    }
    
    /**
     * Loads and converts reporting periods directly from client configuration.
     * 
     * @param fileInfo The file info to populate
     * @param client The loaded client
     * @param file The file (unused, kept for signature compatibility)
     */
    private void loadReportingPeriods(PortfolioFileInfo fileInfo, Client client, File file) {
        try {
            logger.info("Loading reporting periods from client configuration");
            
            // Load periods from client settings configuration set
            List<ReportingPeriod> periods = client.getSettings()
                    .getConfigurationSet(WellKnownConfigurationSets.REPORTING_PERIODS)
                    .getConfigurations()
                    .map(c -> parseReportingPeriod(c.getData()))
                    .filter(java.util.Optional::isPresent)
                    .map(java.util.Optional::get)
                    .collect(Collectors.toList());
            
            // If no periods found, use defaults
            if (periods.isEmpty()) {
                logger.info("No reporting periods found in configuration, using defaults");
                periods = getDefaultReportingPeriods();
            }
            
            // Convert to DTOs
            List<ReportingPeriodDto> periodDtos = convertReportingPeriodsToDto(periods);
            fileInfo.setReportingPeriods(periodDtos);
            logger.info("Set {} reporting periods in fileInfo", periodDtos.size());
            
        } catch (Exception e) {
            logger.error("Failed to load reporting periods", e);
            // Continue without reporting periods
        }
    }
    
    /**
     * Parses a reporting period from its string code.
     * 
     * @param code The reporting period code
     * @return Optional containing the ReportingPeriod if valid
     */
    private java.util.Optional<ReportingPeriod> parseReportingPeriod(String code) {
        try {
            return java.util.Optional.of(ReportingPeriod.from(code));
        } catch (IOException | RuntimeException e) {
            logger.warn("Failed to parse reporting period code: {}", code, e);
            return java.util.Optional.empty();
        }
    }
    
    /**
     * Gets default reporting periods (last 1, 2, and 3 years).
     * 
     * @return List of default ReportingPeriod objects
     */
    private List<ReportingPeriod> getDefaultReportingPeriods() {
        List<ReportingPeriod> defaults = new ArrayList<>();
        for (int ii = 1; ii <= 3; ii++) {
            defaults.add(new ReportingPeriod.LastX(ii, 0));
        }
        return defaults;
    }
    
    /**
     * Converts ReportingPeriod objects to DTOs.
     * 
     * @param periods The reporting periods to convert
     * @return List of ReportingPeriodDto
     */
    private List<ReportingPeriodDto> convertReportingPeriodsToDto(List<ReportingPeriod> periods) {
        List<ReportingPeriodDto> periodDtos = new ArrayList<>();
        
        for (ReportingPeriod period : periods) {
            ReportingPeriodDto dto = new ReportingPeriodDto();
            dto.setCode(period.getCode());
            dto.setLabel(period.toString());
            
            try {
                var interval = period.toInterval(LocalDate.now());
                dto.setStartDate(interval.getStart());
                dto.setEndDate(interval.getEnd());
            } catch (Exception e) {
                logger.warn("Failed to get interval for reporting period: {}", period.getCode(), e);
            }
            
            periodDtos.add(dto);
            logger.debug("Converted ReportingPeriod: code={}, label={}", dto.getCode(), dto.getLabel());
        }
        
        return periodDtos;
    }
    
    
    /**
     * Loads and converts currency conversion rates for today.
     * Collects all unique currencies used in accounts and securities,
     * and retrieves today's exchange rates to the base currency.
     * 
     * @param fileInfo The file info to populate
     * @param client The loaded client
     */
    private void loadCurrencyConversions(PortfolioFileInfo fileInfo, Client client) {       
        List<CurrencyConversionDto> conversionDtos = new ArrayList<>();
        Set<String> currencies = new HashSet<>();
        String baseCurrency = client.getBaseCurrency();
        LocalDate today = LocalDate.now();
        
        // Collect currencies from accounts
        for (name.abuchen.portfolio.model.Account account : client.getAccounts()) {
            String currencyCode = account.getCurrencyCode();
            if (currencyCode != null && !currencyCode.isEmpty() && !currencyCode.equals(baseCurrency)) {
                currencies.add(currencyCode);
            }
        }
        
        // Collect currencies from securities
        for (name.abuchen.portfolio.model.Security security : client.getSecurities()) {
            String currencyCode = security.getCurrencyCode();
            if (currencyCode != null && !currencyCode.isEmpty() && !currencyCode.equals(baseCurrency)) {
                currencies.add(currencyCode);
            }
        }
        
        // Create currency converter
        ExchangeRateProviderFactory factory = new ExchangeRateProviderFactory(client);
        CurrencyConverter converter = new CurrencyConverterImpl(factory, baseCurrency);
        
        // Get exchange rates for each currency
        for (String currency : currencies) {
            try {
                ExchangeRate rate = converter.getRate(today, currency);
                
                CurrencyConversionDto dto = new CurrencyConversionDto();
                dto.setFromCurrency(currency);
                dto.setToCurrency(baseCurrency);
                dto.setRate(rate.getValue().doubleValue());
                dto.setDate(rate.getTime());
                
                conversionDtos.add(dto);
                logger.debug("Added currency conversion: {} to {} = {}", currency, baseCurrency, rate.getValue());
            } catch (Exception e) {
                logger.warn("Failed to get exchange rate for {} to {}: {}", currency, baseCurrency, e.getMessage());
            }
        }
        
        // Sort by currency code for consistent ordering
        conversionDtos.sort((c1, c2) -> c1.getFromCurrency().compareTo(c2.getFromCurrency()));
        
        fileInfo.setCurrencyConversions(conversionDtos);
        logger.info("Set {} currency conversions in fileInfo", conversionDtos.size());
    }
    
    /**
     * Get basic information about a portfolio file without loading it.
     * 
     * @param relativePath The relative path to the portfolio file
     * @return PortfolioFileInfo with basic file information
     * @throws IOException if the file cannot be accessed
     */
    public PortfolioFileInfo getFileInfo(String relativePath) throws IOException {
        logger.info("Getting file info for: {} (relative to: {})", relativePath, portfolioDirectory);
        
        Path path = resolveRelativePath(relativePath);
        
        if (!Files.exists(path)) {
            throw new FileNotFoundException("Portfolio file not found: " + relativePath + " (resolved to: " + path + ")");
        }
        
        File file = path.toFile();
        return createBasicFileInfo(file, relativePath, generateFileId(relativePath));
    }
    
    /**
     * List all portfolio files in the portfolio directory.
     * 
     * @return List of PortfolioFileInfo for all portfolio files found
     * @throws IOException if the directory cannot be read
     */
    public List<PortfolioFileInfo> listPortfolioFiles() throws IOException {
        logger.info("Listing portfolio files in directory: {}", portfolioDirectory);
        
        List<PortfolioFileInfo> files = new ArrayList<>();
        
        if (!Files.exists(portfolioDirectory)) {
            logger.warn("Portfolio directory does not exist: {}", portfolioDirectory);
            return files;
        }
        
        try (Stream<Path> paths = Files.walk(portfolioDirectory)) {
            paths.filter(Files::isRegularFile)
                 .filter(path -> !isInDeletedDirectory(path))
                 .filter(this::isPortfolioFile)
                 .forEach(path -> {
                     try {
                         File file = path.toFile();
                         
                         // Calculate relative path
                         String relativePath = portfolioDirectory.relativize(path).toString();
                         
                         PortfolioFileInfo fileInfo = createBasicFileInfo(file, relativePath, generateFileId(relativePath));
                         enrichBaseCurrencyFromFile(fileInfo, file);
                         
                         files.add(fileInfo);
                         
                     } catch (Exception e) {
                         logger.warn("Error processing file: {}", path, e);
                     }
                 });
        }
        
        logger.info("Found {} portfolio files", files.size());
        return files;
    }

    /**
     * Create a new empty portfolio file.
     * 
     * @param requestedPath The requested relative path or name
     * @return PortfolioFileInfo for the created file
     * @throws IOException if the portfolio file cannot be created
     */
    public PortfolioFileInfo createPortfolioFile(String requestedPath) throws IOException {
        String targetRelativePath = normalizeRequestedPortfolioPath(requestedPath, DEFAULT_PORTFOLIO_EXTENSION);
        Path targetPath = resolveNewPortfolioPath(targetRelativePath);

        logger.info("Creating portfolio file: {}", targetPath);

        Files.createDirectories(targetPath.getParent());

        var client = new Client();
        var flags = getSaveFlagsForNewFile(targetPath);
        ClientFactory.saveAs(client, targetPath.toFile(), null, flags);

        String relativePath = portfolioDirectory.relativize(targetPath).toString();
        String fileId = generateFileId(relativePath);
        clientCache.put(fileId, client);

        var fileInfo = createBasicFileInfo(targetPath.toFile(), relativePath, fileId);
        populateClientData(fileInfo, client, targetPath.toFile());

        return fileInfo;
    }

    /**
     * Duplicate an existing portfolio file.
     * 
     * @param fileId The ID of the portfolio file to duplicate
     * @param requestedPath Optional requested relative path or name for the duplicate
     * @return PortfolioFileInfo for the duplicated file
     * @throws IOException if the portfolio file cannot be duplicated
     */
    public PortfolioFileInfo duplicatePortfolioFile(String fileId, String requestedPath) throws IOException {
        String sourceRelativePath = findFileById(fileId);
        Path sourcePath = resolveRelativePath(sourceRelativePath);

        String targetRelativePath = requestedPath == null || requestedPath.trim().isEmpty()
                        ? createDefaultDuplicateRelativePath(sourcePath)
                        : normalizeRequestedPortfolioPath(requestedPath, getFileExtension(sourcePath));
        Path targetPath = resolveNewPortfolioPath(targetRelativePath);

        logger.info("Duplicating portfolio file {} to {}", sourcePath, targetPath);

        Files.createDirectories(targetPath.getParent());
        Files.copy(sourcePath, targetPath, StandardCopyOption.COPY_ATTRIBUTES);

        String relativePath = portfolioDirectory.relativize(targetPath).toString();
        return createBasicFileInfo(targetPath.toFile(), relativePath, generateFileId(relativePath));
    }

    /**
     * Rename an existing portfolio file.
     *
     * @param fileId The ID of the portfolio file to rename
     * @param requestedPath The requested relative path or name for the renamed file
     * @return PortfolioFileInfo for the renamed file
     * @throws IOException if the portfolio file cannot be renamed
     */
    public PortfolioFileInfo renamePortfolioFile(String fileId, String requestedPath) throws IOException {
        String sourceRelativePath = findFileById(fileId);
        Path sourcePath = resolveRelativePath(sourceRelativePath);
        String targetRelativePath = normalizeRequestedPortfolioPath(requestedPath, getFileExtension(sourcePath));
        Path targetPath = resolveNewPortfolioPath(targetRelativePath);

        logger.info("Renaming portfolio file {} to {}", sourcePath, targetPath);

        Files.createDirectories(targetPath.getParent());
        Files.move(sourcePath, targetPath);

        String relativePath = portfolioDirectory.relativize(targetPath).toString();
        String renamedFileId = generateFileId(relativePath);
        Client cachedClient = clientCache.remove(fileId);
        if (cachedClient != null) {
            clientCache.put(renamedFileId, cachedClient);
        }

        return createBasicFileInfo(targetPath.toFile(), relativePath, renamedFileId);
    }

    /**
     * Update the base (reporting) currency of a portfolio and persist the change.
     *
     * @param fileId The portfolio file ID
     * @param currencyCode ISO 4217 currency code
     * @param password Optional password for encrypted files when not already cached
     * @return Updated portfolio file information
     * @throws IOException if the portfolio cannot be loaded or saved
     */
    public PortfolioFileInfo updateBaseCurrency(String fileId, String currencyCode, char[] password) throws IOException {
        if (currencyCode == null || currencyCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Currency code is required");
        }

        String normalizedCurrency = currencyCode.trim().toUpperCase();
        if (!CurrencyUnit.containsCurrencyCode(normalizedCurrency)) {
            throw new IllegalArgumentException("Unsupported currency code: " + normalizedCurrency);
        }

        String relativePath = findFileById(fileId);
        File file = portfolioDirectory.resolve(relativePath).toFile();

        Client client = getPortfolio(fileId);
        if (client == null) {
            validateFileAccess(file, relativePath, password);
            client = loadClient(file, fileId, relativePath, password);
        }

        String oldCurrency = client.getBaseCurrency();
        boolean changed = !normalizedCurrency.equals(oldCurrency);
        if (changed) {
            client.setBaseCurrency(normalizedCurrency);
            client.markDirty();
            try {
                saveFile(fileId);
            } catch (IOException e) {
                // Restore old currency to keep cache consistent with file on disk
                client.setBaseCurrency(oldCurrency);
                throw e;
            }
            logger.info("Updated base currency for portfolio {} to {}", fileId, normalizedCurrency);
        } else {
            logger.info("Base currency for portfolio {} already set to {}", fileId, normalizedCurrency);
        }

        PortfolioFileInfo fileInfo = createBasicFileInfo(file, relativePath, fileId);
        populateClientData(fileInfo, client, file);
        return fileInfo;
    }

    /**
     * Update the TWS instance assignment custom attribute of a portfolio and persist the change.
     *
     * @param fileId The portfolio file ID
     * @param twsInstanceId TWS instance ID (default, primary, secondary)
     * @param password Optional password for encrypted files when not already cached
     * @return Updated portfolio file information
     * @throws IOException if the portfolio cannot be loaded or saved
     */
    public PortfolioFileInfo updateTwsInstanceId(String fileId, String twsInstanceId, char[] password)
                    throws IOException {
        String normalizedTwsInstanceId = normalizeTwsInstanceId(twsInstanceId);

        String relativePath = findFileById(fileId);
        File file = portfolioDirectory.resolve(relativePath).toFile();

        Client client = getPortfolio(fileId);
        if (client == null) {
            validateFileAccess(file, relativePath, password);
            client = loadClient(file, fileId, relativePath, password);
        }

        var properties = new ClientProperties(client);
        String oldTwsInstanceId = properties.getTwsInstanceId();
        boolean changed = !normalizedTwsInstanceId.equals(oldTwsInstanceId);
        if (changed) {
            properties.setTwsInstanceId(normalizedTwsInstanceId);
            client.markDirty();
            try {
                saveFile(fileId);
            } catch (IOException e) {
                properties.setTwsInstanceId(oldTwsInstanceId);
                throw e;
            }
            logger.info("Updated TWS instance assignment for portfolio {} to {}", fileId, normalizedTwsInstanceId);
        } else {
            logger.info("TWS instance assignment for portfolio {} already set to {}", fileId, normalizedTwsInstanceId);
        }

        PortfolioFileInfo fileInfo = createBasicFileInfo(file, relativePath, fileId);
        populateClientData(fileInfo, client, file);
        return fileInfo;
    }

    private String normalizeTwsInstanceId(String twsInstanceId) {
        if (twsInstanceId == null || twsInstanceId.trim().isEmpty()) {
            throw new IllegalArgumentException("TWS instance ID is required");
        }

        String normalized = twsInstanceId.trim();
        if (!SUPPORTED_TWS_INSTANCE_IDS.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported TWS instance ID: " + normalized);
        }

        return normalized;
    }

    /**
     * Move a portfolio file into the deleted folder.
     * 
     * @param fileId The ID of the portfolio file to remove
     * @return PortfolioFileInfo for the moved file inside the deleted folder
     * @throws IOException if the portfolio file cannot be moved
     */
    public PortfolioFileInfo removePortfolioFile(String fileId) throws IOException {
        String sourceRelativePath = findFileById(fileId);
        Path sourcePath = resolveRelativePath(sourceRelativePath);
        Path deletedPath = createUniqueDeletedPath(sourcePath);

        logger.info("Moving portfolio file {} to deleted folder: {}", sourcePath, deletedPath);

        Files.createDirectories(deletedPath.getParent());
        Files.move(sourcePath, deletedPath);
        clientCache.remove(fileId);

        String deletedRelativePath = portfolioDirectory.relativize(deletedPath).toString();
        return createBasicFileInfo(deletedPath.toFile(), deletedRelativePath, generateFileId(deletedRelativePath));
    }

    /**
     * Resolve an active portfolio file by ID for raw file operations.
     * 
     * @param fileId The ID of the portfolio file
     * @return The resolved portfolio file path
     * @throws IOException if the file cannot be resolved
     */
    public Path getPortfolioFilePath(String fileId) throws IOException {
        String relativePath = findFileById(fileId);
        Path path = resolveRelativePath(relativePath);
        if (!Files.isRegularFile(path)) {
            throw new FileNotFoundException("Portfolio file not found: " + relativePath);
        }

        return path;
    }

    private Path resolveRelativePath(String relativePath) {
        if (relativePath == null || relativePath.trim().isEmpty()) {
            throw new IllegalArgumentException("File path cannot be null or empty");
        }

        Path requestedPath = Paths.get(relativePath.trim().replace('\\', '/'));
        if (requestedPath.isAbsolute()) {
            throw new SecurityException("Access denied: absolute paths are not allowed");
        }

        Path path = portfolioDirectory.resolve(requestedPath).normalize();
        if (!path.startsWith(portfolioDirectory)) {
            throw new SecurityException("Access denied: path outside portfolio directory");
        }

        if (isInDeletedDirectory(path)) {
            throw new SecurityException("Access denied: deleted portfolio files are not active");
        }

        return path;
    }

    private Path resolveNewPortfolioPath(String relativePath) throws IOException {
        Path path = resolveRelativePath(relativePath);
        if (Files.exists(path)) {
            throw new FileAlreadyExistsException("Portfolio file already exists: " + relativePath);
        }

        if (!isPortfolioFile(path)) {
            throw new IllegalArgumentException("Portfolio file must end with .portfolio or .xml");
        }

        return path;
    }

    private String normalizeRequestedPortfolioPath(String requestedPath, String defaultExtension) {
        if (requestedPath == null || requestedPath.trim().isEmpty()) {
            throw new IllegalArgumentException("Portfolio file name or path cannot be null or empty");
        }

        Path path = Paths.get(requestedPath.trim().replace('\\', '/'));
        Path fileName = path.getFileName();
        if (fileName == null || fileName.toString().trim().isEmpty()) {
            throw new IllegalArgumentException("Portfolio file name cannot be empty");
        }

        if (isPortfolioFile(path)) {
            return path.toString();
        }

        Path parent = path.getParent();
        Path fileNameWithExtension = Paths.get(fileName + defaultExtension);
        return parent == null ? fileNameWithExtension.toString() : parent.resolve(fileNameWithExtension).toString();
    }

    private boolean isInDeletedDirectory(Path path) {
        return path.toAbsolutePath().normalize().startsWith(getDeletedDirectory());
    }

    private Path getDeletedDirectory() {
        return portfolioDirectory.resolve(DELETED_DIRECTORY_NAME).normalize();
    }

    private String createDefaultDuplicateRelativePath(Path sourcePath) {
        Path sourceParent = sourcePath.getParent();
        Path targetPath = createUniquePath(sourceParent.resolve(buildCopyFileName(sourcePath.getFileName().toString(), 0)));

        return portfolioDirectory.relativize(targetPath).toString();
    }

    private String buildCopyFileName(String fileName, int index) {
        String extension = getFileExtension(fileName);
        String baseName = fileName.substring(0, fileName.length() - extension.length());
        String copySuffix = index == 0 ? " copy" : " copy " + (index + 1);

        return baseName + copySuffix + extension;
    }

    private Path createUniqueDeletedPath(Path sourcePath) {
        Path relativePath = portfolioDirectory.relativize(sourcePath);
        return createUniquePath(getDeletedDirectory().resolve(relativePath));
    }

    private Path createUniquePath(Path requestedPath) {
        if (!Files.exists(requestedPath)) {
            return requestedPath;
        }

        Path parent = requestedPath.getParent();
        String fileName = requestedPath.getFileName().toString();
        String extension = getFileExtension(fileName);
        String baseName = fileName.substring(0, fileName.length() - extension.length());

        int index = 1;
        while (true) {
            Path candidate = parent.resolve(baseName + " " + index + extension);
            if (!Files.exists(candidate)) {
                return candidate;
            }
            index++;
        }
    }

    private String getFileExtension(Path path) {
        return getFileExtension(path.getFileName().toString());
    }

    private String getFileExtension(String fileName) {
        String lowerCaseFileName = fileName.toLowerCase();
        if (lowerCaseFileName.endsWith(XML_EXTENSION)) {
            return XML_EXTENSION;
        }
        if (lowerCaseFileName.endsWith(DEFAULT_PORTFOLIO_EXTENSION)) {
            return DEFAULT_PORTFOLIO_EXTENSION;
        }
        return DEFAULT_PORTFOLIO_EXTENSION;
    }

    private Set<SaveFlag> getSaveFlagsForNewFile(Path path) {
        if (path.getFileName().toString().toLowerCase().endsWith(XML_EXTENSION)) {
            return EnumSet.copyOf(ClientFileType.XML.getFlags());
        }

        return EnumSet.copyOf(ClientFileType.BINARY.getFlags());
    }
    
    /**
     * Check if a file is a portfolio file based on its extension.
     * 
     * @param path The file path to check
     * @return true if the file appears to be a portfolio file
     */
    private boolean isPortfolioFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();
        return fileName.endsWith(".xml") || fileName.endsWith(".portfolio");
    }
    
    /**
     * Get the portfolio directory path.
     * 
     * @return The portfolio directory path
     */
    public Path getPortfolioDirectory() {
        return portfolioDirectory;
    }
    
    /**
     * Generate a unique file ID by hashing the full relative path.
     * 
     * @param relativePath The relative path to hash
     * @return A unique file ID (SHA-256 hash)
     */
    private String generateFileId(String relativePath) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(relativePath.getBytes("UTF-8"));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException | java.io.UnsupportedEncodingException e) {
            logger.error("Failed to generate file ID for: {}", relativePath, e);
            // Fallback to a simple hash
            return String.valueOf(relativePath.hashCode());
        }
    }
    
    /**
     * Extract the name from a file path (filename without extension).
     * 
     * @param filePath The file path
     * @return The name without extension
     */
    private String extractName(String filePath) {
        String fileName = Paths.get(filePath).getFileName().toString();
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0) {
            return fileName.substring(0, lastDotIndex);
        }
        return fileName;
    }
    
    /**
     * Find a file by its ID by scanning the portfolio directory.
     * 
     * @param fileId The file ID to search for
     * @return The relative path of the file if found
     * @throws FileNotFoundException if no file with the given ID is found
     */
    public String findFileById(String fileId) throws FileNotFoundException {
        logger.info("Searching for file with ID: {}", fileId);
        
        if (fileId == null || fileId.trim().isEmpty()) {
            throw new IllegalArgumentException("File ID cannot be null or empty");
        }
        
        if (!Files.exists(portfolioDirectory)) {
            throw new FileNotFoundException("Portfolio directory does not exist: " + portfolioDirectory);
        }
        
        try (Stream<Path> paths = Files.walk(portfolioDirectory)) {
            return paths.filter(Files::isRegularFile)
                       .filter(path -> !isInDeletedDirectory(path))
                       .filter(this::isPortfolioFile)
                       .map(path -> portfolioDirectory.relativize(path).toString())
                       .filter(relativePath -> {
                           String computedId = generateFileId(relativePath);
                           return fileId.equals(computedId);
                       })
                       .findFirst()
                       .orElseThrow(() -> new FileNotFoundException("No file found with ID: " + fileId));
                       
        } catch (IOException e) {
            logger.error("Error scanning portfolio directory for file ID: {}", fileId, e);
            throw new FileNotFoundException("Error scanning portfolio directory: " + e.getMessage());
        }
    }
    
    /**
     * Open and load a portfolio file by its ID.
     * 
     * @param fileId The file ID to open
     * @param password Optional password for encrypted files
     * @return PortfolioFileInfo containing file information
     * @throws IOException if the file cannot be read
     * @throws FileNotFoundException if the file does not exist
     */
    public PortfolioFileInfo openFileById(String fileId, char[] password) throws IOException {
        logger.info("Opening portfolio file by ID: {}", fileId);
        
        // First find the file by ID
        String relativePath = findFileById(fileId);
        
        // Then open it using the existing method
        return openFile(relativePath, password);
    }
    
    /**
     * Get a cached Client by portfolio ID.
     * This method only returns clients that are already in the cache.
     * 
     * @param portfolioId The portfolio ID (file ID)
     * @return The cached Client or null if not found in cache
     */
    public Client getPortfolio(String portfolioId) {
        logger.debug("Getting cached portfolio for ID: {}", portfolioId);
        
        if (portfolioId == null || portfolioId.trim().isEmpty()) {
            logger.warn("Portfolio ID cannot be null or empty");
            return null;
        }
        
        // Use the portfolio ID directly as the cache key (without password for now)
        Client client = clientCache.get(portfolioId);
        
        if (client != null) {
            logger.debug("Found cached client for portfolio ID: {}", portfolioId);
        } else {
            logger.info("No cached client found for portfolio ID: {}", portfolioId);
        }
        
        return client;
    }
    
    /**
     * Save a portfolio file by its ID.
     * 
     * @param portfolioId The portfolio ID (file ID) to save
     * @throws IOException if the file cannot be saved
     * @throws FileNotFoundException if the portfolio file is not found
     * @throws IllegalStateException if the portfolio is not loaded in cache
     */
    public void saveFile(String portfolioId) throws IOException {
        logger.info("Saving portfolio file with ID: {}", portfolioId);
        
        // Get the cached client
        Client client = getPortfolio(portfolioId);
        if (client == null) {
            throw new IllegalStateException("Portfolio not loaded in cache: " + portfolioId);
        }
        
        // Find the file path
        String relativePath = findFileById(portfolioId);
        File file = portfolioDirectory.resolve(relativePath).toFile();
        
        // Save using ClientFactory
        ClientFactory.save(client, file);
        
        logger.info("Portfolio file saved successfully: {}", file.getAbsolutePath());
    }
    
    /**
     * Get full portfolio information from cache, including all client data.
     * This is similar to openFile but uses the cached client instead of reloading.
     * 
     * @param portfolioId The portfolio ID (file ID)
     * @return PortfolioFileInfo with complete client data
     * @throws IOException if the portfolio file cannot be found
     * @throws IllegalStateException if the portfolio is not loaded in cache
     */
    public PortfolioFileInfo getFullPortfolioInfo(String portfolioId) throws IOException {
        logger.info("Getting full portfolio info from cache for ID: {}", portfolioId);
        
        // Get the cached client
        Client client = getPortfolio(portfolioId);
        if (client == null) {
            throw new IllegalStateException("Portfolio not loaded in cache: " + portfolioId);
        }
        
        // Find the file path
        String relativePath = findFileById(portfolioId);
        File file = portfolioDirectory.resolve(relativePath).toFile();
        
        // Create basic file info
        PortfolioFileInfo fileInfo = createBasicFileInfo(file, relativePath, portfolioId);
        
        // Populate with client data (same as openFile does)
        populateClientData(fileInfo, client, file);
        
        logger.info("Full portfolio info retrieved from cache for ID: {}", portfolioId);
        
        return fileInfo;
    }
    
    /**
     * Clear the client cache.
     */
    public void clearCache() {
        logger.info("Clearing client cache");
        clientCache.clear();
    }
    
    
    /**
     * Get the IDs of all portfolios currently cached.
     * 
     * @return Set of portfolio IDs (file IDs) that are currently in the cache
     */
    public Set<String> getCachedPortfolioIds() {
        // Return a copy to prevent external modification of internal cache structure
        return new HashSet<>(clientCache.keySet());
    }
    
    /**
     * Get cache statistics.
     * 
     * @return Map containing cache statistics
     */
    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("cachedClients", clientCache.size());
        // Don't expose internal cache keys - use getCachedPortfolioIds() instead
        return stats;
    }
    
    
    /**
     * Cleanup method called when the application shuts down.
     * This ensures proper cleanup of resources.
     */
    public void shutdown() {
        logger.info("Shutting down PortfolioFileService - clearing client cache");
        clientCache.clear();
        logger.info("PortfolioFileService shutdown complete");
    }
    
    /**
     * Minimal progress monitor implementation that implements Eclipse's IProgressMonitor interface.
     */
    private static class MinimalProgressMonitor implements IProgressMonitor {
        private boolean cancelled = false;
        
        @Override
        public void beginTask(String name, int totalWork) {
            logger.debug("Starting task: {} (total work: {})", name, totalWork);
        }
        
        @Override
        public void done() {
            logger.debug("Task completed");
        }
        
        @Override
        public void internalWorked(double work) {
            // No-op for now
        }
        
        @Override
        public boolean isCanceled() {
            return cancelled;
        }
        
        @Override
        public void setCanceled(boolean value) {
            this.cancelled = value;
        }
        
        @Override
        public void setTaskName(String name) {
            logger.debug("Task: {}", name);
        }
        
        @Override
        public void subTask(String name) {
            logger.debug("Subtask: {}", name);
        }
        
        @Override
        public void worked(int work) {
            // No-op for now
        }
    }
}

