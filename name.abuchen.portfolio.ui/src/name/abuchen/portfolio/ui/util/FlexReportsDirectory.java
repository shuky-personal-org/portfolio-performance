package name.abuchen.portfolio.ui.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves the IB Flex reports directory from {@code FLEX_REPORTS_DIR} or
 * {@code flex.reports.dir}. Used by the REST API, Redis flex import, and the
 * desktop IB Flex import file dialog.
 */
public final class FlexReportsDirectory
{
    private static final Logger logger = LoggerFactory.getLogger(FlexReportsDirectory.class);

    private FlexReportsDirectory()
    {
    }

    private static String configuredPath()
    {
        String flexReportsDir = System.getenv("FLEX_REPORTS_DIR"); //$NON-NLS-1$
        if (flexReportsDir == null)
            flexReportsDir = System.getProperty("flex.reports.dir"); //$NON-NLS-1$
        return flexReportsDir;
    }

    /**
     * @return configured Flex reports directory when set and present on disk
     */
    public static Optional<Path> resolve()
    {
        String flexReportsDir = configuredPath();
        if (flexReportsDir == null)
            return Optional.empty();

        Path path = Paths.get(flexReportsDir).toAbsolutePath().normalize();
        if (!Files.isDirectory(path))
            return Optional.empty();

        return Optional.of(path);
    }

    /**
     * @return configured directory path when set (even if not yet created)
     */
    public static Optional<Path> resolveConfigured()
    {
        String flexReportsDir = configuredPath();
        if (flexReportsDir == null)
            return Optional.empty();
        return Optional.of(Paths.get(flexReportsDir).toAbsolutePath().normalize());
    }

    /**
     * @return configured directory, or the process working directory when unset
     */
    public static Path resolveConfiguredOrUserDir()
    {
        return resolveConfigured()
                        .orElseGet(() -> Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize()); //$NON-NLS-1$
    }

    /**
     * Same resolution as {@link #resolveConfiguredOrUserDir()} but creates the directory when missing
     * (for API import paths).
     */
    public static Path resolveOrCreate()
    {
        Path path = resolveConfiguredOrUserDir();
        if (!Files.exists(path))
        {
            try
            {
                Files.createDirectories(path);
            }
            catch (Exception e)
            {
                logger.error("Failed to create Flex reports directory: {}", path, e);
            }
        }
        return path;
    }

    /**
     * @return path suitable for SWT {@code FileDialog#setFilterPath}, or empty when unset or missing
     */
    public static Optional<String> defaultFileDialogPath()
    {
        return resolve().map(Path::toString);
    }
}
