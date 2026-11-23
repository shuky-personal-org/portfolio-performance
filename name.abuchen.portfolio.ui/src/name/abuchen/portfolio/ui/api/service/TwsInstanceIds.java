package name.abuchen.portfolio.ui.api.service;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import name.abuchen.portfolio.model.ClientProperties;

/**
 * The TWS instance ids a portfolio may be assigned to.
 * <p>
 * The built-in ids ({@code default}, {@code primary}, {@code secondary}) are
 * always accepted. Deployments with more IBKR accounts add their ids with the
 * environment variable {@code TWS_INSTANCE_IDS}, a comma-separated list such as
 * {@code secondary,acct3}. With the variable unset the accepted ids are exactly
 * the built-in ones. Any other id is rejected.
 */
public final class TwsInstanceIds
{
    /** Environment variable with additional instance ids, comma separated. */
    public static final String ENV_VARIABLE = "TWS_INSTANCE_IDS"; //$NON-NLS-1$

    private static final Logger logger = LoggerFactory.getLogger(TwsInstanceIds.class);

    private static final Set<String> BUILT_IN = Set.of(ClientProperties.DEFAULT_TWS_INSTANCE_ID, "primary", //$NON-NLS-1$
                    "secondary"); //$NON-NLS-1$

    /** Same shape as the ids in the ops instance manifest. */
    private static final Pattern VALID_ID = Pattern.compile("[a-z0-9]+"); //$NON-NLS-1$

    private TwsInstanceIds()
    {
    }

    /** Ids accepted in this process: built-in plus {@code TWS_INSTANCE_IDS}. */
    public static Set<String> supported()
    {
        return supported(System.getenv(ENV_VARIABLE));
    }

    public static boolean isSupported(String instanceId)
    {
        return instanceId != null && supported().contains(instanceId);
    }

    /**
     * Ids accepted for a given value of {@code TWS_INSTANCE_IDS}. Blank entries
     * are skipped; entries that are not lower-case letters and digits are
     * ignored with a warning, so a typo cannot widen what is accepted.
     */
    static Set<String> supported(String configured)
    {
        Set<String> ids = new LinkedHashSet<>(BUILT_IN);

        if (configured != null)
        {
            for (String entry : configured.split(",")) //$NON-NLS-1$
            {
                String id = entry.trim();
                if (id.isEmpty())
                    continue;

                if (VALID_ID.matcher(id).matches())
                    ids.add(id);
                else
                    logger.warn("Ignoring invalid TWS instance id in {}: '{}'", ENV_VARIABLE, id); //$NON-NLS-1$
            }
        }

        return Collections.unmodifiableSet(ids);
    }
}
