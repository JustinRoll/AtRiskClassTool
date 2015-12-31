package com.jroll.driver;

import com.jroll.data.ClassData;
import com.jroll.data.CommitData;
import com.jroll.data.GitMetadata;
import com.jroll.data.Requirement;
import com.jroll.extractors.FindBugsExtractor;
import com.jroll.extractors.GitExtractor;
import com.jroll.extractors.JiraExtractor;
import com.jroll.util.*;
import gr.spinellis.ckjm.ClassMetrics;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.XMLConfiguration;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.jroll.command.CommandExecutor.printOutput;
import static com.jroll.util.CKJMUtil.parseData;
import static com.jroll.util.CustomFileUtil.findAllFilesWithExtension;
import static com.jroll.util.CustomFileUtil.getExtension;
import static com.jroll.util.CustomFileUtil.trimCustom;

/**
 * Created by jroll on 9/28/15.
 */
public class MainDriver {






    public static TreeMap<String, ClassData> runStaticMetrics(XMLConfiguration config) throws Exception {

        TreeMap<String, String> fixVersions = CustomFileUtil.readFile("fix.txt", "\\|");
        System.out.println(fixVersions);
        return analyzeCommits(fixVersions, config);
    }

    public static void main(String[] args) throws Exception {
        XMLConfiguration config = new XMLConfiguration("config.xml");
        //TreeMap<String, ClassData> fixToClassData = runStaticMetrics(config);
        //runBigTable(fixToClassData);
        //ARFFGenerator.convertFile("bigTableRandom.txt", "bigTableRandom.arff");
        oldmain(args);

    }

    public static Map<String, ClassMetrics> readDependencies(String repo) throws Exception {
        Set<String> files = new HashSet<String>();
        findAllFilesWithExtension(".class", new File(repo).listFiles(), files);

        Map<String, ClassMetrics> fileMetrics = parseData(files);

        return fileMetrics;
    }



    public static int getStaticCount(String fixName, String className, TreeMap<String, TreeMap<String, Integer>> staticCount) {
        if (staticCount == null || className == null || staticCount == null)
            return 0;
        else if (staticCount.get(fixName) != null && staticCount.get(fixName).get(className) != null) {
            return staticCount.get(fixName).get(className);
        }
        return 0;
    }






    /*
        Get numbers of times the current class has changed
        Filter out stuff that doesn't include org.apache.qpid
            -
     */

    public static void oldmain(String[] args) throws Exception {
        XMLConfiguration config = new XMLConfiguration("config.xml");
        List<HashMap<String, String>> jiraRows = JiraExtractor.parseExcel(config.getString("jira_file"));
        System.out.println("Jira Data Done");
        /* Gather all commit data */
        CommitData data = gatherCommits();
        System.out.println("Commit Data Done");
        ArrayList<Requirement> reqs = new ArrayList<Requirement>();
        String gitRepo = config.getString("git_repo");
        String cpFix = "cp -r " + gitRepo + " /Users/jroll/Downloads/data/";
        String staticAnalysis = config.getString("static_analysis_command");
        TreeMap<String, String> fixVersions = new TreeMap<String, String>();

        for (HashMap<String, String> row : jiraRows) {
            String ticketId = row.get("Key").replaceAll("QPID-", "");
            //System.out.println(ticketId);
            if (data.gitMetas.get(ticketId) != null) {//Look up each mapped commit {

                Requirement req = new Requirement();
                req.setJiraFields(row);
                req.setId(row.get("Key"));
                req.setCreateDate(parseDateString(row.get("Created")));

                req.setGitMetadatas(data.gitMetas.get(ticketId));
                reqs.add(req);
                String fixVersion = req.getJiraFields().get("Fix Version/s");
                if (fixVersions.get(fixVersion) == null) {
                    fixVersions.put(fixVersion, req.getGitMetadatas().get(0).getCommitId());
                }
                //System.out.println("Found a match");
        }


            //Check to see if there is a ticket match
        }
        //serializeFixVersions(fixVersions);

        //analyzeCommits(fixVersions, config);
        serializeReqs(reqs, data.fileCommitDates);
        System.out.println("Serialization complete");
    }




    /* Goals
        Get output -> write to file

     */

    /*
        Depending on the arguments passed in, this program will:
            -Go through all the commit logs and match specific issues to commits
            -Go through all the commits and run the maven scripts to build the project and
                then get the static metrics. ALSO build a full list of possible classes to start with.
            -To build the feature list, assume the state of the code at the given ticket is the state of the code
                at the PRIOR commit

             Gather all requirements into an arrayList
             Gather all commits into an ArrayList


     */
    public static CommitData gatherCommits () throws Exception {
        int i = 0;
        XMLConfiguration config = new XMLConfiguration("config.xml");
        String gitRepo = config.getString("git_repo");
        String buildCommand = config.getString("build_command");
        String staticAnalysis = config.getString("static_analysis_command");
        String subProject = config.getString("subproject");
        Repository localRepo = new FileRepository(gitRepo + "/.git");
        Runtime rt = Runtime.getRuntime();
        HashMap<String, ArrayList<GitMetadata>> gitMetas = new HashMap<String, ArrayList<GitMetadata>>();
        HashMap<String, LocalDateTime> fileCommitDates = new HashMap<String, LocalDateTime>();

        FindBugsExtractor ex = new FindBugsExtractor(String.format("%s/%s/", gitRepo, subProject));

        rt.exec("rm " + gitRepo + "/.git/index.lock");

        GitExtractor extractor = new GitExtractor(localRepo);
        Iterable<RevCommit> commits = extractor.getAllCommits();

        /* check out each commit. Get the number of parent commits */

        for (RevCommit commit : commits) {
            GitMetadata meta = new GitMetadata();
            meta.setAuthor(commit.getAuthorIdent().getName());
            meta.setCommitMessage(commit.getFullMessage());
            meta.setCommitId(commit.getName().intern());
            meta.setCommitDate(LocalDateTime.ofInstant(commit.getAuthorIdent().getWhen().toInstant(), ZoneId.systemDefault()));
            meta.setChangedFiles(extractor.getChangedFiles(localRepo, commit));
            meta.setAllFiles(extractor.getAllFiles(localRepo, commit, fileCommitDates));
            System.out.printf("Commit %d\n", i++);

           //if (i > 500)
           //     break;
            String ticketId = getTicketId(meta.getCommitMessage().toLowerCase());

            if (ticketId != null) {
                if (gitMetas.get(ticketId) == null) {
                    ArrayList<GitMetadata> metaList = new ArrayList<GitMetadata>();
                    gitMetas.put(ticketId, metaList);
                }
                gitMetas.get(ticketId).add(meta);

            }

        }
        return new CommitData(gitMetas, fileCommitDates);
    }


    public static TreeMap<String, ClassData> analyzeCommits(TreeMap<String, String> fixVersions, XMLConfiguration config) throws Exception {
        String gitRepo = config.getString("git_repo");
        String buildCommand = config.getString("build_command");
        String staticAnalysis = config.getString("static_analysis_command");
        String subProject = config.getString("subproject");
        Repository localRepo = new FileRepository(gitRepo + "/.git");
        Runtime rt = Runtime.getRuntime();
        FindBugsExtractor ex = new FindBugsExtractor(gitRepo + "/" + subProject, config.getString("find_bugs_relative"));
        GitExtractor extractor = new GitExtractor((localRepo));
        Git git = new Git(localRepo);
        TreeMap<String, ClassData> statics = new TreeMap<String, ClassData>();
        int i = 0;


        for (Map.Entry<String, String> fixVersion : fixVersions.entrySet()) {
            System.out.printf("Fix:%s Commit:%s\n", fixVersion.getKey(), fixVersion.getValue());
            ClassData  metrics = analyzeCommit(git, fixVersion.getValue(), rt, config, ex);
            System.out.println("metrics");
            System.out.println(metrics);
            statics.put(fixVersion.getKey(), metrics);
        }
        System.out.println("static metrics");
        //System.out.println(statics);
        //serializeStatic(statics);
        return statics;
    }



    /* perform static analysis for a single commit */
    public static ClassData analyzeCommit(Git git, String commit, Runtime rt, XMLConfiguration config,
                                             FindBugsExtractor ex) throws Exception {
        ArrayList<ArrayList <TreeMap>> finalMap = new ArrayList<ArrayList <TreeMap>>();
        String gitRepo = config.getString("git_repo");
        String staticAnalysis = config.getString("staticAnalysis");
        ClassData data = new ClassData();

        System.out.println("checking out commit");
        git.checkout().setName(commit).call();

        Process pr = rt.exec(config.getString("build_command"), null, new File(String.format("%s/", gitRepo)));
        printOutput(pr);
        int exitCode = pr.waitFor();

        Process clocPr = rt.exec(config.getString("cloc_command"), null, new File(String.format("%s/", gitRepo)));
        printOutput(pr);
        exitCode = pr.waitFor();
        TreeMap<String, Integer> clocs = readClocFile(config.getString("cloc_file"));
        data.setLinesOfCode(clocs);
        Map<String, ClassMetrics> ckjmMap = readDependencies(gitRepo);
        data.setCkjmMetrics(ckjmMap);

        //now perform the cloc steps and the dependency steps


        //ArrayList<File> xmls = CustomFileUtil.findFileSub(gitRepo, "target", "findBugsXml.xml");
        /* for each subdirectory
        * run the maven script. check the findbugsxml

        File[] directories = new File(gitRepo).listFiles(File::isDirectory);
        for (File dir : directories) {
            System.out.println("now performing static analysis");
            System.out.println(dir.toString());


            Process pr2 = rt.exec(staticAnalysis, null, dir);
            printOutput(pr2);
            File xml = CustomFileUtil.findFileInSub(dir, "target", "findBugsXml.xml");
            System.out.println(xml);
            exitCode = pr2.waitFor();
            if (xml != null)
                finalMap.add(ex.execute(xml));
        }*/
        data.setStaticMetrics(finalMap);
        return data;
    }







}
