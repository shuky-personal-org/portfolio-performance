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
import com.google.gson.JsonArray;
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
 * Enable with {@code FLEX_IMPORT_VIA_REDIS=true}. For one portfolio, set
 * {@code FLEX_IMPORT_PORTFOLIO_ID}, {@code FLEX_CURRENCY_ACCOUNT_MAP} (JSON object), and
 * {@code FLEX_PORTFOLIO_UUID}. For multiple portfolios, set {@code FLEX_IMPORT_AUTOMATIONS} to a
 * JSON array and route entries by Flex {@code query_id}.
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

    private final PortfolioFileService portfolioFileService;
    private final RedisConnectionConfig redisConfig;

    private volatile boolean running;
    private Thread subscriberThread;
    private volatile Jedis activeSubscriber;

    private final List<FlexImportAutomation> flexImportAutomations;
    private final boolean flexImportConfigured;

    private record FlexImportAutomation(String queryId, Path reportsDir, String portfolioId,
                    Map<String, String> currencyAccountMap, Map<String, String> currencySecondaryAccountMap,
                    String portfolioUuid, Optional<String> secondaryPortfolioUuid, boolean convertBuySellToDelivery,
                    boolean removeDividends, boolean importNotes)
    {
    }

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

        this.flexImportAutomations = loadImportAutomations();
        this.flexImportConfigured = envWantsImport && !flexImportAutomations.isEmpty();

        if (envWantsImport && !flexImportConfigured)
        {
            logger.warn(
                            "FLEX_IMPORT_VIA_REDIS is true but no valid Flex import automation is configured; flex Redis import disabled"); //$NON-NLS-1$
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
            Map<String, String> m = new Gson().fromJson(json.trim(), type);
            return m != null ? m : Map.of();
        }
        catch (JsonSyntaxException e)
        {
            logger.error("Invalid JSON for {}: {}", envName, e.getMessage()); //$NON-NLS-1$
            return Map.of();
        }
    }

    private static Map<String, String> parseStringMap(JsonElement json, String envName)
    {
        if (json == null || json.isJsonNull())
            return Map.of();
        if (json.isJsonObject())
        {
            try
            {
                var type = new TypeToken<Map<String, String>>()
                {
                }.getType();
                Map<String, String> m = new Gson().fromJson(json, type);
                return m != null ? m : Map.of();
            }
            catch (JsonSyntaxException e)
            {
                logger.error("Invalid JSON object for {}: {}", envName, e.getMessage());
                return Map.of();
            }
        }
        if (json.isJsonPrimitive())
            return parseStringMap(json.getAsString(), envName);
        logger.error("Invalid JSON for {}: expected object or JSON string", envName);
        return Map.of();
    }

    private static List<FlexImportAutomation> loadImportAutomations()
    {
        String automationsJson = System.getenv("FLEX_IMPORT_AUTOMATIONS");
        if (automationsJson != null && !automationsJson.isBlank())
            return parseImportAutomations(automationsJson);

        var legacyAutomation = loadLegacyImportAutomation();
        return legacyAutomation.map(List::of).orElseGet(List::of);
    }

    private static Optional<FlexImportAutomation> loadLegacyImportAutomation()
    {
        String portfolioId = trimOrEmpty(System.getenv("FLEX_IMPORT_PORTFOLIO_ID"));
        String portfolioUuid = trimOrEmpty(System.getenv("FLEX_PORTFOLIO_UUID"));
        Map<String, String> currencyAccountMap = parseStringMap(System.getenv("FLEX_CURRENCY_ACCOUNT_MAP"),
                        "FLEX_CURRENCY_ACCOUNT_MAP");
        if (portfolioId.isEmpty() || portfolioUuid.isEmpty() || currencyAccountMap.isEmpty())
            return Optional.empty();

        String secondaryJson = System.getenv("FLEX_CURRENCY_SECONDARY_ACCOUNT_MAP");
        Map<String, String> currencySecondaryAccountMap = secondaryJson == null || secondaryJson.isBlank() ? Map.of()
                        : parseStringMap(secondaryJson, "FLEX_CURRENCY_SECONDARY_ACCOUNT_MAP");

        String secPf = trimOrEmpty(System.getenv("FLEX_SECONDARY_PORTFOLIO_UUID"));
        Optional<String> secondaryPortfolioUuid = secPf.isEmpty() ? Optional.empty() : Optional.of(secPf);

        return Optional.of(new FlexImportAutomation("", getFlexReportsDirectory(), portfolioId, currencyAccountMap,
                        currencySecondaryAccountMap, portfolioUuid, secondaryPortfolioUuid,
                        envBool("FLEX_CONVERT_BUY_SELL_TO_DELIVERY", false), envBool("FLEX_REMOVE_DIVIDENDS", false),
                        envBool("FLEX_IMPORT_NOTES", true)));
    }

    private static List<FlexImportAutomation> parseImportAutomations(String automationsJson)
    {
        try
        {
            JsonElement parsed = JsonParser.parseString(automationsJson);
            if (!parsed.isJsonArray())
            {
                logger.error("FLEX_IMPORT_AUTOMATIONS must be a JSON array");
                return List.of();
            }

            List<FlexImportAutomation> automations = new ArrayList<>();
            Map<String, Boolean> queryIds = new HashMap<>();
            JsonArray array = parsed.getAsJsonArray();
            for (int ii = 0; ii < array.size(); ii++)
            {
                JsonElement element = array.get(ii);
                if (!element.isJsonObject())
                {
                    logger.warn("Skipping FLEX_IMPORT_AUTOMATIONS[{}]: expected object", ii);
                    continue;
                }

                JsonObject object = element.getAsJsonObject();
                Optional<FlexImportAutomation> automation = parseImportAutomation(object, ii);
                if (automation.isEmpty())
                    continue;

                String queryId = automation.get().queryId();
                if (queryIds.containsKey(queryId))
                {
                    logger.warn("Skipping duplicate FLEX_IMPORT_AUTOMATIONS query_id: {}", queryId);
                    continue;
                }

                queryIds.put(queryId, Boolean.TRUE);
                automations.add(automation.get());
            }

            return List.copyOf(automations);
        }
        catch (JsonSyntaxException e)
        {
            logger.error("Invalid JSON for FLEX_IMPORT_AUTOMATIONS: {}", e.getMessage());
            return List.of();
        }
    }

    private static Optional<FlexImportAutomation> parseImportAutomation(JsonObject object, int index)
    {
        String queryId = readString(object, "query_id", "queryId").orElse("");
        String portfolioId = readString(object, "portfolio_id", "portfolioId").orElse("");
        String portfolioUuid = readString(object, "portfolio_uuid", "portfolioUuid").orElse("");
        Map<String, String> currencyAccountMap = parseStringMap(
                        firstProperty(object, "currency_account_map", "currencyAccountMap").orElse(null),
                        "FLEX_IMPORT_AUTOMATIONS[" + index + "].currency_account_map");

        if (queryId.isEmpty() || portfolioId.isEmpty() || portfolioUuid.isEmpty() || currencyAccountMap.isEmpty())
        {
            logger.warn(
                            "Skipping FLEX_IMPORT_AUTOMATIONS[{}]: query_id, portfolio_id, portfolio_uuid, and currency_account_map are required",
                            index);
            return Optional.empty();
        }

        Map<String, String> currencySecondaryAccountMap = parseStringMap(
                        firstProperty(object, "currency_secondary_account_map", "currencySecondaryAccountMap")
                                        .orElse(null),
                        "FLEX_IMPORT_AUTOMATIONS[" + index + "].currency_secondary_account_map");
        String secondaryPortfolioUuid = readString(object, "secondary_portfolio_uuid", "secondaryPortfolioUuid")
                        .orElse("");
        String reportsDir = readString(object, "reports_dir", "reportsDir").orElse("");

        return Optional.of(new FlexImportAutomation(queryId,
                        reportsDir.isEmpty() ? getFlexReportsDirectory() : Paths.get(reportsDir).toAbsolutePath(),
                        portfolioId, currencyAccountMap, currencySecondaryAccountMap, portfolioUuid,
                        secondaryPortfolioUuid.isEmpty() ? Optional.empty() : Optional.of(secondaryPortfolioUuid),
                        readBoolean(object, "convert_buy_sell_to_delivery", "convertBuySellToDelivery", false),
                        readBoolean(object, "remove_dividends", "removeDividends", false),
                        readBoolean(object, "import_notes", "importNotes", true)));
    }

    private Optional<FlexImportAutomation> findAutomation(String queryId)
    {
        if (flexImportAutomations.size() == 1 && flexImportAutomations.get(0).queryId().isEmpty())
            return Optional.of(flexImportAutomations.get(0));

        if (queryId == null || queryId.isBlank())
        {
            if (flexImportAutomations.size() == 1)
                return Optional.of(flexImportAutomations.get(0));
            return Optional.empty();
        }

        return flexImportAutomations.stream().filter(a -> queryId.equals(a.queryId())).findFirst();
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
            logger.info("Redis flex import listener skipped (set FLEX_IMPORT_VIA_REDIS=true and portfolio env)"); //$NON-NLS-1$
            return;
        }

        running = true;
        subscriberThread = new Thread(this::runSubscriberLoop, "RedisFlexImportListener"); //$NON-NLS-1$
        subscriberThread.setDaemon(true);
        subscriberThread.start();
        logger.info("Starting Redis flex import listener on {}:{} channel '{}'", redisConfig.host(), //$NON-NLS-1$
                        redisConfig.port(), CHANNEL_FLEX_IMPORT_READY);
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

            String queryId = readString(payload, "query_id").orElse(""); //$NON-NLS-1$
            Optional<FlexImportAutomation> automation = findAutomation(queryId);
            if (automation.isEmpty())
            {
                logger.warn("Skipping Redis flex import: no configured import automation for query_id '{}'", queryId); //$NON-NLS-1$
                return;
            }
            FlexImportAutomation config = automation.get();

            String rawPath = readString(payload, "file_path").orElse(""); //$NON-NLS-1$
            if (rawPath.isEmpty())
            {
                logger.warn("flex_import_ready message missing file_path: {}", message); //$NON-NLS-1$
                return;
            }

            Path path = Paths.get(rawPath);
            String relativeFileName = path.getFileName() != null ? path.getFileName().toString() : rawPath;

            logger.info("Redis flex import: query {}, file {}", queryId, relativeFileName); //$NON-NLS-1$

            Client client = portfolioFileService.getPortfolio(config.portfolioId());
            if (client == null)
            {
                logger.warn(
                                "Skipping Redis flex import: portfolio {} is not in cache — open it first (e.g. GET /api/v1/portfolios/{})", //$NON-NLS-1$
                                config.portfolioId());
                return;
            }

            Path flexReportsDir = config.reportsDir();
            Path resolvedPath = flexReportsDir.resolve(relativeFileName).normalize();
            if (!resolvedPath.startsWith(flexReportsDir))
            {
                logger.warn("File path outside Flex reports directory: {}", relativeFileName); //$NON-NLS-1$
                return;
            }
            if (!Files.exists(resolvedPath))
            {
                logger.warn("Flex report file not found: {} (resolved: {})", relativeFileName, resolvedPath); //$NON-NLS-1$
                return;
            }

            File file = resolvedPath.toFile();

            FlexImportService service = new FlexImportService();
            Map<String, String> secondary = config.currencySecondaryAccountMap().isEmpty() ? new HashMap<>()
                            : new HashMap<>(config.currencySecondaryAccountMap());

            FlexImportService.ImportResult result = service.importFlexReport(client, file, config.currencyAccountMap(),
                            secondary, config.portfolioUuid(), config.secondaryPortfolioUuid().orElse(null),
                            config.convertBuySellToDelivery(), config.removeDividends(), config.importNotes());

            if (result.getItemsImported() > 0)
            {
                try
                {
                    portfolioFileService.saveFile(config.portfolioId());
                    logger.info("Portfolio saved after Redis flex import — portfolio: {}, items: {}", //$NON-NLS-1$
                                    config.portfolioId(), result.getItemsImported());
                }
                catch (Exception e)
                {
                    logger.error("Failed to save portfolio after flex import: {}", config.portfolioId(), e); //$NON-NLS-1$
                }
            }

            if (result.isSuccess())
            {
                logger.info("Redis flex import finished — file: {}, items imported: {}", relativeFileName, //$NON-NLS-1$
                                result.getItemsImported());
            }
            else
            {
                logger.warn("Redis flex import completed with errors — file: {}, errors: {}", relativeFileName, //$NON-NLS-1$
                                result.getErrors());
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

    private static Optional<JsonElement> firstProperty(JsonObject object, String... properties)
    {
        for (String property : properties)
        {
            JsonElement element = object.get(property);
            if (element != null && !element.isJsonNull())
                return Optional.of(element);
        }
        return Optional.empty();
    }

    private static Optional<String> readString(JsonObject object, String... properties)
    {
        Optional<JsonElement> element = firstProperty(object, properties);
        if (element.isEmpty())
            return Optional.empty();

        String value = element.get().getAsString();
        return (value == null || value.isBlank()) ? Optional.empty() : Optional.of(value.trim());
    }

    private static boolean readBoolean(JsonObject object, String snakeCaseProperty, String camelCaseProperty,
                    boolean defaultValue)
    {
        Optional<JsonElement> element = firstProperty(object, snakeCaseProperty, camelCaseProperty);
        if (element.isEmpty())
            return defaultValue;
        try
        {
            return element.get().getAsBoolean();
        }
        catch (UnsupportedOperationException | IllegalStateException e)
        {
            return defaultValue;
        }
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
