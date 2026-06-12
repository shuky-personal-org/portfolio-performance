package name.abuchen.portfolio.ui.api.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.JsonParser;

import name.abuchen.portfolio.model.Classification;
import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.Taxonomy;
import name.abuchen.portfolio.model.TaxonomyJSONImporter;
import name.abuchen.portfolio.model.TaxonomyJSONImporter.ImportResult;
import name.abuchen.portfolio.model.TaxonomyJSONImporter.Operation;
import name.abuchen.portfolio.ui.api.dto.TaxonomyImportResultDto;

public final class TaxonomyImportService
{
    public record ImportOptions(boolean preserveNameAndDescription, boolean pruneAbsentClassifications)
    {
        public static ImportOptions defaults()
        {
            return new ImportOptions(false, false);
        }
    }

    private TaxonomyImportService()
    {
    }

    public static List<TaxonomyImportResultDto> importStructures(Client client, InputStream jsonStream,
                    ImportOptions options) throws IOException
    {
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(jsonStream, "jsonStream");

        var importOptions = options != null ? options : ImportOptions.defaults();
        var taxonomyData = parseTaxonomyPayload(jsonStream);
        var results = new ArrayList<TaxonomyImportResultDto>();

        for (var data : taxonomyData)
        {
            var name = extractTaxonomyName(data);
            var existing = findTaxonomyByName(client, name);

            Taxonomy taxonomy;
            String action;

            if (existing != null)
            {
                taxonomy = existing;
                action = "updated";
            }
            else
            {
                taxonomy = createTaxonomyShell(name);
                client.addTaxonomy(taxonomy);
                action = "created";
            }

            var result = applyImport(client, taxonomy, data, importOptions);
            if (result.hasChanges() || "created".equals(action))
            {
                results.add(toResultDto(taxonomy, action, result));
            }
        }

        return results;
    }

    public static TaxonomyImportResultDto importStructureIntoTaxonomy(Client client, String taxonomyId,
                    InputStream jsonStream, ImportOptions options) throws IOException
    {
        Objects.requireNonNull(client, "client");

        var taxonomy = TaxonomyManagementService.findTaxonomy(client, taxonomyId);
        var importOptions = options != null ? options : ImportOptions.defaults();
        var taxonomyData = parseTaxonomyPayload(jsonStream);

        if (taxonomyData.isEmpty())
            throw new IllegalArgumentException("Import payload must contain at least one taxonomy structure");

        if (taxonomyData.size() > 1)
            throw new IllegalArgumentException("Only one taxonomy structure can be imported into an existing taxonomy");

        var result = applyImport(client, taxonomy, taxonomyData.get(0), importOptions);
        return toResultDto(taxonomy, "updated", result);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> parseTaxonomyPayload(InputStream jsonStream) throws IOException
    {
        try (var reader = new InputStreamReader(jsonStream, StandardCharsets.UTF_8))
        {
            var gson = new Gson();
            var root = JsonParser.parseReader(reader);

            if (root == null || root.isJsonNull())
                throw new IOException("Invalid JSON: empty payload");

            var taxonomyData = new ArrayList<Map<String, Object>>();

            if (root.isJsonArray())
            {
                for (var element : root.getAsJsonArray())
                    taxonomyData.add(gson.fromJson(element, Map.class));
            }
            else if (root.isJsonObject())
            {
                taxonomyData.add(gson.fromJson(root, Map.class));
            }
            else
            {
                throw new IOException("Invalid JSON: expected an object or array of taxonomy structures");
            }

            if (taxonomyData.isEmpty())
                throw new IOException("Invalid JSON: taxonomy list is empty");

            return taxonomyData;
        }
    }

    private static ImportResult applyImport(Client client, Taxonomy taxonomy, Map<String, Object> jsonData,
                    ImportOptions options) throws IOException
    {
        var importer = new TaxonomyJSONImporter(client, taxonomy, options.preserveNameAndDescription(),
                        options.pruneAbsentClassifications());
        return importer.importTaxonomy(jsonData);
    }

    private static Taxonomy createTaxonomyShell(String name)
    {
        var taxonomyName = name != null && !name.isBlank() ? name.trim() : "Imported Taxonomy";
        var taxonomy = new Taxonomy(taxonomyName);
        taxonomy.setRootNode(new Classification(UUID.randomUUID().toString(), taxonomyName));
        return taxonomy;
    }

    private static String extractTaxonomyName(Map<String, Object> jsonData)
    {
        var name = (String) jsonData.get("name");
        return name != null && !name.isBlank() ? name.trim() : "Imported Taxonomy";
    }

    private static Taxonomy findTaxonomyByName(Client client, String name)
    {
        if (name == null || name.isBlank())
            return null;

        return client.getTaxonomies().stream() //
                        .filter(taxonomy -> name.equals(taxonomy.getName())) //
                        .findFirst() //
                        .orElse(null);
    }

    private static TaxonomyImportResultDto toResultDto(Taxonomy taxonomy, String action, ImportResult result)
    {
        var dto = new TaxonomyImportResultDto();
        dto.setTaxonomyId(taxonomy.getId());
        dto.setTaxonomyName(taxonomy.getName());
        dto.setAction(action);
        dto.setCreatedObjects(result.getCreatedObjects());
        dto.setModifiedObjects(result.getModifiedObjects());
        dto.setWarnings(collectWarnings(result));
        return dto;
    }

    private static List<String> collectWarnings(ImportResult result)
    {
        var warnings = new ArrayList<String>();
        for (var change : result.getChanges())
        {
            if (change.getOperation() == Operation.WARNING || change.getOperation() == Operation.ERROR)
                warnings.add(change.getComment());
        }
        return warnings;
    }
}
