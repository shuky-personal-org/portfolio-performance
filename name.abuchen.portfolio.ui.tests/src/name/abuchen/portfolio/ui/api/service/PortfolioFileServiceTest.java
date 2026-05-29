package name.abuchen.portfolio.ui.api.service;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.FileNotFoundException;
import java.nio.file.Files;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class PortfolioFileServiceTest
{
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void createsPortfolioFileWithDefaultExtension() throws Exception
    {
        var service = new PortfolioFileService(folder.getRoot().getAbsolutePath());

        var fileInfo = service.createPortfolioFile("Example");

        assertThat(fileInfo.getName(), is("Example"));
        assertThat(fileInfo.getPath(), is("Example.portfolio"));
        assertThat(Files.exists(folder.getRoot().toPath().resolve("Example.portfolio")), is(true));
        assertThat(service.listPortfolioFiles().size(), is(1));
    }

    @Test
    public void duplicatesPortfolioFile() throws Exception
    {
        var root = folder.getRoot().toPath();
        Files.writeString(root.resolve("Source.xml"), "<client/>");
        var service = new PortfolioFileService(root.toString());
        var sourceFile = service.listPortfolioFiles().get(0);

        var duplicate = service.duplicatePortfolioFile(sourceFile.getId(), "Copy.xml");

        assertThat(duplicate.getName(), is("Copy"));
        assertThat(duplicate.getPath(), is("Copy.xml"));
        assertThat(Files.readString(root.resolve("Copy.xml")), is("<client/>"));
        assertThat(service.listPortfolioFiles().size(), is(2));
    }

    @Test
    public void renamesPortfolioFileWithExistingExtension() throws Exception
    {
        var root = folder.getRoot().toPath();
        Files.writeString(root.resolve("Original.xml"), "<client/>");
        var service = new PortfolioFileService(root.toString());
        var sourceFile = service.listPortfolioFiles().get(0);

        var renamed = service.renamePortfolioFile(sourceFile.getId(), "Renamed");

        assertThat(renamed.getName(), is("Renamed"));
        assertThat(renamed.getPath(), is("Renamed.xml"));
        assertThat(Files.exists(root.resolve("Original.xml")), is(false));
        assertThat(Files.readString(root.resolve("Renamed.xml")), is("<client/>"));
        assertThat(service.listPortfolioFiles().size(), is(1));
    }

    @Test(expected = java.nio.file.FileAlreadyExistsException.class)
    public void rejectsRenameToExistingPortfolioFile() throws Exception
    {
        var root = folder.getRoot().toPath();
        Files.writeString(root.resolve("Original.xml"), "<client/>");
        Files.writeString(root.resolve("Existing.xml"), "<client/>");
        var service = new PortfolioFileService(root.toString());
        var sourceFile = service.listPortfolioFiles().stream()
                        .filter(file -> file.getPath().equals("Original.xml"))
                        .findFirst()
                        .orElseThrow();

        service.renamePortfolioFile(sourceFile.getId(), "Existing.xml");
    }

    @Test
    public void movesRemovedPortfolioFileToDeletedFolder() throws Exception
    {
        var root = folder.getRoot().toPath();
        Files.writeString(root.resolve("Removable.xml"), "<client/>");
        var service = new PortfolioFileService(root.toString());
        var sourceFile = service.listPortfolioFiles().get(0);

        var deleted = service.removePortfolioFile(sourceFile.getId());

        assertThat(deleted.getPath(), is("deleted/Removable.xml"));
        assertThat(Files.exists(root.resolve("Removable.xml")), is(false));
        assertThat(Files.exists(root.resolve("deleted").resolve("Removable.xml")), is(true));
        assertThat(service.listPortfolioFiles().size(), is(0));

        try
        {
            service.findFileById(sourceFile.getId());
        }
        catch (FileNotFoundException e)
        {
            assertThat(e.getMessage().contains(sourceFile.getId()), is(true));
            return;
        }

        throw new AssertionError("Deleted portfolio file should not be discoverable by its original ID");
    }

    @Test
    public void resolvesPortfolioFilePathForDownload() throws Exception
    {
        var root = folder.getRoot().toPath();
        Files.writeString(root.resolve("Download.xml"), "<client/>");
        var service = new PortfolioFileService(root.toString());
        var sourceFile = service.listPortfolioFiles().get(0);

        var path = service.getPortfolioFilePath(sourceFile.getId());

        assertThat(path, is(root.resolve("Download.xml")));
        assertThat(Files.readString(path), is("<client/>"));
    }

    @Test(expected = SecurityException.class)
    public void rejectsPathsOutsidePortfolioDirectory() throws Exception
    {
        var service = new PortfolioFileService(folder.getRoot().getAbsolutePath());

        service.createPortfolioFile("../outside.portfolio");
    }

    @Test(expected = SecurityException.class)
    public void rejectsActiveFilesInDeletedFolder() throws Exception
    {
        var service = new PortfolioFileService(folder.getRoot().getAbsolutePath());

        service.createPortfolioFile("deleted/restore-me.portfolio");
    }
}
