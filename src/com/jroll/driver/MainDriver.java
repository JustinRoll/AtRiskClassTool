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
import org.apache.commons.collections4.bidimap.AbstractDualBidiMap;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.XMLConfiguration;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
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
import static com.jroll.util.ReportUtil.testSerializedFile;
import static com.jroll.util.TextParser.*;
import static org.kohsuke.args4j.ExampleMode.ALL;

/**
 * Created by jroll on 9/28/15.
 */
public class MainDriver {


    @Option(name="-reqnum",usage="number of different requirements to process")
    private int reqNum = -1;

    @Option(name="-config",usage="config file to open",metaVar="OUTPUT")
    private String configFile;

    @Option(name="-cpr",usage="number of commits per release",metaVar="OUTPUT")
    private int cpr = -1;

    @Option(name="-split", usage="split file")
    private String splitFile = null;
    // receives other command line parameters than options

    @Option(name="-arff", usage="arff custom file")
    private String arff;

    @Option(name="-join", usage="join together some serialized files")
    private String joinFiles = null;

    @Argument
    private List<String> operations = new ArrayList<String>();



    private String RUN_REQS = "reqs";
    private String RUN_JIRA = "jira";
    private String RUN_QUALITY_REPORT = "report";
    private String RUN_STATICS_REPORT = "report_statics";
    private String STATIC_ANALYSIS = "statics";
    private String RUN_SIMILARITY = "similarity";
    private String CUSTOM_PHP = "php";
    private String RUN_DUMB_BIGTABLE = "dumb";
    private String RUN_BIGTABLE = "big";
    private String RUN_ARFF = "arff";
    private String RUN_FIX = "fix";
    private String RUN_TWEAKS = "tweaks";
    private String DEBUG_SONAR = "debug_sonar";
    private String SONAR_PROPERTIES = "properties";
    private String LANGUAGE = "java";
    private String REPORT_FREQUENCY = "report_freq";
    private String REPORT_RELEASES = "report_releases";
    private String REPORT_REQS = "report_reqs";
    private String SET_EXCLUDE_MISSING = "exclude_missing";
    private String DELETE_NOTOUCH_REQS = "delete_notouch";

    private Boolean IS_SONAR = false;
    private Boolean CREATE_SONAR_PROPERTIES = false;
    private Boolean EXCLUDE_MISSING = true;

    public final String FIX_ERRORS = "fix_errors.txt";
    FinalConfig config;

    public TreeMap<String, ClassData> runStaticMetrics() throws Exception {

        TreeMap<String, String> fixVersions = CustomFileUtil.readFile(config.fixFile, ",");
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

        if (operations.contains(CUSTOM_PHP)) {
            LANGUAGE = "php";
            //analyzeReqsUnorganized(100);
        }



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
        if (operations.contains(RUN_STATICS_REPORT)) {
            testSerializedFile(config);
        }
        if (operations.contains("clean")) {
            ReportUtil.cleanSerializedFile(config, LANGUAGE);
        }

        if (operations.contains(SONAR_PROPERTIES)) {
            CREATE_SONAR_PROPERTIES = true;
        }




        if (operations.contains(RUN_FIX)) {
            serializeFixVersions("." + LANGUAGE);
        }

        if (operations.contains(DEBUG_SONAR)) {
            debugSonar(config);
        }

        if (operations.contains(STATIC_ANALYSIS)) {
            runStaticMetrics();
        }



        if (operations.contains(RUN_DUMB_BIGTABLE) || operations.contains(RUN_BIGTABLE)) {

            TreeMap<String, ClassData> fixToClassData = readClasses(config.staticsFile);
            if (operations.contains(RUN_DUMB_BIGTABLE)) {
                System.out.println("Running DUMB big table routine...");
                serializer.runBigTableNew(fixToClassData, LANGUAGE, EXCLUDE_MISSING);
                if (operations.contains(DELETE_NOTOUCH_REQS))
                    serializer.deleteNoTouch();
            }
            else {
                serializer.runBigTable(fixToClassData);
            }

        }
        if (splitFile != null) {
           serializer.splitFile(config, splitFile);
        }
        if (joinFiles != null) {
            ReportUtil.joinSerializedFiles(joinFiles.split(","), config);
        }

        if (operations.contains(RUN_ARFF) || arff != null) {
            System.out.println("Running ARFF routine...");
            serializer.convertArff(arff);
        }

        if (operations.contains(RUN_TWEAKS)) {
            System.out.println("Running tweaks");
            serializer.runTweaks(config);

        }

        if (operations.contains(REPORT_FREQUENCY)) {
            ReportUtil.reportFreqs(config);
        }

        if (operations.contains(REPORT_RELEASES)) {
            ReportUtil.reportReleases(config, LANGUAGE);
        }

        if (operations.contains(REPORT_REQS)) {
            ReportUtil.reportRequirements(config, LANGUAGE);
        }


    }

    private TreeMap<String, ClassData> loadSerializedMetrics() {
        return null;
    }


    private void debugSonar(FinalConfig config) throws IOException, ConfigurationException, InterruptedException {
        SonarWebApiImpl api = new SonarWebApiImpl(config);
        System.out.println(api.getSonarMetrics());
    }

    private TreeMap<String, HashMap<String, Double>> runSonar(FinalConfig config, Runtime rt) throws IOException, ConfigurationException, InterruptedException {


        SonarWebApiImpl api = new SonarWebApiImpl(config);

        if (rt == null)
            rt = Runtime.getRuntime();

        api.deleteProject();

        Process pr = rt.exec(config.sonarCommand, null, new File(String.format("%s/", config.gitRepo)));
        printOutput(pr);
        int exitCode = pr.waitFor();


        //runCommand("sonar-runner", config.gitRepo);
        try {
            return api.getSonarMetrics();
        }
        catch (Exception e) {
            System.out.println("Couldn't run sonar metrics");
            return new TreeMap<String, HashMap<String, Double>>();
        }

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
        Given our requirement file, parse it into a map
     */
    public  void analyzeReqsUnorganized(int commitsPerRelease) throws Exception {
        int reqCount = 0;
        Serializer s = new Serializer(config);
        List<TreeMap<String, String>> jiraRows;
        if (config.jiraFile.contains(".xls"))
            jiraRows =  JiraExtractor.parseExcel(config.jiraFile);
        else
            jiraRows = JiraExtractor.parseCSVToMapUnorganized(config.jiraFile);

        TreeMap<String, ArrayList<String>> commitToTicket = CustomFileUtil.readFileRev("drupal_data.csv", ",");
        System.out.printf("Commits to ticket:%d\n", commitToTicket.size());
        /* Gather all commit data */
        CommitData data = gatherCommitsUnorganized(commitToTicket);
        System.out.println("Commit Data Done Unorganized");
        System.out.println(config.jiraFile);
        ArrayList<Requirement> reqs = new ArrayList<Requirement>();
        String firstFix = "0";
        System.out.printf("Jira rows: %d\n", jiraRows.size());
        String cpFix = "cp -r " + config.gitRepo + " " + config.fixDataDirectory;
        Set<String> usedTickets = new HashSet<String>();

        TreeMap<String, String> fixVersions = new TreeMap<String, String>();
        //System.out.println(data.gitMetas);

        for (TreeMap<String, String> row : jiraRows) {
            String ticketId = row.get("Key"); //strip project name from ticket
            System.out.printf("Ticket: %s\n", ticketId);
            if (data.gitMetas.get(ticketId) != null && !usedTickets.contains(ticketId)) {//Look up each mapped commit {
                reqCount++;
                usedTickets.add(ticketId);
                Requirement req = new Requirement();
                req.setJiraFields(row);
                req.setId(row.get("Key"));

                String dateText = row.get("Created");
                if (dateParsable(dateText)) {
                    req.setCreateDate(parseDateString(dateText));
                } else if (dateParsableAlt(row.get("Created"))) {
                    req.setCreateDate(parseDateStringAlt(dateText));
                }
                else {

                    req.setCreateDate(parseDateStringAmPm(dateText.replace(" AM", "").replace(" PM", "")));
                }

                req.setGitMetadatas(data.gitMetas.get(ticketId));
                reqs.add(req);
                String fixVersion = req.getJiraFields().get("Key");

                String normalCommit = req.getGitMetadatas().get(0).getCommitId();
                String parentCommit = req.getGitMetadatas().get(0).getParent();
                    System.out.printf("Parent commit: %s Original %s", normalCommit, parentCommit);
                    fixVersions.put(fixVersion, parentCommit != null && parentCommit.length() > 1 ? parentCommit : normalCommit);

                //System.out.println("Found a match");
            }
            else {
                boolean found = false;
                for (ArrayList<String> value : commitToTicket.values()) {
                    for (String item : value) {
                        if (item.equals(ticketId)) {
                            found = true;
                            System.out.println("breaking");
                            break;
                        }
                    }
                }
                if (found)
                    System.out.println("BAD BAD Could not find ticket:" + ticketId);
                else
                    System.out.println("Could not find ticket:" + ticketId);
            }


            //Check to see if there is a ticket match
        }

        System.out.println("Checking Tickets");
        for (ArrayList<String> ticketl : commitToTicket.values()) {
            for (String ticket: ticketl)
                System.out.println(ticket);
        }
        System.out.printf("Mapped Req count:%d\n", reqCount);
        System.out.println("Done");
        //s.serializeFixVersions(fixVersions, firstFix);
        //s.copyFixVersions(fixVersions, "." + LANGUAGE);
        System.out.println("Analyzing Commits");
        analyzeCommits(fixVersions);
        //s.serializeReqsUnorganized(reqs, data.fileCommitDates, LANGUAGE);
        System.out.println("Serialization complete");
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
        System.out.printf("%d jira rows\n", jiraRows.size());
        /* Gather all commit data */
        CommitData data = gatherCommits();
        System.out.println("Commit Data Done");
        ArrayList<Requirement> reqs = new ArrayList<Requirement>();

        String cpFix = "cp -r " + config.gitRepo + " " + config.fixDataDirectory;
        String firstFix = null;

        TreeMap<String, String> fixVersions = new TreeMap<String, String>();

        for (TreeMap<String, String> row : jiraRows) {
            System.out.println(row.get("Key"));
            String ticketId = getTicketId(row.get("Key")); //strip project name from ticket
            System.out.println("New ticketId " + ticketId);
            if (data.gitMetas.get(ticketId) != null) {//Look up each mapped commit {

                Requirement req = new Requirement();
                req.setJiraFields(row);
                req.setId(row.get("Key"));

                req.setCreateDate(parseDateString(row.get("Created")));
                req.setGitMetadatas(data.gitMetas.get(ticketId));
                reqs.add(req);
                System.out.println("Adding ticket " + ticketId);
                String fixVersion = req.getJiraFields().get("Fix Version/s").replaceAll(",", "_");
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
        //s.copyFixVersions(fixVersions, "." + LANGUAGE);
        //analyzeCommits(fixVersions);
        System.out.printf("final req size:%d\n", reqs.size());
        s.serializeReqs(reqs, data.fileCommitDates, LANGUAGE);
        System.out.println("Serialization complete");
    }


    public  void serializeFixVersions(String extension) throws Exception {

        Serializer s = new Serializer(config);

        s.copyFixVersions(CustomFileUtil.readFile(config.fixFile, ","), extension);
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
            if (i++ % 1000 == 0) {
                System.out.printf("Commit %d\n", i);
            }


           if (i > config.reqLimit && config.reqLimit > 0)
                break;
            String ticketId = getTicketId(meta.getCommitMessage().toLowerCase());
            System.out.printf("ticketId: %s message:%s\n", ticketId, meta.getCommitMessage().replaceAll("\n", " "));

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

    /* Gather commits. Read auxilary file to determine the ticket number for each commit*/
    public CommitData gatherCommitsUnorganized (TreeMap<String, ArrayList<String>> commitToTicket) throws Exception {
        int i = 0;
        int ticketCount = 0;
        Repository localRepo = new FileRepository(config.gitRepo + "/.git");
        Runtime rt = Runtime.getRuntime();
        HashMap<String, ArrayList<GitMetadata>> gitMetas = new HashMap<String, ArrayList<GitMetadata>>();
        HashMap<String, LocalDateTime> fileCommitDates = new HashMap<String, LocalDateTime>();
        Set<String> commitIds = new HashSet<String>();

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
            ArrayList<String> tickets = commitToTicket.get(meta.getCommitId());

            if (tickets != null) {
                for (String ticket : tickets) {
                    if (gitMetas.get(ticket) == null) {
                        ticketCount++;
                        System.out.println("this ticket should be in there " + ticket);
                        ArrayList<GitMetadata> metaList = new ArrayList<GitMetadata>();
                        gitMetas.put(ticket, metaList);
                    }
                    gitMetas.get(ticket).add(meta);
                    commitIds.add(meta.getCommitId());
                }
            }

        }
        System.out.println("Checking parsed commits");
        for (String commitId : commitToTicket.keySet()) {
            if (!commitIds.contains(commitId)) {
                System.out.println(commitId + "not in parsed commits");
            }
        }
        System.out.printf("Here's the ticket count in gitMetas:%d\n",ticketCount);
        return new CommitData(gitMetas, fileCommitDates);
    }

    public TreeMap<String, String> getMissingVersions(TreeMap<String, ClassData> orig, TreeMap<String, String> fixes) {

        TreeMap<String, String> finalFixes = new TreeMap<String, String>();

        for (Map.Entry<String, String> entry : fixes.entrySet()) {
            if (orig != null && !orig.containsKey(entry.getKey())) {
                finalFixes.put(entry.getKey(), entry.getValue());
            }
        }

        return finalFixes;
    }


    /* To do: Open ser file. See which fixes we need data for */
    public  TreeMap<String, ClassData> analyzeCommits(TreeMap<String, String> initialfixVersions) throws Exception {
        Repository localRepo = new FileRepository(config.gitRepo + "/.git");
        Runtime rt = Runtime.getRuntime();
        FindBugsExtractor ex = new FindBugsExtractor(config.gitRepo + "/" + config.subProject, config.findBugsRelative);
        GitExtractor extractor = new GitExtractor((localRepo));
        Git git = new Git(localRepo);
        TreeMap<String, ClassData> statics = new TreeMap<String, ClassData>();
        int i = 0;
        TreeMap<String, String> fixVersions = getMissingVersions(readClasses(config.staticsFile), initialfixVersions);
        PrintWriter pw = new PrintWriter(FIX_ERRORS);

        for (Map.Entry<String, String> fixVersion : fixVersions.entrySet()) {
            System.out.printf("Fix:%s Commit:%s\n", fixVersion.getKey(), fixVersion.getValue());
            try {
                ClassData metrics = analyzeCommit(git, fixVersion.getValue(), LANGUAGE, rt, ex);
                FileOutputStream f_out = new
                        FileOutputStream(config.staticsFile);

                ObjectOutputStream obj_out = new
                        ObjectOutputStream(f_out);

                obj_out.writeObject(statics);
                statics.put(fixVersion.getKey(), metrics);
            }
            /* log failed fix to file */
            catch (Exception e) {
                pw.write("Fix " + fixVersion.getKey());
                pw.write("\n");
                pw.write(e.toString());
            }
        }
        System.out.println("static metrics");

        serializeStatic(config, statics);
        System.out.println(readStatics(config.staticsFile));
        return statics;
    }




    /* Serialize static metrics to a file. We need to check the .ser file and see if
        it has already been completed.
     */
    public static void serializeStatic(FinalConfig config, TreeMap<String, ClassData> statics) {
        TreeMap<String, ClassData> origMetrics = readClasses(config.staticsFile);
        for (Map.Entry<String, ClassData> entry: statics.entrySet()) {
            origMetrics.put(entry.getKey(), entry.getValue());
        }

        try
        {
            FileOutputStream fileOut =
                    new FileOutputStream(config.staticsFile);
            ObjectOutputStream out = new ObjectOutputStream(fileOut);
            out.flush();
            out.writeObject(statics);
            out.close();
            fileOut.close();
            System.out.printf("Serialized data is saved in " + config.staticsFile);
        }catch(IOException i)
        {
            i.printStackTrace();
        }

    }

    /* Read the list of classes serialized from file */
    public static TreeMap<String, ClassData>  readClasses(String fileName) {
        TreeMap<String, ClassData> classes = null;
        try {
            ObjectInputStream fileIn = new ObjectInputStream(new FileInputStream(fileName));
            try {
                classes = (TreeMap<String, ClassData>) fileIn.readObject();
            }
            catch(ClassNotFoundException e) {
                System.out.println("Error reading class file");
                System.out.println(e);
            }
            fileIn.close();

        }
        catch(IOException  e) {
            System.out.println("Error reading serialized file");
            System.out.println(e);

        }
        return classes == null ? new TreeMap<String, ClassData>() : classes;
    }


    /* perform static analysis for a single commit */
    public ClassData analyzeCommit(Git git, String commit, String language, Runtime rt,
                                             FindBugsExtractor ex) throws Exception {
        ArrayList<ArrayList<TreeMap>> finalMap = new ArrayList<ArrayList<TreeMap>>();

        ClassData data = new ClassData();

        System.out.println("checking out commit");
        git.reset().setMode( ResetCommand.ResetType.HARD ).call();
        Thread.sleep(1000);
        git.checkout().setName(commit).call();

        if (CREATE_SONAR_PROPERTIES)
            CustomFileUtil.generateSonarProperties(config.gitRepo,config.sonarProject[0], commit, language);

        if (config.buildCommand != null && config.buildCommand != "") {
            System.out.println(config.buildCommand);
            Process pr = rt.exec(config.buildCommand, null, new File(String.format("%s/", config.gitRepo)));
            printOutput(pr);
            int exitCode = pr.waitFor();
        }


        if (this.IS_SONAR) {
            System.out.println("Running sonarqube");

            data.setSonarMetrics(runSonar(config, rt));
        }
        else {
            System.out.println("Running cloc");
            runClocData(rt, data);
        }

        Map<String, ClassMetrics> ckjmMap = readDependencies(config.gitRepo);
        data.setCkjmMetrics(ckjmMap);

        data.setStaticMetrics(finalMap);
        return data;
    }

    public void runClocData(Runtime rt, ClassData data) throws IOException, InterruptedException {
        Process clocPr = rt.exec(config.clocCommand, null, new File(String.format("%s/", config.gitRepo)));
        printOutput(clocPr);
        int exitCode = clocPr.waitFor();
        TreeMap<String, Integer> clocs = readClocFile(config.sonarProject[0], config.clocFile);
        data.setLinesOfCode(clocs);

    }
}
