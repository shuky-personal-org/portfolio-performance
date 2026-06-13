package name.abuchen.portfolio.ui.views.taxonomy;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.Test;

import name.abuchen.portfolio.model.Classification;
import name.abuchen.portfolio.model.Classification.Assignment;
import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.model.Taxonomy;
import name.abuchen.portfolio.money.CurrencyUnit;
import name.abuchen.portfolio.money.ExchangeRateProviderFactory;

@SuppressWarnings("nls")
public class TaxonomyModelStaleAssignmentTest
{
    @Test
    public void ignoresAssignmentsToVehiclesMissingFromClient() throws Exception
    {
        var client = new Client();
        var security = new Security("Stale Holding", CurrencyUnit.EUR);
        client.addSecurity(security);

        var taxonomy = new Taxonomy("Asset Classes");
        var root = new Classification(null, "root", "Asset Classes");
        taxonomy.setRootNode(root);

        var equities = new Classification(root, "equities-id", "Equities");
        root.addChild(equities);
        equities.addAssignment(new Assignment(security, Classification.ONE_HUNDRED_PERCENT));

        // Simulate stale state: assignment remains but security was dropped without cleanup
        client.getSecurities().remove(security);

        var factory = new ExchangeRateProviderFactory(client);
        var model = new TaxonomyModel(factory, client, taxonomy);

        assertThat(model.getClassificationRootNode(), notNullValue());
        assertThat(model.getClassificationRootNode().getChildren().size(), is(1));
    }
}
