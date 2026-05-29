package name.abuchen.portfolio.ui.api.redis;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
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
 * Enable with {@code FLEX_IMPORT_VIA_REDIS=true}. Import messages must carry {@code portfolio_id}
 * and {@code import_config}; legacy environment variables are parsed but are not used as fallback.
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

    private final boolean flexImportConfigured;
    private final String portfolioId;
    private final Map<String, String> currencyAccountMap;
    private final Map<String, String> currencySecondaryAccountMap;
    private final String portfolioUuid;
    private final Optional<String> secondaryPortfolioUuid;
    private final boolean convertBuySellToDelivery;
    private final boolean removeDividends;
    private final boolean importNotes;

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

        this.portfolioId = trimOrEmpty(System.getenv("FLEX_IMPORT_PORTFOLIO_ID")); //$NON-NLS-1$
        this.portfolioUuid = trimOrEmpty(System.getenv("FLEX_PORTFOLIO_UUID")); //$NON-NLS-1$
        String primaryJson = System.getenv("FLEX_CURRENCY_ACCOUNT_MAP"); //$NON-NLS-1$
        String secondaryJson = System.getenv("FLEX_CURRENCY_SECONDARY_ACCOUNT_MAP"); //$NON-NLS-1$

        this.currencyAccountMap = parseStringMap(primaryJson, "FLEX_CURRENCY_ACCOUNT_MAP"); //$NON-NLS-1$
        this.currencySecondaryAccountMap = secondaryJson == null || secondaryJson.isBlank() ? Map.of()
                        : parseStringMap(secondaryJson, "FLEX_CURRENCY_SECONDARY_ACCOUNT_MAP"); //$NON-NLS-1$

        String secPf = trimOrEmpty(System.getenv("FLEX_SECONDARY_PORTFOLIO_UUID")); //$NON-NLS-1$
        this.secondaryPortfolioUuid = secPf.isEmpty() ? Optional.empty() : Optional.of(secPf);

        this.convertBuySellToDelivery = envBool("FLEX_CONVERT_BUY_SELL_TO_DELIVERY", false); //$NON-NLS-1$
        this.removeDividends = envBool("FLEX_REMOVE_DIVIDENDS", false); //$NON-NLS-1$
        this.importNotes = envBool("FLEX_IMPORT_NOTES", true); //$NON-NLS-1$

        this.flexImportConfigured = envWantsImport;
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
            logger.info("Redis flex import listener skipped (set FLEX_IMPORT_VIA_REDIS=true)"); //$NON-NLS-1$
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

            String rawPath = readString(payload, "relative_path").orElse(readString(payload, "file_path").orElse("")); //$NON-NLS-1$ //$NON-NLS-2$
            if (rawPath.isEmpty())
            {
                logger.warn("flex_import_ready message missing relative_path or file_path: {}", message); //$NON-NLS-1$
                return;
            }

            Path path = Paths.get(rawPath);
            String relativeFileName = path.isAbsolute() && path.getFileName() != null ? path.getFileName().toString()
                            : path.toString();

            logger.info("Redis flex import: file {}", relativeFileName); //$NON-NLS-1$

            JsonObject importConfig = payload.has("import_config") && payload.get("import_config").isJsonObject() //$NON-NLS-1$ //$NON-NLS-2$
                            ? payload.getAsJsonObject("import_config") //$NON-NLS-1$
                            : new JsonObject();

            // Legacy env fallbacks are intentionally disabled for automated imports. The env fields are
            // still parsed above so reverting to the old single-portfolio flow is straightforward.
            String targetPortfolioId = readString(payload, "portfolio_id").orElse(""); //$NON-NLS-1$
            String targetPortfolioUuid = readString(importConfig, "portfolioUUID").orElse(""); //$NON-NLS-1$
            Optional<String> targetSecondaryPortfolioUuid = readString(importConfig, "secondaryPortfolioUUID"); //$NON-NLS-1$
            Map<String, String> targetCurrencyAccountMap = readStringMap(importConfig, "currencyAccountMap") //$NON-NLS-1$
                            .orElse(Map.of());
            Map<String, String> targetCurrencySecondaryAccountMap = readStringMap(importConfig,
                            "currencySecondaryAccountMap").orElse(Map.of()); //$NON-NLS-1$
            boolean targetConvertBuySellToDelivery = readBoolean(importConfig, "convertBuySellToDelivery") //$NON-NLS-1$
                            .orElse(false);
            boolean targetRemoveDividends = readBoolean(importConfig, "removeDividends").orElse(false); //$NON-NLS-1$
            boolean targetImportNotes = readBoolean(importConfig, "importNotes").orElse(true); //$NON-NLS-1$

            if (targetPortfolioId.isEmpty() || targetPortfolioUuid.isEmpty() || targetCurrencyAccountMap.isEmpty())
            {
                logger.warn(
                                "Skipping Redis flex import: portfolio_id, portfolioUUID, or currencyAccountMap missing in message"); //$NON-NLS-1$
                return;
            }

            Client client = portfolioFileService.getPortfolio(targetPortfolioId);
            if (client == null)
            {
                logger.warn(
                                "Skipping Redis flex import: portfolio {} is not in cache — open it first (e.g. GET /api/v1/portfolios/{})", //$NON-NLS-1$
                                targetPortfolioId, targetPortfolioId);
                return;
            }

            Path flexReportsDir = getFlexReportsDirectory();
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
            Map<String, String> secondary = targetCurrencySecondaryAccountMap.isEmpty() ? new HashMap<>()
                            : new HashMap<>(targetCurrencySecondaryAccountMap);

            FlexImportService.ImportResult result = service.importFlexReport(client, file, targetCurrencyAccountMap,
                            secondary, targetPortfolioUuid, targetSecondaryPortfolioUuid.orElse(null),
                            targetConvertBuySellToDelivery, targetRemoveDividends, targetImportNotes);

            if (result.getItemsImported() > 0)
            {
                try
                {
                    portfolioFileService.saveFile(targetPortfolioId);
                    logger.info("Portfolio saved after Redis flex import — portfolio: {}, items: {}", //$NON-NLS-1$
                                    targetPortfolioId,
                                    result.getItemsImported());
                }
                catch (Exception e)
                {
                    logger.error("Failed to save portfolio after flex import: {}", targetPortfolioId, e); //$NON-NLS-1$
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

    private Optional<String> readString(JsonObject object, String property)
    {
        JsonElement element = object.get(property);
        if (element == null || element.isJsonNull())
            return Optional.empty();

        String value = element.getAsString();
        return (value == null || value.isBlank()) ? Optional.empty() : Optional.of(value.trim());
    }

    private Optional<Map<String, String>> readStringMap(JsonObject object, String property)
    {
        JsonElement element = object.get(property);
        if (element == null || element.isJsonNull() || !element.isJsonObject())
            return Optional.empty();

        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet())
        {
            if (entry.getValue() == null || entry.getValue().isJsonNull())
                continue;
            String key = trimOrEmpty(entry.getKey());
            String value = trimOrEmpty(entry.getValue().getAsString());
            if (!key.isEmpty() && !value.isEmpty())
                result.put(key, value);
        }
        return result.isEmpty() ? Optional.empty() : Optional.of(result);
    }

    private Optional<Boolean> readBoolean(JsonObject object, String property)
    {
        JsonElement element = object.get(property);
        if (element == null || element.isJsonNull())
            return Optional.empty();
        return Optional.of(element.getAsBoolean());
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
