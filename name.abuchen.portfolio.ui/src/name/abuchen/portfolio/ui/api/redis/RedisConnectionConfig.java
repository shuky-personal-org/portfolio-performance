package name.abuchen.portfolio.ui.api.redis;

import java.util.Optional;

/**
 * Shared Redis connection settings (host, port, auth) from the environment.
 */
public final class RedisConnectionConfig
{
    private final boolean enabled;
    private final String host;
    private final int port;
    private final int database;
    private final Optional<String> username;
    private final Optional<String> password;
    private final int timeoutMillis;
    private final long reconnectDelayMillis;

    public RedisConnectionConfig(boolean enabled, String host, int port, int database, Optional<String> username,
                    Optional<String> password, int timeoutMillis, long reconnectDelayMillis)
    {
        this.enabled = enabled && host != null && !host.isBlank();
        this.host = (host == null || host.isBlank()) ? "localhost" : host; //$NON-NLS-1$
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
        this.timeoutMillis = timeoutMillis;
        this.reconnectDelayMillis = reconnectDelayMillis;
    }

    public static RedisConnectionConfig fromEnvironment()
    {
        String enabledEnv = System.getenv("REDIS_ENABLED"); //$NON-NLS-1$
        boolean enabled = enabledEnv == null || Boolean.parseBoolean(enabledEnv);

        String host = System.getenv("REDIS_HOST"); //$NON-NLS-1$
        int port = parseInt(System.getenv("REDIS_PORT"), 6379); //$NON-NLS-1$
        int db = parseInt(System.getenv("REDIS_DB"), 0); //$NON-NLS-1$
        int timeout = parseInt(System.getenv("REDIS_TIMEOUT_MS"), 2_000); //$NON-NLS-1$
        long reconnectDelay = parseLong(System.getenv("REDIS_RECONNECT_DELAY_MS"), 5_000L); //$NON-NLS-1$

        Optional<String> username = optionalEnv("REDIS_USERNAME"); //$NON-NLS-1$
        Optional<String> password = optionalEnv("REDIS_PASSWORD"); //$NON-NLS-1$

        return new RedisConnectionConfig(enabled, host, port, db, username, password, timeout, reconnectDelay);
    }

    public boolean isEnabled()
    {
        return enabled;
    }

    public String host()
    {
        return host;
    }

    public int port()
    {
        return port;
    }

    public int database()
    {
        return database;
    }

    public Optional<String> username()
    {
        return username;
    }

    public Optional<String> password()
    {
        return password;
    }

    public int timeoutMillis()
    {
        return timeoutMillis;
    }

    public long reconnectDelayMillis()
    {
        return reconnectDelayMillis;
    }

    private static int parseInt(String value, int defaultValue)
    {
        if (value == null || value.isBlank())
            return defaultValue;
        try
        {
            return Integer.parseInt(value.trim());
        }
        catch (NumberFormatException e)
        {
            return defaultValue;
        }
    }

    private static long parseLong(String value, long defaultValue)
    {
        if (value == null || value.isBlank())
            return defaultValue;
        try
        {
            return Long.parseLong(value.trim());
        }
        catch (NumberFormatException e)
        {
            return defaultValue;
        }
    }

    private static Optional<String> optionalEnv(String key)
    {
        String value = System.getenv(key);
        if (value == null || value.isBlank())
            return Optional.empty();
        return Optional.of(value.trim());
    }
}
