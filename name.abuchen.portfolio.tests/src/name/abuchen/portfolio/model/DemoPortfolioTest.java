package name.abuchen.portfolio.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.junit.Test;

@SuppressWarnings("nls")
public class DemoPortfolioTest
{
    @Test
    public void testDemoPortfolioLoadsFromDisk() throws Exception
    {
        var demoPortfolio = findDemoPortfolio();
        var client = ClientFactory.load(demoPortfolio.toFile(), null, new NullProgressMonitor());

        assertEquals(Client.CURRENT_VERSION, client.getFileVersionAfterRead());
        assertEquals("EUR", client.getBaseCurrency());

        assertEquals(2, client.getSecurities().size());
        assertEquals("Demo Global Equity ETF", client.getSecurities().get(0).getName());
        assertEquals("Demo Corporate Bond ETF", client.getSecurities().get(1).getName());

        assertEquals(1, client.getAccounts().size());
        assertEquals("Demo Cash Account", client.getAccounts().get(0).getName());
        assertEquals(3, client.getAccounts().get(0).getTransactions().size());

        assertEquals(1, client.getPortfolios().size());
        assertEquals("Demo Brokerage Account", client.getPortfolios().get(0).getName());
        assertEquals(4, client.getPortfolios().get(0).getTransactions().size());
    }

    private Path findDemoPortfolio() throws Exception
    {
        var root = Path.of(DemoPortfolioTest.class.getProtectionDomain().getCodeSource().getLocation().toURI());

        while (root != null)
        {
            var demoPortfolio = root.resolve("demo/demo.portfolio");
            if (Files.isRegularFile(demoPortfolio))
                return demoPortfolio;

            root = root.getParent();
        }

        fail("Unable to locate demo/demo.portfolio from test runtime");
        throw new IllegalStateException("unreachable");
    }
}
