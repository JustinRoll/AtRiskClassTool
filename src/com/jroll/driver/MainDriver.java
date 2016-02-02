package com.jroll.driver;

import com.jroll.data.ClassData;
import com.jroll.data.CommitData;
import com.jroll.data.GitMetadata;
import com.jroll.data.Requirement;
import com.jroll.extractors.FindBugsExtractor;
import com.jroll.extractors.GitExtractor;
import com.jroll.extractors.JiraExtractor;
import com.jroll.sonar.SonarWebApiImpl;
import com.jroll.util.*;
import gr.spinellis.ckjm.ClassMetrics;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.XMLConfiguration;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import java.io.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

import static com.jroll.command.CommandExecutor.printOutput;
import static com.jroll.command.CommandExecutor.runCommand;
import static com.jroll.util.CKJMUtil.readDependencies;
import static com.jroll.util.CustomFileUtil.*;
import static com.jroll.util.ReportUtil.tabulate_report;
import static com.jroll.util.TextParser.getTicketId;
import static com.jroll.util.TextParser.parseDateString;
import static org.kohsuke.args4j.ExampleMode.ALL;

/**
 * Created by jroll on 9/28/15.
 */
public class MainDriver {


    @Option(name="-reqnum",usage="number of different requirements to process")
    private int reqNum = -1;

    @Option(name="-config",usage="config file to open",metaVar="OUTPUT")
    private String configFile;

    // receives other command line parameters than options
    @Argument
    private List<String> operations = new ArrayList<String>();

    private String RUN_REQS = "reqs";
    private String RUN_JIRA = "jira";
    private String RUN_QUALITY_REPORT = "report";
    private String ANALYZE_COMMITS = "commits";
    private String RUN_SIMILARITY = "similarity";
    private String RUN_DUMB_BIGTABLE = "dumb";
    private String RUN_BIGTABLE = "big";
    private String RUN_ARFF = "arff";
    private String RUN_FIX = "fix";
    private String DEBUG_SONAR = "debug_sonar";
    private Boolean IS_SONAR = false;

    FinalConfig config;



    public TreeMap<String, ClassData> runStaticMetrics() throws Exception {

        TreeMap<String, String> fixVersions = CustomFileUtil.readFile(config.fixFile, "\\|");
        System.out.println(fixVersions);
        return analyzeCommits(fixVersions);
    }

    public static void main(String[] args) throws Exception {
        new MainDriver().doMain(args);
    }



    public  void parse(String[] args) {
        CmdLineParser parser = new CmdLineParser(this);

        // if you have a wider console, you could increase the value;
        // here 80 is also the default
        parser.setUsageWidth(80);

        try {
            // parse the arguments.
            parser.parseArgument(args);

            // you can parse additional arguments if you want.
            // parser.parseArgument("more","args");

            // after parsing arguments, you should check
            // if enough arguments are given.
            if( operations.isEmpty() )
                throw new CmdLineException(parser,"No operation is given");

        } catch( CmdLineException e ) {
            // if there's a problem in the command line,
            // you'll get this exception. this will report
            // an error message.
            System.err.println(e.getMessage());
            System.err.println("java predictor [options...] arguments...");
            // print the list of available options
            parser.printUsage(System.err);
            System.err.println();

            // print option sample. This is useful some time
            System.err.println("  Example: java predictor"+parser.printExample(ALL));

            return;
        }
    }
    public void doMain(String[] args) throws Exception {
        parse(args);
        XMLConfiguration initialConfig = new XMLConfiguration(configFile);
        config = new FinalConfig(initialConfig);
        Serializer serializer = new Serializer(config);

        if (operations.contains(DEBUG_SONAR) || operations.contains("sonar"))
            IS_SONAR = true;

        if(operations.contains(RUN_JIRA)) {
            /* open the git repo. Get the date of the first commit in the repo. Pass that into the pull
            JiraDataDirectory
             */
            Repository repo = new FileRepository(config.gitRepo + "/.git");
            LocalDateTime firstCommitDate = GitExtractor.getFirstCommitTime(repo);

            JiraExtractor.pullJiraData(config.jiraFile, config.jiraDirectory, config.jiraUrl, firstCommitDate.toLocalDate());
        }

        if (operations.contains(RUN_QUALITY_REPORT)) {
            tabulate_report(config);

        }
        if (operations.contains(RUN_REQS)) {
            analyzeReqs();
        }

        if (operations.contains(RUN_FIX)) {
            serializeFixVersions();
        }

        if (operations.contains(DEBUG_SONAR)) {
            debugSonar(config);
        }

        if (operations.contains(RUN_DUMB_BIGTABLE) || operations.contains(RUN_BIGTABLE)) {

            TreeMap<String, ClassData> fixToClassData = runStaticMetrics();
            if (operations.contains(RUN_DUMB_BIGTABLE)) {
                System.out.println("Running DUMB big table routine...");
                serializer.runBigTableDumb(fixToClassData);
            }
            else {
                serializer.runBigTable(fixToClassData);
            }

        }

        if (operations.contains(RUN_ARFF)) {
            System.out.println("Running ARFF routine...");
            serializer.convertArff();
        }


    }

    private void debugSonar(FinalConfig config) throws IOException, ConfigurationException {
        SonarWebApiImpl api = new SonarWebApiImpl(config);
        System.out.println(api.getSonarMetrics());
    }

    private TreeMap<String, HashMap<String, Double>> runSonar(FinalConfig config) throws IOException, ConfigurationException, InterruptedException {
        SonarWebApiImpl api = new SonarWebApiImpl(config);
        runCommand("sonar-runner", config.gitRepo);
        return api.getSonarMetrics();
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

    public  void analyzeReqs() throws Exception {

        Serializer s = new Serializer(config);
        List<TreeMap<String, String>> jiraRows;
        if (config.jiraFile.contains(".xls"))
            jiraRows =  JiraExtractor.parseExcel(config.jiraFile);
        else
            jiraRows = JiraExtractor.parseCSVToMap(config.jiraFile);

        System.out.println("Jira Data Done");
        /* Gather all commit data */
        CommitData data = gatherCommits();
        System.out.println("Commit Data Done");
        ArrayList<Requirement> reqs = new ArrayList<Requirement>();

        String cpFix = "cp -r " + config.gitRepo + " " + config.fixDataDirectory;
        String firstFix = null;

        TreeMap<String, String> fixVersions = new TreeMap<String, String>();

        for (TreeMap<String, String> row : jiraRows) {
            String ticketId = row.get("Key").replaceAll("[^0-9]", ""); //strip project name from ticket
            if (data.gitMetas.get(ticketId) != null) {//Look up each mapped commit {

                Requirement req = new Requirement();
                req.setJiraFields(row);
                req.setId(row.get("Key"));

                req.setCreateDate(parseDateString(row.get("Created")));
                req.setGitMetadatas(data.gitMetas.get(ticketId));
                reqs.add(req);
                String fixVersion = req.getJiraFields().get("Fix Version/s");
                if (firstFix == null)
                    firstFix = fixVersion;

                if (fixVersions.get(fixVersion) == null) {
                    String normalCommit = req.getGitMetadatas().get(0).getCommitId();
                    String parentCommit = req.getGitMetadatas().get(0).getParent();
                    System.out.printf("Parent commit: %s Original %s", normalCommit, parentCommit);
                    fixVersions.put(fixVersion, parentCommit != null && parentCommit.length() > 1 ? parentCommit : normalCommit);
                }

                //System.out.println("Found a match");
        }


            //Check to see if there is a ticket match
        }

        s.serializeFixVersions(fixVersions, firstFix);
        s.copyFixVersions(fixVersions);
        //analyzeCommits(fixVersions);
        s.serializeReqs(reqs, data.fileCommitDates);
        System.out.println("Serialization complete");
    }


    public  void serializeFixVersions() throws Exception {

        Serializer s = new Serializer(config);

        s.copyFixVersions(CustomFileUtil.readFile(config.fixFile, ","));
    }


    public  void serializeFixVersionsOld() throws Exception {

        Serializer s = new Serializer(config);
        List<TreeMap<String, String>> jiraRows;
        if (config.jiraFile.contains(".xls"))
            jiraRows =  JiraExtractor.parseExcel(config.jiraFile);
        else
            jiraRows = JiraExtractor.parseCSVToMap(config.jiraFile);

        System.out.println("Jira Data Done");
        /* Gather all commit data */
        CommitData data = gatherCommits();
        System.out.println("Commit Data Done");
        ArrayList<Requirement> reqs = new ArrayList<Requirement>();
        String firstFix = null;

        String cpFix = "cp -r " + config.gitRepo + " " + config.fixDataDirectory;

        System.out.println(cpFix);
        TreeMap<String, String> fixVersions = new TreeMap<String, String>();

        for (TreeMap<String, String> row : jiraRows) {
            String ticketId = row.get("Key").replaceAll("[^0-9]", ""); //strip project name from ticket
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
                if (firstFix == null)
                    firstFix = fixVersion;
                //System.out.println("Found a match");
            }


            //Check to see if there is a ticket match
        }
        s.serializeFixVersions(fixVersions, firstFix);
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
    public CommitData gatherCommits () throws Exception {
        int i = 0;

        Repository localRepo = new FileRepository(config.gitRepo + "/.git");
        Runtime rt = Runtime.getRuntime();
        HashMap<String, ArrayList<GitMetadata>> gitMetas = new HashMap<String, ArrayList<GitMetadata>>();
        HashMap<String, LocalDateTime> fileCommitDates = new HashMap<String, LocalDateTime>();

        FindBugsExtractor ex = new FindBugsExtractor(String.format("%s/%s/", config.gitRepo, config.subProject), config.findBugsRelative);

        rt.exec("rm " + config.gitRepo + "/.git/index.lock");

        GitExtractor extractor = new GitExtractor(localRepo);
        Iterable<RevCommit> commits = extractor.getAllCommits();

        /* check out each commit. Get the number of parent commits */

        for (RevCommit commit : commits) {
            GitMetadata meta = new GitMetadata();
            meta.setAuthor(commit.getAuthorIdent().getName());
            meta.setCommitMessage(commit.getFullMessage());
            meta.setCommitId(commit.getName().intern());
            if (commit.getParentCount() > 0)
                meta.setParent(commit.getParent(0).getName());

            meta.setCommitDate(LocalDateTime.ofInstant(commit.getAuthorIdent().getWhen().toInstant(), ZoneId.systemDefault()));
            meta.setChangedFiles(extractor.getChangedFiles(localRepo, commit));
            meta.setAllFiles(extractor.getAllFiles(localRepo, commit, fileCommitDates));
            if (i++ % 1000 == 0)
                System.out.printf("Commit %d\n", i);


           if (i > config.reqLimit && config.reqLimit > 0)
                break;
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


    public  TreeMap<String, ClassData> analyzeCommits(TreeMap<String, String> fixVersions) throws Exception {
        Repository localRepo = new FileRepository(config.gitRepo + "/.git");
        Runtime rt = Runtime.getRuntime();
        FindBugsExtractor ex = new FindBugsExtractor(config.gitRepo + "/" + config.subProject, config.findBugsRelative);
        GitExtractor extractor = new GitExtractor((localRepo));
        Git git = new Git(localRepo);
        TreeMap<String, ClassData> statics = new TreeMap<String, ClassData>();
        int i = 0;


        for (Map.Entry<String, String> fixVersion : fixVersions.entrySet()) {
            System.out.printf("Fix:%s Commit:%s\n", fixVersion.getKey(), fixVersion.getValue());
            ClassData  metrics = analyzeCommit(git, fixVersion.getValue(), rt, ex);
            FileOutputStream f_out = new
                    FileOutputStream(config.sonarProject + ".ser");

            ObjectOutputStream obj_out = new
                    ObjectOutputStream (f_out);

            obj_out.writeObject(statics);
            statics.put(fixVersion.getKey(), metrics);
        }
        System.out.println("static metrics");
        //System.out.println(statics);
        //serializeStatic(statics);
        return statics;
    }



    /* perform static analysis for a single commit */
    public ClassData analyzeCommit(Git git, String commit, Runtime rt,
                                             FindBugsExtractor ex) throws Exception {
        ArrayList<ArrayList<TreeMap>> finalMap = new ArrayList<ArrayList<TreeMap>>();

        ClassData data = new ClassData();

        System.out.println("checking out commit");
        git.checkout().setName(commit).call();

        Process pr = rt.exec(config.buildCommand, null, new File(String.format("%s/", config.gitRepo)));
        printOutput(pr);
        int exitCode = pr.waitFor();


        if (this.IS_SONAR) {
            data.setSonarMetrics(runSonar(config));
        }
        else {
            runClocData(pr, rt, data);
        }

        Map<String, ClassMetrics> ckjmMap = readDependencies(config.gitRepo);
        data.setCkjmMetrics(ckjmMap);

        data.setStaticMetrics(finalMap);
        return data;
    }

    public void runClocData(Process pr, Runtime rt, ClassData data) throws IOException, InterruptedException {
        Process clocPr = rt.exec(config.clocCommand, null, new File(String.format("%s/", config.gitRepo)));
        printOutput(pr);
        int exitCode = pr.waitFor();
        TreeMap<String, Integer> clocs = readClocFile(config.sonarProject, config.clocFile);
        data.setLinesOfCode(clocs);

    }
}
