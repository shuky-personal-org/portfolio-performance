package name.abuchen.portfolio.model;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

@SuppressWarnings("nls")
public class TaxonomyJSONExporterTest
{
    private Taxonomy taxonomy;

    @Before
    public void setUp()
    {
        taxonomy = new Taxonomy("Asset Classes");
        var root = new Classification(null, "root", "Asset Classes");
        root.setColor("#112233");

        var equities = new Classification(root, "equities", "Equities");
        equities.setKey("equities");
        equities.setNote("Stocks and ETFs");
        equities.setColor("#FF0000");
        root.addChild(equities);

        taxonomy.setRootNode(root);
    }

    @Test
    public void testStructureOnlyExportOmitsInstruments() throws IOException
    {
        var exporter = new TaxonomyJSONExporter(taxonomy, false);
        var json = exportToString(exporter);

        Map<String, Object> result = new Gson().fromJson(json, new TypeToken<Map<String, Object>>() {}.getType());

        assertThat(result.get("name"), is("Asset Classes"));
        assertThat(result.get("color"), is("#112233"));
        assertThat(result.containsKey("instruments"), is(false));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> categories = (List<Map<String, Object>>) result.get("categories");
        assertThat(categories.size(), is(1));
        assertThat(categories.get(0).get("name"), is("Equities"));
        assertThat(categories.get(0).get("key"), is("equities"));
    }

    @Test
    public void testFullExportIncludesInstruments() throws IOException
    {
        var exporter = new TaxonomyJSONExporter(taxonomy, true);
        var json = exportToString(exporter);

        Map<String, Object> result = new Gson().fromJson(json, new TypeToken<Map<String, Object>>() {}.getType());

        assertThat(result.get("instruments"), is(not(nullValue())));
    }

    private String exportToString(TaxonomyJSONExporter exporter) throws IOException
    {
        try (var out = new ByteArrayOutputStream())
        {
            exporter.export(out);
            return out.toString(StandardCharsets.UTF_8);
        }
    }
}
