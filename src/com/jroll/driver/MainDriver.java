package com.jroll.driver;

import com.jroll.exception.FindBugsException;
import com.jroll.extractors.FindBugsExtractor;
import com.jroll.extractors.GitExtractor;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.XMLConfiguration;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.TreeMap;

/**
 * Created by jroll on 9/28/15.
 */
public class MainDriver {

    /*
        Depending on the arguments passed in, this program will:
            -Go through all the commit logs and match specific issues to commits
            -Go through all the commits and run the maven scripts to build the project and
                then get the static metrics. ALSO build a full list of possible classes to start with.
            -To build the feature list, assume the state of the code at the given ticket is the state of the code
                at the PRIOR commit

     */
    public static void runFullCheckouts (String[] args) throws Exception {
        XMLConfiguration config = new XMLConfiguration("config.xml");
        String gitRepo = config.getString("git_repo");
        String buildCommand = config.getString("build_command");
        String staticAnalysis = config.getString("static_analysis_command");
        Repository localRepo = new FileRepository(gitRepo + "/.git");
        Runtime rt = Runtime.getRuntime();

        rt.exec("rm " + gitRepo + "/.git/index.lock");

        GitExtractor extractor = new GitExtractor(localRepo);
        Iterable<RevCommit> commits = extractor.getAllCommits();
        for (RevCommit commit : commits) {
            System.out.println(commit.getName());
            extractor.checkout(commit);
            System.out.println(buildCommand);
            Process pr = rt.exec(buildCommand, null, new File(gitRepo));
            int exitCode = pr.waitFor();
            System.out.println(exitCode);
        }
    }

    public static void main(String[] args) throws FindBugsException, ConfigurationException, IOException, InterruptedException {
        FindBugsExtractor ex = new FindBugsExtractor();

        XMLConfiguration config = new XMLConfiguration("config.xml");
        String buildCommand = config.getString("build_command");
        String staticAnalysis = config.getString("static_analysis_command");
        String subProject = config.getString("subproject");
        String gitRepo = config.getString("git_repo");
        /*Runtime rt = Runtime.getRuntime();

        Process pr = rt.exec(buildCommand, null, new File(gitRepo + "/" + subProject));
        int exitCode = pr.waitFor();
        System.out.println(exitCode);

        Process pr2 = rt.exec(staticAnalysis, null, new File(gitRepo + "/" + subProject));
        exitCode = pr2.waitFor();
        System.out.println(exitCode); */
        List<TreeMap> rows = ex.execute(new File("/Users/jroll/dev/thesis/qpid-java/client/target/findbugsXml.xml"));
        System.out.println("commit, category, classname, classpath, rank");
        for (TreeMap row : rows) {
            printRow(row, "HEAD");
        }

    }
    public static void printRow(TreeMap<String, String> row, String commit) {
        System.out.printf("%s, %s,%s,%s,%s\n", commit, row.get("category"), row.get("classname"), row.get("classpath"), row.get("rank"));
    }
}
