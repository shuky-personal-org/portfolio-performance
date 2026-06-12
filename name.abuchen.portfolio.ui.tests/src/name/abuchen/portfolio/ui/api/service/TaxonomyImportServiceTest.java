package name.abuchen.portfolio.ui.api.service;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

import name.abuchen.portfolio.model.Classification;
import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.Taxonomy;
import name.abuchen.portfolio.ui.api.service.TaxonomyImportService.ImportOptions;

@SuppressWarnings("nls")
public class TaxonomyImportServiceTest
{
    @Test
    public void createsNewTaxonomyFromSingleStructureJson() throws Exception
    {
        var client = new Client();
        var json = """
                        {
                          "name": "Asset Classes",
                          "color": "#336699",
                          "categories": [
                            {
                              "name": "Equities",
                              "key": "equities",
                              "color": "#ff0000"
                            }
                          ]
                        }
                        """;

        var results = TaxonomyImportService.importStructures(client,
                        new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), ImportOptions.defaults());

        assertThat(results, hasSize(1));
        assertThat(results.get(0).getAction(), is("created"));
        assertThat(results.get(0).getTaxonomyName(), is("Asset Classes"));
        assertThat(client.getTaxonomies(), hasSize(1));

        var taxonomy = client.getTaxonomies().get(0);
        assertThat(taxonomy.getRoot().getChildren(), hasSize(1));
        assertThat(taxonomy.getRoot().getChildren().get(0).getName(), is("Equities"));
    }

    @Test
    public void importsMultipleStructuresFromArrayJson() throws Exception
    {
        var client = new Client();
        var json = """
                        [
                          {
                            "name": "Regions",
                            "categories": [{ "name": "Europe" }]
                          },
                          {
                            "name": "Sectors",
                            "categories": [{ "name": "Technology" }]
                          }
                        ]
                        """;

        var results = TaxonomyImportService.importStructures(client,
                        new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), ImportOptions.defaults());

        assertThat(results, hasSize(2));
        assertThat(client.getTaxonomies(), hasSize(2));
    }

    @Test
    public void updatesExistingTaxonomyWhenNameMatches() throws Exception
    {
        var client = new Client();
        var taxonomy = new Taxonomy("Asset Classes");
        taxonomy.setRootNode(new Classification(null, "root", "Asset Classes"));
        client.addTaxonomy(taxonomy);

        var json = """
                        {
                          "name": "Asset Classes",
                          "categories": [{ "name": "Bonds" }]
                        }
                        """;

        var results = TaxonomyImportService.importStructures(client,
                        new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), ImportOptions.defaults());

        assertThat(results, hasSize(1));
        assertThat(results.get(0).getAction(), is("updated"));
        assertThat(client.getTaxonomies(), hasSize(1));
        assertThat(taxonomy.getRoot().getChildren(), hasSize(1));
        assertThat(taxonomy.getRoot().getChildren().get(0).getName(), is("Bonds"));
    }

    @Test
    public void importsStructureIntoExistingTaxonomy() throws Exception
    {
        var client = new Client();
        var taxonomy = new Taxonomy("My Taxonomy");
        taxonomy.setRootNode(new Classification(null, "root", "My Taxonomy"));
        client.addTaxonomy(taxonomy);

        var json = """
                        {
                          "name": "Ignored Name",
                          "categories": [{ "name": "Cash" }]
                        }
                        """;

        var result = TaxonomyImportService.importStructureIntoTaxonomy(client, taxonomy.getId(),
                        new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), ImportOptions.defaults());

        assertThat(result, notNullValue());
        assertThat(result.getAction(), is("updated"));
        assertThat(taxonomy.getRoot().getChildren(), hasSize(1));
        assertThat(taxonomy.getRoot().getChildren().get(0).getName(), is("Cash"));
    }

    @Test
    public void pruneAbsentClassificationsRemovesMissingCategories() throws Exception
    {
        var client = new Client();
        var taxonomy = new Taxonomy("Asset Classes");
        var root = new Classification(null, "root", "Asset Classes");
        taxonomy.setRootNode(root);
        root.addChild(new Classification(root, "keep-id", "Keep Me"));
        root.addChild(new Classification(root, "drop-id", "Drop Me"));
        client.addTaxonomy(taxonomy);

        var json = """
                        {
                          "name": "Asset Classes",
                          "categories": [{ "name": "Keep Me" }]
                        }
                        """;

        TaxonomyImportService.importStructures(client,
                        new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)),
                        new ImportOptions(false, true));

        assertThat(taxonomy.getRoot().getChildren(), hasSize(1));
        assertThat(taxonomy.getRoot().getChildren().get(0).getName(), is("Keep Me"));
    }

    @Test
    public void createdObjectsCountReflectsImportedCategories() throws Exception
    {
        var client = new Client();
        var json = """
                        {
                          "name": "New Taxonomy",
                          "categories": [
                            { "name": "One" },
                            { "name": "Two" }
                          ]
                        }
                        """;

        var results = TaxonomyImportService.importStructures(client,
                        new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), ImportOptions.defaults());

        assertThat(results.get(0).getCreatedObjects(), greaterThan(0));
    }
}
