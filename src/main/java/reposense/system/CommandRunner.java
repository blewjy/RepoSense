package reposense.system;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import reposense.git.CommitNotFoundException;
import reposense.model.Author;
import reposense.model.RepoConfiguration;
import reposense.util.FileUtil;
import reposense.util.StringsUtil;

public class CommandRunner {
    private static final DateFormat GIT_LOG_SINCE_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'00:00:00+08:00");
    private static final DateFormat GIT_LOG_UNTIL_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'23:59:59+08:00");

    // ignore check against email
    private static final String AUTHOR_NAME_PATTERN = "^%s <.*>$";
    private static final String OR_OPERATOR_PATTERN = "\\|";

    private static boolean isWindows = isWindows();

    public static String gitLog(RepoConfiguration config, Author author) {
        Path rootPath = Paths.get(config.getRepoRoot());

        String command = "git log --no-merges -i ";
        command += convertToGitDateRangeArgs(config.getSinceDate(), config.getUntilDate());
        command += " --pretty=format:\"%H|%aN|%ad|%s\" --date=iso --shortstat";
        command += convertToFilterAuthorArgs(author);
        command += convertToGitFormatsArgs(config.getFormats());
        command += convertToGitExcludeGlobArgs(author.getIgnoreGlobList());

        return runCommand(rootPath, command);
    }

    public static void checkout(String root, String hash) {
        Path rootPath = Paths.get(root);
        runCommand(rootPath, "git checkout " + hash);
    }

    /**
     * Checks out to the latest commit before {@code untilDate} in {@code branchName} branch
     * if {@code untilDate} is not null.
     * @throws CommitNotFoundException if commits before {@code untilDate} cannot be found.
     */
    public static void checkoutToDate(String root, String branchName, Date untilDate) throws CommitNotFoundException {
        if (untilDate == null) {
            return;
        }

        Path rootPath = Paths.get(root);

        String substituteCommand = "git rev-list -1 --before="
                + GIT_LOG_UNTIL_DATE_FORMAT.format(untilDate) + " " + branchName;
        String hash = runCommand(rootPath, substituteCommand);
        if (hash.isEmpty()) {
            throw new CommitNotFoundException("Commit before until date is not found.");
        }
        String checkoutCommand = "git checkout " + hash;
        runCommand(rootPath, checkoutCommand);
    }

    public static String blameRaw(String root, String fileDirectory) {
        Path rootPath = Paths.get(root);

        String blameCommand = "git blame -w --line-porcelain";
        blameCommand += " " + addQuote(fileDirectory);

        return StringsUtil.filterText(runCommand(rootPath, blameCommand), "(^author .*)|(^[0-9a-f]{40} .*)");
    }

    public static String checkStyleRaw(String absoluteDirectory) {
        Path rootPath = Paths.get(absoluteDirectory);
        return runCommand(
                rootPath,
                "java -jar checkstyle-7.7-all.jar -c /google_checks.xml -f xml " + absoluteDirectory
        );
    }

    /**
     * Returns the git diff result of the current commit compared to {@code lastCommitHash}, without any context.
     */
    public static String diffCommit(String root, String lastCommitHash) {
        Path rootPath = Paths.get(root);
        return runCommand(rootPath, "git diff -U0 " + lastCommitHash);
    }

    /**
     * Returns the latest commit hash before {@code date}.
     * Returns an empty {@code String} if {@code date} is null, or there is no such commit.
     */
    public static String getCommitHashBeforeDate(String root, String branchName, Date date) {
        if (date == null) {
            return "";
        }

        Path rootPath = Paths.get(root);
        String revListCommand = "git rev-list -1 --before="
                + GIT_LOG_SINCE_DATE_FORMAT.format(date) + " " + branchName;
        return runCommand(rootPath, revListCommand);
    }

    /**
     * Returns the current working branch.
     */
    public static String getCurrentBranch(String root) {
        Path rootPath = Paths.get(root);
        String gitBranchCommand = "git branch";

        return StringsUtil.filterText(runCommand(rootPath, gitBranchCommand), "\\* (.*)").split("\\*")[1].trim();
    }

    public static String getShortlogSummary(String root, Date sinceDate, Date untilDate) {
        Path rootPath = Paths.get(root);
        String command = "git log --pretty=short";
        command += convertToGitDateRangeArgs(sinceDate, untilDate);
        command += " | git shortlog --summary";

        return runCommand(rootPath, command);
    }

    public static String cloneRepo(String location, String repoName) throws IOException {
        Path rootPath = Paths.get(FileUtil.REPOS_ADDRESS, repoName);
        Files.createDirectories(rootPath);
        return runCommand(rootPath, "git clone " + addQuote(location));
    }

    private static String runCommand(Path path, String command) {
        ProcessBuilder pb = null;
        if (isWindows) {
            pb = new ProcessBuilder()
                    .command(new String[]{"CMD", "/c", command})
                    .directory(path.toFile());
        } else {
            pb = new ProcessBuilder()
                    .command(new String[]{"bash", "-c", command})
                    .directory(path.toFile());
        }
        Process p = null;
        try {
            p = pb.start();
        } catch (IOException e) {
            throw new RuntimeException("Error Creating Thread:" + e.getMessage());
        }
        StreamGobbler errorGobbler = new StreamGobbler(p.getErrorStream());
        StreamGobbler outputGobbler = new StreamGobbler(p.getInputStream());
        outputGobbler.start();
        errorGobbler.start();
        int exit = 0;
        try {
            exit = p.waitFor();
            outputGobbler.join();
            errorGobbler.join();
        } catch (InterruptedException e) {
            throw new RuntimeException("Error Handling Thread.");
        }

        if (exit == 0) {
            return outputGobbler.getValue();
        } else {
            String errorMessage = "Error returned from command ";
            errorMessage += command + "on path ";
            errorMessage += path.toString() + " :\n" + errorGobbler.getValue();
            throw new RuntimeException(errorMessage);
        }
    }

    private static String addQuote(String original) {
        return "\"" + original + "\"";
    }

    private static boolean isWindows() {
        return (System.getProperty("os.name").toLowerCase().indexOf("win") >= 0);
    }

    /**
     * Returns the {@code String} command to specify the date range of commits to analyze for `git` commands.
     */
    private static String convertToGitDateRangeArgs(Date sinceDate, Date untilDate) {
        String gitDateRangeArgs = "";

        if (sinceDate != null) {
            gitDateRangeArgs += " --since=" + addQuote(GIT_LOG_SINCE_DATE_FORMAT.format(sinceDate));
        }
        if (untilDate != null) {
            gitDateRangeArgs += " --until=" + addQuote(GIT_LOG_UNTIL_DATE_FORMAT.format(untilDate));
        }

        return gitDateRangeArgs;
    }

    /**
     * Returns the {@code String} command to specify the authors to analyze for `git log` command.
     */
    private static String convertToFilterAuthorArgs(Author author) {
        StringBuilder filterAuthorArgsBuilder = new StringBuilder(" --author=\"");

        // git author names may contain regex meta-characters, so we need to escape those
        author.getAuthorAliases().stream()
                .map(authorAlias -> String.format(AUTHOR_NAME_PATTERN,
                        StringsUtil.replaceSpecialSymbols(authorAlias, ".")) + OR_OPERATOR_PATTERN)
                .forEach(filterAuthorArgsBuilder::append);

        filterAuthorArgsBuilder.append(
                String.format(AUTHOR_NAME_PATTERN,
                        StringsUtil.replaceSpecialSymbols(author.getGitId(), "."))).append("\"");
        return filterAuthorArgsBuilder.toString();
    }

    /**
     * Returns the {@code String} command to specify the file formats to analyze for `git` commands.
     */
    private static String convertToGitFormatsArgs(List<String> formats) {
        StringBuilder gitFormatsArgsBuilder = new StringBuilder();
        final String cmdFormat = " -- " + addQuote("*.%s");
        formats.stream()
                .map(format -> String.format(cmdFormat, format))
                .forEach(gitFormatsArgsBuilder::append);

        return gitFormatsArgsBuilder.toString();
    }

    /**
     * Returns the {@code String} command to specify the globs to exclude for `git log` command.
     */
    private static String convertToGitExcludeGlobArgs(List<String> ignoreGlobList) {
        StringBuilder gitExcludeGlobArgsBuilder = new StringBuilder();
        final String cmdFormat = " " + addQuote(":(exclude)%s");
        ignoreGlobList.stream()
                .filter(item -> !item.isEmpty())
                .map(ignoreGlob -> String.format(cmdFormat, ignoreGlob))
                .forEach(gitExcludeGlobArgsBuilder::append);

        return gitExcludeGlobArgsBuilder.toString();
    }
}
