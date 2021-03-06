package reposense.template;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;

import reposense.authorship.FileInfoAnalyzer;
import reposense.authorship.FileInfoExtractor;
import reposense.authorship.model.FileInfo;
import reposense.authorship.model.FileResult;
import reposense.authorship.model.LineInfo;
import reposense.git.GitDownloader;
import reposense.git.GitDownloaderException;
import reposense.model.Author;
import reposense.model.RepoConfiguration;
import reposense.parser.ArgsParser;
import reposense.parser.InvalidLocationException;
import reposense.system.CommandRunner;
import reposense.util.FileUtil;

public class GitTestTemplate {
    protected static final String TEST_REPO_GIT_LOCATION = "https://github.com/reposense/testrepo-Alpha.git";
    protected static final String DISK_REPO_DISPLAY_NAME = "testrepo-Alpha_master";
    protected static final String FIRST_COMMIT_HASH = "7d7584f";
    protected static final String TEST_COMMIT_HASH = "2fb6b9b";
    protected static final String MAIN_AUTHOR_NAME = "harryggg";
    protected static final String FAKE_AUTHOR_NAME = "fakeAuthor";
    protected static final String EUGENE_AUTHOR_NAME = "eugenepeh";
    protected static final String LATEST_COMMIT_HASH = "136c6713fc00cfe79a1598e8ce83c6ef3b878660";
    protected static final String EUGENE_AUTHOR_README_FILE_COMMIT_07052018 =
            "2d87a431fcbb8f73a731b6df0fcbee962c85c250";
    protected static final String FAKE_AUTHOR_BLAME_TEST_FILE_COMMIT_08022018 =
            "768015345e70f06add2a8b7d1f901dc07bf70582";
    protected static final String MAIN_AUTHOR_BLAME_TEST_FILE_COMMIT_06022018 =
            "8d0ac2ee20f04dce8df0591caed460bffacb65a4";
    protected static final String NONEXISTENT_COMMIT_HASH = "nonExistentCommitHash";


    protected static RepoConfiguration config;

    @Before
    public void before() throws InvalidLocationException {
        config = new RepoConfiguration(TEST_REPO_GIT_LOCATION, "master");
        config.setAuthorList(Collections.singletonList(getAlphaAllAliasAuthor()));
        config.setFormats(ArgsParser.DEFAULT_FORMATS);
    }

    @BeforeClass
    public static void beforeClass() throws GitDownloaderException, IOException, InvalidLocationException {
        deleteRepos();
        config = new RepoConfiguration(TEST_REPO_GIT_LOCATION, "master");
        config.setFormats(ArgsParser.DEFAULT_FORMATS);
        GitDownloader.downloadRepo(config);
    }

    @AfterClass
    public static void afterClass() throws IOException {
        deleteRepos();
    }

    @After
    public void after() {
        CommandRunner.checkout(config.getRepoRoot(), "master");
    }

    private static void deleteRepos() throws IOException {
        FileUtil.deleteDirectory(FileUtil.REPOS_ADDRESS);
    }

    public FileInfo generateTestFileInfo(String relativePath) {
        FileInfo fileInfo = FileInfoExtractor.generateFileInfo(config.getRepoRoot(), relativePath);

        config.getAuthorAliasMap().put(MAIN_AUTHOR_NAME, new Author(MAIN_AUTHOR_NAME));
        config.getAuthorAliasMap().put(FAKE_AUTHOR_NAME, new Author(FAKE_AUTHOR_NAME));

        return fileInfo;
    }

    public FileResult getFileResult(String relativePath) {
        FileInfo fileinfo = generateTestFileInfo(relativePath);
        return FileInfoAnalyzer.analyzeFile(config, fileinfo);
    }

    public void assertFileAnalysisCorrectness(FileResult fileResult) {
        for (LineInfo line : fileResult.getLines()) {
            if (line.getContent().startsWith("fake")) {
                Assert.assertEquals(line.getAuthor(), new Author(FAKE_AUTHOR_NAME));
            } else {
                Assert.assertNotEquals(line.getAuthor(), new Author(FAKE_AUTHOR_NAME));
            }
        }
    }

    /**
     * Returns a {@code Author} that has git id and aliases of all authors in testrepo-Alpha, so that no commits
     * will be filtered out in the `git log` command.
     */
    protected Author getAlphaAllAliasAuthor() {
        Author author = new Author(MAIN_AUTHOR_NAME);
        author.setAuthorAliases(Arrays.asList(FAKE_AUTHOR_NAME, EUGENE_AUTHOR_NAME));
        return author;
    }
}
