package name.abuchen.portfolio.ui.api.redis;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.ui.api.service.FlexImportService;
import name.abuchen.portfolio.ui.api.service.PortfolioFileService;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.exceptions.JedisConnectionException;

/**
 * Subscribes to {@code flex:import_ready} (published by tws-api after a Flex XML is written)
 * and imports the file into a configured portfolio via {@link FlexImportService}.
 * <p>
 * Enable with {@code FLEX_IMPORT_VIA_REDIS=true}. For a single portfolio, set
 * {@code FLEX_IMPORT_PORTFOLIO_ID}, {@code FLEX_CURRENCY_ACCOUNT_MAP} (JSON object), and
 * {@code FLEX_PORTFOLIO_UUID}. For multiple portfolios, set {@code FLEX_IMPORT_CONFIGS} to a
 * JSON object/list with per-portfolio import settings and optional {@code query_id} /
 * {@code reports_dir} matchers.
 * <p>
 * Imports only run if that portfolio is <strong>already in memory</strong> (opened via the REST API
 * or UI). Encrypted files must be opened with a password through those paths first; this listener
 * does not load portfolios from disk.
 */
public class RedisFlexImportListener
{
    private static final Logger logger = LoggerFactory.getLogger(RedisFlexImportListener.class);

    /** Must match tws_api.messaging.redis_pubsub.FLEX_IMPORT_READY_CHANNEL */
    private static final String CHANNEL_FLEX_IMPORT_READY = "flex:import_ready"; //$NON-NLS-1$
    private static final Gson GSON = new Gson();

    private final PortfolioFileService portfolioFileService;
    private final RedisConnectionConfig redisConfig;

    private volatile boolean running;
    private Thread subscriberThread;
    private volatile Jedis activeSubscriber;

    private final boolean flexImportConfigured;
    private final List<FlexImportConfig> flexImportConfigs;

    public RedisFlexImportListener()
    {
        this(PortfolioFileService.getInstance(), RedisConnectionConfig.fromEnvironment());
    }

    RedisFlexImportListener(PortfolioFileService portfolioFileService, RedisConnectionConfig redisConfig)
    {
        this.portfolioFileService = Objects.requireNonNull(portfolioFileService);
        this.redisConfig = Objects.requireNonNull(redisConfig);

        String enabledEnv = System.getenv("FLEX_IMPORT_VIA_REDIS"); //$NON-NLS-1$
        boolean envWantsImport = Boolean.parseBoolean(enabledEnv != null ? enabledEnv : "false"); //$NON-NLS-1$

        this.flexImportConfigs = loadFlexImportConfigs();
        this.flexImportConfigured = envWantsImport && !flexImportConfigs.isEmpty();

        if (envWantsImport && !flexImportConfigured)
        {
            logger.warn(
                            "FLEX_IMPORT_VIA_REDIS is true but no valid FLEX_IMPORT_CONFIGS or legacy FLEX_IMPORT_PORTFOLIO_ID/FLEX_PORTFOLIO_UUID/FLEX_CURRENCY_ACCOUNT_MAP configuration was found; flex Redis import disabled"); //$NON-NLS-1$
        }
    }

    private static String trimOrEmpty(String s)
    {
        return s == null ? "" : s.trim(); //$NON-NLS-1$
    }

    private static boolean envBool(String key, boolean defaultValue)
    {
        String v = System.getenv(key);
        if (v == null || v.isBlank())
            return defaultValue;
        return Boolean.parseBoolean(v.trim());
    }

    private static Map<String, String> parseStringMap(String json, String envName)
    {
        if (json == null || json.isBlank())
            return Map.of();
        try
        {
            var type = new TypeToken<Map<String, String>>()
            {
            }.getType();
            Map<String, String> m = GSON.fromJson(json.trim(), type);
            return m != null ? m : Map.of();
        }
        catch (JsonSyntaxException e)
        {
            logger.error("Invalid JSON for {}: {}", envName, e.getMessage()); //$NON-NLS-1$
            return Map.of();
        }
    }

    private static Map<String, String> parseStringMap(JsonElement element, String configName)
    {
        if (element == null || element.isJsonNull())
            return Map.of();

        if (element.isJsonObject())
        {
            try
            {
                var type = new TypeToken<Map<String, String>>()
                {
                }.getType();
                Map<String, String> m = GSON.fromJson(element, type);
                return m != null ? m : Map.of();
            }
            catch (JsonSyntaxException | ClassCastException e)
            {
                logger.error("Invalid JSON object for {}: {}", configName, e.getMessage()); //$NON-NLS-1$
                return Map.of();
            }
        }

        if (element.isJsonPrimitive())
            return parseStringMap(element.getAsString(), configName);

        logger.error("Invalid JSON for {}: expected object or JSON object string", configName); //$NON-NLS-1$
        return Map.of();
    }

    private List<FlexImportConfig> loadFlexImportConfigs()
    {
        String configsJson = System.getenv("FLEX_IMPORT_CONFIGS"); //$NON-NLS-1$
        if (configsJson != null && !configsJson.isBlank())
        {
            List<FlexImportConfig> configs = parseFlexImportConfigs(configsJson);
            if (!configs.isEmpty())
                return configs;

            logger.warn("FLEX_IMPORT_CONFIGS did not contain any valid entries; falling back to legacy FLEX_IMPORT_* variables"); //$NON-NLS-1$
        }

        return loadLegacyFlexImportConfig()
                        .map(List::of)
                        .orElseGet(List::of);
    }

    private Optional<FlexImportConfig> loadLegacyFlexImportConfig()
    {
        String portfolioId = trimOrEmpty(System.getenv("FLEX_IMPORT_PORTFOLIO_ID")); //$NON-NLS-1$
        String portfolioUuid = trimOrEmpty(System.getenv("FLEX_PORTFOLIO_UUID")); //$NON-NLS-1$
        Map<String, String> currencyAccountMap = parseStringMap(System.getenv("FLEX_CURRENCY_ACCOUNT_MAP"), //$NON-NLS-1$
                        "FLEX_CURRENCY_ACCOUNT_MAP"); //$NON-NLS-1$
        String secondaryJson = System.getenv("FLEX_CURRENCY_SECONDARY_ACCOUNT_MAP"); //$NON-NLS-1$
        Map<String, String> currencySecondaryAccountMap = secondaryJson == null || secondaryJson.isBlank() ? Map.of()
                        : parseStringMap(secondaryJson, "FLEX_CURRENCY_SECONDARY_ACCOUNT_MAP"); //$NON-NLS-1$
        String secPf = trimOrEmpty(System.getenv("FLEX_SECONDARY_PORTFOLIO_UUID")); //$NON-NLS-1$

        if (portfolioId.isEmpty() || portfolioUuid.isEmpty() || currencyAccountMap.isEmpty())
            return Optional.empty();

        return Optional.of(new FlexImportConfig(portfolioId, Optional.empty(), getFlexReportsDirectory(),
                        currencyAccountMap, currencySecondaryAccountMap, portfolioUuid,
                        secPf.isEmpty() ? Optional.empty() : Optional.of(secPf),
                        envBool("FLEX_CONVERT_BUY_SELL_TO_DELIVERY", false), //$NON-NLS-1$
                        envBool("FLEX_REMOVE_DIVIDENDS", false), //$NON-NLS-1$
                        envBool("FLEX_IMPORT_NOTES", true))); //$NON-NLS-1$
    }

    private List<FlexImportConfig> parseFlexImportConfigs(String configsJson)
    {
        try
        {
            JsonElement parsed = JsonParser.parseString(configsJson);
            List<JsonObject> objects = new ArrayList<>();

            if (parsed.isJsonObject())
            {
                objects.add(parsed.getAsJsonObject());
            }
            else if (parsed.isJsonArray())
            {
                parsed.getAsJsonArray().forEach(element -> {
                    if (element.isJsonObject())
                    {
                        objects.add(element.getAsJsonObject());
                    }
                    else
                    {
                        logger.warn("Skipping FLEX_IMPORT_CONFIGS entry because it is not an object"); //$NON-NLS-1$
                    }
                });
            }
            else
            {
                logger.error("FLEX_IMPORT_CONFIGS must be a JSON object or array of objects"); //$NON-NLS-1$
                return List.of();
            }

            List<FlexImportConfig> configs = new ArrayList<>();
            for (JsonObject object : objects)
            {
                parseFlexImportConfig(object).ifPresent(configs::add);
            }
            return List.copyOf(configs);
        }
        catch (JsonSyntaxException e)
        {
            logger.error("Invalid JSON for FLEX_IMPORT_CONFIGS: {}", e.getMessage()); //$NON-NLS-1$
            return List.of();
        }
    }

    private Optional<FlexImportConfig> parseFlexImportConfig(JsonObject object)
    {
        String portfolioId = readString(object, "portfolio_id") //$NON-NLS-1$
                        .or(() -> readString(object, "portfolioId")) //$NON-NLS-1$
                        .orElse(""); //$NON-NLS-1$
        String portfolioUuid = readString(object, "portfolio_uuid") //$NON-NLS-1$
                        .or(() -> readString(object, "portfolioUuid")) //$NON-NLS-1$
                        .orElse(""); //$NON-NLS-1$
        Map<String, String> currencyAccountMap = parseStringMap(
                        readElement(object, "currency_account_map").or(() -> readElement(object, "currencyAccountMap")) //$NON-NLS-1$ //$NON-NLS-2$
                                        .orElse(null),
                        "currency_account_map"); //$NON-NLS-1$

        if (portfolioId.isEmpty() || portfolioUuid.isEmpty() || currencyAccountMap.isEmpty())
        {
            logger.warn(
                            "Skipping FLEX_IMPORT_CONFIGS entry: portfolio_id/portfolioId, portfolio_uuid/portfolioUuid, and currency_account_map/currencyAccountMap are required"); //$NON-NLS-1$
            return Optional.empty();
        }

        Map<String, String> currencySecondaryAccountMap = parseStringMap(
                        readElement(object, "currency_secondary_account_map") //$NON-NLS-1$
                                        .or(() -> readElement(object, "currencySecondaryAccountMap")) //$NON-NLS-1$
                                        .orElse(null),
                        "currency_secondary_account_map"); //$NON-NLS-1$
        Optional<String> secondaryPortfolioUuid = readString(object, "secondary_portfolio_uuid") //$NON-NLS-1$
                        .or(() -> readString(object, "secondaryPortfolioUuid")); //$NON-NLS-1$
        Optional<String> queryId = readString(object, "query_id").or(() -> readString(object, "queryId")); //$NON-NLS-1$ //$NON-NLS-2$
        Path reportsDirectory = readString(object, "reports_dir") //$NON-NLS-1$
                        .or(() -> readString(object, "reportsDir")) //$NON-NLS-1$
                        .or(() -> readString(object, "destination_folder")) //$NON-NLS-1$
                        .or(() -> readString(object, "destinationFolder")) //$NON-NLS-1$
                        .or(() -> readString(object, "destination_dir")) //$NON-NLS-1$
                        .or(() -> readString(object, "destinationDir")) //$NON-NLS-1$
                        .map(Paths::get)
                        .map(Path::toAbsolutePath)
                        .orElseGet(RedisFlexImportListener::getFlexReportsDirectory)
                        .normalize();

        boolean convertBuySellToDelivery = readBoolean(object, "convert_buy_sell_to_delivery") //$NON-NLS-1$
                        .or(() -> readBoolean(object, "convertBuySellToDelivery")) //$NON-NLS-1$
                        .orElse(false);
        boolean removeDividends = readBoolean(object, "remove_dividends") //$NON-NLS-1$
                        .or(() -> readBoolean(object, "removeDividends")) //$NON-NLS-1$
                        .orElse(false);
        boolean importNotes = readBoolean(object, "import_notes") //$NON-NLS-1$
                        .or(() -> readBoolean(object, "importNotes")) //$NON-NLS-1$
                        .orElse(true);

        return Optional.of(new FlexImportConfig(portfolioId, queryId, reportsDirectory, currencyAccountMap,
                        currencySecondaryAccountMap, portfolioUuid, secondaryPortfolioUuid, convertBuySellToDelivery,
                        removeDividends, importNotes));
    }

    /**
     * Same resolution as {@link name.abuchen.portfolio.ui.api.controller.FlexImportController}.
     */
    private static Path getFlexReportsDirectory()
    {
        String flexReportsDir = System.getenv("FLEX_REPORTS_DIR"); //$NON-NLS-1$
        if (flexReportsDir == null)
            flexReportsDir = System.getProperty("flex.reports.dir"); //$NON-NLS-1$
        if (flexReportsDir == null)
            flexReportsDir = System.getProperty("user.dir"); //$NON-NLS-1$
        return Paths.get(flexReportsDir).toAbsolutePath();
    }

    private static final class FlexImportConfig
    {
        private final String portfolioId;
        private final Optional<String> queryId;
        private final Path reportsDirectory;
        private final Map<String, String> currencyAccountMap;
        private final Map<String, String> currencySecondaryAccountMap;
        private final String portfolioUuid;
        private final Optional<String> secondaryPortfolioUuid;
        private final boolean convertBuySellToDelivery;
        private final boolean removeDividends;
        private final boolean importNotes;

        private FlexImportConfig(String portfolioId, Optional<String> queryId, Path reportsDirectory,
                        Map<String, String> currencyAccountMap, Map<String, String> currencySecondaryAccountMap,
                        String portfolioUuid, Optional<String> secondaryPortfolioUuid,
                        boolean convertBuySellToDelivery, boolean removeDividends, boolean importNotes)
        {
            this.portfolioId = portfolioId;
            this.queryId = queryId;
            this.reportsDirectory = reportsDirectory.normalize();
            this.currencyAccountMap = Map.copyOf(currencyAccountMap);
            this.currencySecondaryAccountMap = Map.copyOf(currencySecondaryAccountMap);
            this.portfolioUuid = portfolioUuid;
            this.secondaryPortfolioUuid = secondaryPortfolioUuid;
            this.convertBuySellToDelivery = convertBuySellToDelivery;
            this.removeDividends = removeDividends;
            this.importNotes = importNotes;
        }

        private boolean matches(Optional<String> payloadQueryId, Optional<String> payloadOutputDir, String rawPath)
        {
            if (queryId.isPresent())
            {
                if (payloadQueryId.map(queryId.get()::equals).orElse(false))
                {
                    return payloadOutputDir.map(this::matchesOutputDir).orElse(true);
                }
                return false;
            }

            if (payloadOutputDir.isPresent())
            {
                if (matchesOutputDir(payloadOutputDir.get()))
                    return true;
            }

            Path path = Paths.get(rawPath);
            return path.isAbsolute() ? path.normalize().startsWith(reportsDirectory) : true;
        }

        private boolean matchesOutputDir(String outputDir)
        {
            Path path = Paths.get(outputDir).toAbsolutePath().normalize();
            return path.startsWith(reportsDirectory) || reportsDirectory.startsWith(path);
        }
    }

    public void start()
    {
        if (running)
        {
            logger.warn("Redis flex import listener already running"); //$NON-NLS-1$
            return;
        }

        if (!redisConfig.isEnabled())
        {
            logger.info("Redis flex import listener skipped (REDIS_ENABLED=false)"); //$NON-NLS-1$
            return;
        }

        if (!flexImportConfigured)
        {
            logger.info("Redis flex import listener skipped (set FLEX_IMPORT_VIA_REDIS=true and Flex import config env)"); //$NON-NLS-1$
            return;
        }

        running = true;
        subscriberThread = new Thread(this::runSubscriberLoop, "RedisFlexImportListener"); //$NON-NLS-1$
        subscriberThread.setDaemon(true);
        subscriberThread.start();
        logger.info("Starting Redis flex import listener on {}:{} channel '{}' with {} config(s)", redisConfig.host(), //$NON-NLS-1$
                        redisConfig.port(), CHANNEL_FLEX_IMPORT_READY, flexImportConfigs.size());
    }

    public void stop()
    {
        running = false;

        Jedis subscriber = activeSubscriber;
        if (subscriber != null)
        {
            try
            {
                subscriber.close();
            }
            catch (Exception e)
            {
                logger.debug("Error while closing Redis subscriber: {}", e.getMessage()); //$NON-NLS-1$
            }
        }

        if (subscriberThread != null)
        {
            subscriberThread.interrupt();
            try
            {
                subscriberThread.join(TimeUnit.SECONDS.toMillis(5));
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void runSubscriberLoop()
    {
        JedisPubSub pubSub = createPubSub();

        while (running)
        {
            try (Jedis jedis = new Jedis(new HostAndPort(redisConfig.host(), redisConfig.port()), buildClientConfig()))
            {
                activeSubscriber = jedis;
                jedis.subscribe(pubSub, CHANNEL_FLEX_IMPORT_READY);
            }
            catch (JedisConnectionException e)
            {
                if (!running)
                    break;

                logger.warn("Lost Redis connection: {}. Reconnecting in {} ms", e.getMessage(), //$NON-NLS-1$
                                redisConfig.reconnectDelayMillis());
                sleep(redisConfig.reconnectDelayMillis());
            }
            catch (Exception e)
            {
                if (!running)
                    break;

                logger.error("Unexpected Redis flex import subscription error", e); //$NON-NLS-1$
                sleep(redisConfig.reconnectDelayMillis());
            }
            finally
            {
                activeSubscriber = null;
            }
        }

        try
        {
            pubSub.unsubscribe();
        }
        catch (Exception e)
        {
            logger.debug("Error unsubscribing Redis pub/sub: {}", e.getMessage()); //$NON-NLS-1$
        }
    }

    private JedisPubSub createPubSub()
    {
        return new JedisPubSub()
        {
            @Override
            public void onMessage(String channel, String message)
            {
                if (!CHANNEL_FLEX_IMPORT_READY.equals(channel))
                    return;

                handleFlexImportMessage(message);
            }

            @Override
            public void onSubscribe(String channel, int subscribedChannels)
            {
                logger.info("Subscribed to Redis channel '{}'", channel); //$NON-NLS-1$
            }

            @Override
            public void onUnsubscribe(String channel, int subscribedChannels)
            {
                logger.info("Unsubscribed from Redis channel '{}'", channel); //$NON-NLS-1$
            }
        };
    }

    private DefaultJedisClientConfig buildClientConfig()
    {
        DefaultJedisClientConfig.Builder builder = DefaultJedisClientConfig.builder()
                        .database(redisConfig.database()).timeoutMillis(redisConfig.timeoutMillis());

        redisConfig.username().ifPresent(builder::user);
        redisConfig.password().ifPresent(builder::password);

        return builder.build();
    }

    private void handleFlexImportMessage(String message)
    {
        try
        {
            JsonElement parsed = JsonParser.parseString(message);
            if (!parsed.isJsonObject())
            {
                logger.debug("Ignoring non-object Redis flex message: {}", message); //$NON-NLS-1$
                return;
            }

            JsonObject payload = parsed.getAsJsonObject();

            String type = readString(payload, "type").orElse(""); //$NON-NLS-1$
            if (!"flex_import_ready".equals(type)) //$NON-NLS-1$
            {
                logger.debug("Ignoring Redis message type: {}", type); //$NON-NLS-1$
                return;
            }

            String rawPath = readString(payload, "file_path").orElse(""); //$NON-NLS-1$
            if (rawPath.isEmpty())
            {
                logger.warn("flex_import_ready message missing file_path: {}", message); //$NON-NLS-1$
                return;
            }

            Optional<String> payloadQueryId = readString(payload, "query_id"); //$NON-NLS-1$
            Optional<String> payloadOutputDir = readString(payload, "output_dir"); //$NON-NLS-1$
            List<FlexImportConfig> matchingConfigs = flexImportConfigs.stream()
                            .filter(config -> config.matches(payloadQueryId, payloadOutputDir, rawPath))
                            .toList();

            if (matchingConfigs.isEmpty())
            {
                logger.warn("No Flex import config matched file_path={}, query_id={}", rawPath, //$NON-NLS-1$
                                payloadQueryId.orElse("")); //$NON-NLS-1$
                return;
            }

            for (FlexImportConfig config : matchingConfigs)
            {
                importFlexReport(rawPath, config);
            }
        }
        catch (JsonSyntaxException e)
        {
            logger.warn("Failed to parse Redis flex import message: {}", e.getMessage()); //$NON-NLS-1$
        }
        catch (Exception e)
        {
            logger.error("Error handling Redis flex import message", e); //$NON-NLS-1$
        }
    }

    private void importFlexReport(String rawPath, FlexImportConfig config)
    {
        Path resolvedPath = resolveFlexReportPath(rawPath, config.reportsDirectory);
        String displayPath = config.reportsDirectory.relativize(resolvedPath).toString();

        logger.info("Redis flex import: file {}, portfolio {}", displayPath, config.portfolioId); //$NON-NLS-1$

        Client client = portfolioFileService.getPortfolio(config.portfolioId);
        if (client == null)
        {
            logger.warn(
                            "Skipping Redis flex import: portfolio {} is not in cache — open it first (e.g. GET /api/v1/portfolios/{})", //$NON-NLS-1$
                            config.portfolioId, config.portfolioId);
            return;
        }

        if (!resolvedPath.startsWith(config.reportsDirectory))
        {
            logger.warn("File path outside Flex reports directory: {}", rawPath); //$NON-NLS-1$
            return;
        }
        if (!Files.exists(resolvedPath))
        {
            logger.warn("Flex report file not found: {} (resolved: {})", rawPath, resolvedPath); //$NON-NLS-1$
            return;
        }

        File file = resolvedPath.toFile();

        FlexImportService service = new FlexImportService();
        Map<String, String> secondary = config.currencySecondaryAccountMap.isEmpty() ? new HashMap<>()
                        : new HashMap<>(config.currencySecondaryAccountMap);

        FlexImportService.ImportResult result = service.importFlexReport(client, file, config.currencyAccountMap,
                        secondary, config.portfolioUuid, config.secondaryPortfolioUuid.orElse(null),
                        config.convertBuySellToDelivery, config.removeDividends, config.importNotes);

        if (result.getItemsImported() > 0)
        {
            try
            {
                portfolioFileService.saveFile(config.portfolioId);
                logger.info("Portfolio saved after Redis flex import — portfolio: {}, items: {}", config.portfolioId, //$NON-NLS-1$
                                result.getItemsImported());
            }
            catch (Exception e)
            {
                logger.error("Failed to save portfolio after flex import: {}", config.portfolioId, e); //$NON-NLS-1$
            }
        }

        if (result.isSuccess())
        {
            logger.info("Redis flex import finished — file: {}, portfolio: {}, items imported: {}", displayPath, //$NON-NLS-1$
                            config.portfolioId, result.getItemsImported());
        }
        else
        {
            logger.warn("Redis flex import completed with errors — file: {}, portfolio: {}, errors: {}", displayPath, //$NON-NLS-1$
                            config.portfolioId, result.getErrors());
        }
    }

    private Path resolveFlexReportPath(String rawPath, Path reportsDirectory)
    {
        Path path = Paths.get(rawPath);
        if (path.isAbsolute())
        {
            Path normalized = path.normalize();
            if (normalized.startsWith(reportsDirectory))
                return normalized;

            Path fileName = normalized.getFileName();
            if (fileName != null)
                return reportsDirectory.resolve(fileName).normalize();
        }

        return reportsDirectory.resolve(rawPath).normalize();
    }

    private Optional<String> readString(JsonObject object, String property)
    {
        JsonElement element = object.get(property);
        if (element == null || element.isJsonNull())
            return Optional.empty();

        if (!element.isJsonPrimitive())
            return Optional.empty();

        String value = element.getAsString();
        return (value == null || value.isBlank()) ? Optional.empty() : Optional.of(value.trim());
    }

    private Optional<JsonElement> readElement(JsonObject object, String property)
    {
        JsonElement element = object.get(property);
        return element == null || element.isJsonNull() ? Optional.empty() : Optional.of(element);
    }

    private Optional<Boolean> readBoolean(JsonObject object, String property)
    {
        JsonElement element = object.get(property);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive())
            return Optional.empty();

        String value = element.getAsString();
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(Boolean.parseBoolean(value.trim()));
    }

    private void sleep(long millis)
    {
        try
        {
            Thread.sleep(millis);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
    }
}
