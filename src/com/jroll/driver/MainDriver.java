package com.jroll.driver;

import com.jroll.exception.FindBugsException;
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

import static com.jroll.util.CKJMUtil.parseData;
import static com.jroll.util.CustomFileUtil.findAllFilesWithExtension;

/**
 * Created by jroll on 9/28/15.
 */
public class MainDriver {

    public static TreeMap<String, String> readFile(String fileName, String delimiter) throws IOException {
        File file = new File(fileName);
        BufferedReader reader = null;
        TreeMap<String, String> commitMap = new TreeMap<String, String>();

        reader = new BufferedReader(new FileReader(file));
        String text = null;

        while ((text = reader.readLine()) != null) {

            String[] line = text.split(delimiter);

            if (line.length >= 1) {
                commitMap.put(line[0], line[1]);
            }
        }

        return commitMap;

    }

    /* read statics, run cloc metrics, read cloc metrics, read/run dependency info */
    public static TreeMap<String, Integer> readClocFile(String fileName) throws IOException {
        File file = new File(fileName);
        BufferedReader reader = null;
        TreeMap<String, Integer> commitMap = new TreeMap<String, Integer>();

        reader = new BufferedReader(new FileReader(file));
        String text = null;
        text = reader.readLine();

        while ((text = reader.readLine()) != null) {
            String[] line = text.split(",");
            if (line.length >= 5 && line[0].toLowerCase().equals("java") && line[1].contains("org/apache/qpid")) {
                commitMap.put(line[1].substring(line[1].indexOf("org/apache/qpid")), Integer.parseInt(line[4]));
            }
            else {
                System.out.println("bad class at " + line[1]);
            }
        }

        return commitMap;

    }


    public static TreeMap<String, TreeMap<String, Integer>> readStatics(String fileName) throws IOException {
        File file = new File(fileName);
        BufferedReader reader = null;

        TreeMap<String, TreeMap<String, Integer>> classStatics = new TreeMap<String, TreeMap<String, Integer>>();

        /*
           Map of fix version to map of classes with count of stuff
         */

        reader = new BufferedReader(new FileReader(file));
        String text = null;

        while ((text = reader.readLine()) != null) {
            String[] line = text.split(",");
            System.out.println(line);
            if (line.length >= 4 && !line[1].contains("SECURITY")) {
                if (classStatics.get(line[0]) == null) {
                    classStatics.put(line[0], new TreeMap<String, Integer>());
                }
                TreeMap<String, Integer> bugMap = classStatics.get(line[0]);
                if (bugMap.get(line[3]) == null) {
                    bugMap.put(line[3], 1);
                }
                else {
                    bugMap.put(line[3], bugMap.get(line[3]) + 1);
                }
            }
        }
        System.out.println(classStatics);
        return classStatics;

    }


    public static TreeMap<String, ClassData> runStaticMetrics(XMLConfiguration config) throws Exception {

        TreeMap<String, String> fixVersions = readFile("fix.txt", "\\|");
        System.out.println(fixVersions);
        return analyzeCommits(fixVersions, config);
    }

    public static void main(String[] args) throws Exception {
        XMLConfiguration config = new XMLConfiguration("config.xml");
        TreeMap<String, ClassData> fixToClassData = runStaticMetrics(config);
        runBigTable(fixToClassData);
        ARFFGenerator.convertFile("bigTable.txt", "bigTable.arff");
        //oldmain(args);
    }

    public static Map<String, ClassMetrics> readDependencies(String repo) throws Exception {
        Set<String> files = new HashSet<String>();
        findAllFilesWithExtension(".class", new File(repo).listFiles(), files);

        Map<String, ClassMetrics> fileMetrics = parseData(files);

        return fileMetrics;
    }

    public static String trimCustom(String inputPath, String replaceString) {
        int index = inputPath.indexOf(replaceString);
        return index >= 0 ? inputPath.substring(index) : inputPath;
    }

    public static int getStaticCount(String fixName, String className, TreeMap<String, TreeMap<String, Integer>> staticCount) {
        if (staticCount == null || className == null || staticCount == null)
            return 0;
        else if (staticCount.get(fixName) != null && staticCount.get(fixName).get(className) != null) {
            return staticCount.get(fixName).get(className);
        }
        return 0;
    }


    public static TreeMap<String, TreeMap<String, Double>> readVsm(File file) throws IOException {
        BufferedReader reader = null;
        TreeMap<String, TreeMap<String, Double>> lines = new TreeMap<String, TreeMap<String, Double>>();

        reader = new BufferedReader(new FileReader(file));
        String text = null;
        String[] header = reader.readLine().split(",");

        while ((text = reader.readLine()) != null) {
            String[] line = text.split(",");
            if (lines.get(line[0]) == null) {
                TreeMap<String, Double> map = new TreeMap<String, Double>();
                lines.put(line[1], map);
                for (int i = 2; i < line.length; i++) {
                    map.put(header[i], Double.parseDouble(line[i]));
                }
            }
        }
        return lines;
    }

    public static void runBigTable(TreeMap<String, ClassData> fixToClassData) throws Exception {
        XMLConfiguration config = new XMLConfiguration("config.xml");

        TreeMap<String, TreeMap<String, Integer>> staticMap = readStatics("statics2.txt");
        TreeMap<String, Integer> frequencyMap = new TreeMap<String, Integer>();

        //This field will map a class to its frequency of change
        float ticketCount = 0.0f;
        String prevTicket = null;
        PrintWriter writer = new PrintWriter("bigTable.txt");


        File file = new File("outOnlyReq.txt");
        File sims = new File("ReqSimilarity_12.csv");
        TreeMap<String, TreeMap<String, Double>> scores = readVsm(sims);

        BufferedReader reader = null;
        TreeMap<String, String> commitMap = new TreeMap<String, String>();
        HashMap<String, Integer> headerMap = new HashMap<String, Integer>();
        TreeMap<String, String> fileMap = readFile("fileMap.txt", "\\|\\|");
        TreeMap<String, String> trimmedFileMap = new TreeMap<String, String>();

        for (Map.Entry<String, String> entry : fileMap.entrySet()) {
            trimmedFileMap.put(entry.getKey(), trimCustom(entry.getValue(), "org/apache"));
        }
        System.out.println(fileMap.toString() + " file map");
        System.out.println(trimmedFileMap.toString() + " trimmed file map");
        reader = new BufferedReader(new FileReader(file));
        String[] header = reader.readLine().split("|");

        for (int i = 0; i < header.length; i++) {
            headerMap.put(header[i], i);
        }
        String text = null;

        String ckjmHeader = "ckjm_noc\tckjm_wmc\tckjm_rfc\tckjm_cbo\tckjm_dit\tckjm_lcom\tckjm_ca\tckjm_npm";

        String HEADER = "Ticket\tLast 10\tFix Version\tIssue Type\tLast Commit Time\tReq Created\tCommits\tFile\tRequirement\tBugCount\tChangeHistory\t";
        HEADER += "Vsm_MaxSimilarity\tVsm_AverSimilarity\tJsd_MaxSimilarity\tJsd_AverSimilarity\tCmJcn_MaxSimilarity\tCmJcn_AverSimilarity\tGreedyRes_MaxSimilarity\tGreedyRes_AverSimilarity\tBleuLinMaxSimilarity\tBleuLin_AverSimilarity\tOptWup_MaxSimilarity\tOptWup_AverSimilarity\t";
        HEADER += ckjmHeader;
        HEADER += "\tloc\t";
        HEADER += "Changed?\n";
        writer.write(HEADER);

        int count = 0;
        while ((text = reader.readLine()) != null) {
            count++;
            String[] line = text.replaceAll("\t", " ").split("\\|");
            String fix = line[2];
            System.out.println(fix);
            ClassData currentFix = fixToClassData.get(fix);


            if (!line[0].equals(prevTicket)) {
                ticketCount++;
            }
            prevTicket = line[0];

            String className = trimmedFileMap.get(line[7]);
            Integer freq = frequencyMap.get(className) == null ? 0 : frequencyMap.get(className);
            if (line[9].trim().equals("1")) {
                frequencyMap.put(className, freq + 1);
                System.out.println(line[0] + " " + line[7] + "freq increase");
            }
            else {
                System.out.println(line[0] + " " + line[7] + " freq same");
            }
            int staticCount = getStaticCount(line[2], className, staticMap);
            String last10 = line[1].replaceAll(",", " ");
            String firstFields = String.format("%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%.5f\t", line[0], last10, line[2].replaceAll(",", "|"), line[3], line[4], line[5], line[6], line[7], line[8], staticCount, Math.min(1.0, freq / ticketCount));
            String[] lsmHeader = "Vsm_MaxSimilarity,Vsm_AverSimilarity,Jsd_MaxSimilarity,Jsd_AverSimilarity,CmJcn_MaxSimilarity,CmJcn_AverSimilarity,GreedyRes_MaxSimilarity,GreedyRes_AverSimilarity,BleuLinMaxSimilarity,BleuLin_AverSimilarity,OptWup_MaxSimilarity,OptWup_AverSimilarity".split(",");
            System.out.println(fixToClassData);
            System.out.println(currentFix);
            ClassMetrics cm = currentFix.getCkjmMetrics().get(className.replaceAll(".java", "").replaceAll("/", "."));
            Integer loc = currentFix.getLinesOfCode().get(className);
            System.out.println(className);
            String ckjmMetrics = "";
            if (cm != null) {
                ckjmMetrics += String.format("%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d", cm.getNoc(),
                        cm.getWmc(), cm.getRfc(), cm.getCbo(), cm.getDit(), cm.getLcom(), cm.getCa(), cm.getNpm());
                System.out.println("found class data!");
            }
            else {
                //"ckjm_noc\tckjm_wmc\tckjm_rfc\tckjm_cbo\tckjm_dit\tckjm_lcom\tckjm_ca\tckjm_npm";
                ckjmMetrics += "-1\t-1\t-1\t-1\t-1\t-1\t-1\t-1";
            }
            ckjmMetrics += String.format("\t%d", loc != null ? loc : -1);

            for (int i = 0; i < 12; i++) {
                firstFields += String.format("% .5f\t", scores.get(line[0]).get(lsmHeader[i]));
            }
            firstFields += ckjmMetrics;
            firstFields +=String.format("\t%s\n", line[9].equals("1") ? "y" : "n");
            /*
            Vsm_MaxSimilarity,Vsm_AverSimilarity,Jsd_MaxSimilarity,Jsd_AverSimilarity,CmJcn_MaxSimilarity,CmJcn_AverSimilarity,GreedyRes_MaxSimilarity,GreedyRes_AverSimilarity,BleuLinMaxSimilarity,BleuLin_AverSimilarity,OptWup_MaxSimilarity,OptWup_AverSimilarity
             */
            //Ticket|Last 10|Fix Version|Issue Type|Last Commit Time|Req Created|Commits|File|Requirement|BugCount|ChangeHistory|Changed?
            if (trimmedFileMap.get(line[7]).contains("qpid") && loc != null && loc > 0 && cm != null) {
                writer.write(firstFields);
            }
        }
        writer.flush();
        writer.close();

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

        analyzeCommits(fixVersions, config);
        serializeReqs(reqs, data.fileCommitDates);
        System.out.println("Serialization complete");
    }

    /* Serialize to file mapping first. Then export folder structure
    * .24
    * .28
    * .34
    * etc */
    public static void serializeFixVersions(TreeMap<String, String> fixVersions) throws IOException, ConfigurationException, GitAPIException, InterruptedException {
        PrintWriter fixWriter = new PrintWriter("fix.txt", "UTF-8");
        /* initialize gitrepo. check out commit. copy over the fix directory to
        a directory name of .24, .26
         */
        XMLConfiguration config = new XMLConfiguration("config.xml");
        String gitRepo = config.getString("git_repo");
        String cpFix = "cp -r " + gitRepo + " /Users/jroll/Downloads/data/";
        String staticAnalysis = config.getString("static_analysis_command");
        Repository localRepo = new FileRepository(gitRepo + "/.git");
        Runtime rt = Runtime.getRuntime();

        for (Map.Entry<String, String> fixVersion : fixVersions.entrySet()) {
            Git git = new Git(localRepo);
            git.checkout().setName(fixVersion.getValue()).call();

            Process pr = rt.exec(cpFix + fixVersion.getKey(),
                    null, new File(String.format("%s/", gitRepo)));
            int exitCode = pr.waitFor();
            System.out.printf("Done Fix Number:%s %d\n", fixVersion.getKey(), exitCode);
            System.out.println(cpFix + fixVersion.getKey());

            fixWriter.write(fixVersion.getKey() + "," + fixVersion.getValue() + "\n");
        }
        fixWriter.close();
    }

    public static LocalDateTime parseDateString(String text) {
        //06/Oct/13 11:01
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MMM/yy HH:mm");
        return LocalDateTime.parse(text, formatter);
    }

    private static String stringifyCommits(ArrayList<GitMetadata> metas) {
        String s = "";
        for (GitMetadata meta : metas) {
            s += meta.getCommitId();
            s += " ";
        }
        return s.trim();
    }

    public static String getExtension(String fileName) {
        return fileName.substring(fileName.lastIndexOf(".") + 1, fileName.length());
    }

    /* Filter out all files that were created AFTER the requirement time */
    public static Set<String> filterFiles(Requirement req, HashMap<String, LocalDateTime> map) {
        List<String> allFiles = new ArrayList<String>();
        String JAVA = "java";
        for (GitMetadata meta : req.getGitMetadatas()) {

            List<String> files = meta.getAllFiles().stream().filter(f -> !meta.getChangedFiles().contains(f)
                    && JAVA.equals(getExtension(f).toLowerCase()) &&
            req.getCreateDate().isAfter(map.get(f))).collect(Collectors.toList());
            allFiles.addAll(files);
        }
        System.out.println(allFiles.size());
        return new HashSet<String>(allFiles);
    }

    public static Set<String> addChangedFiles(ArrayList<GitMetadata> commits) {
        ArrayList<String> fileList = new ArrayList<String>();

        for (GitMetadata commit : commits) {

            fileList.addAll(commit.getChangedFiles().stream().filter(f -> "java".equals(getExtension(f))).collect(Collectors.toList()));
        }
        return new HashSet<String>(fileList);
    }

    public static LocalDateTime getLastCommitTime(ArrayList<GitMetadata> metas) {
        LocalDateTime lastCommit = null;

        for (GitMetadata meta : metas) {
            if (lastCommit == null || meta.getCommitDate().isAfter(lastCommit)) {
                lastCommit = meta.getCommitDate();
            }
        }
        return lastCommit;
    }

    private static void serializeReq(Requirement req, PrintWriter writer, String lastCommit, String last10, int fid, int rid, int changed) {
        //System.out.printf("%s||%s||%s||%s||%d", req.getId(), req.getGitMetadatas().get(0).getCommitId(), req.getJiraFields().get("Description"), file, 1);
        writer.write(String.format("%s|%s|%s|%s|%s|%s|%s|%d|%d|%d\n", req.getId(), last10, req.getJiraFields().get("Fix Version/s"), req.getJiraFields().get("Issue Type"), lastCommit.toString(), req.getJiraFields().get("Created"), stringifyCommits(req.getGitMetadatas()),
                fid, rid, changed));
    }

    private static void serializeReqs(ArrayList<Requirement> reqs, HashMap<String, LocalDateTime> map) throws FileNotFoundException, UnsupportedEncodingException {
        PrintWriter reqWriter = new PrintWriter("reqMap.txt", "UTF-8");
        PrintWriter fileWriter = new PrintWriter("fileMap.txt", "UTF-8");
        PrintWriter writer = new PrintWriter("outOnlyReq.txt", "UTF-8");
        PrintWriter bugWriter = new PrintWriter("bug.txt", "UTF-8");

        writer.write("Ticket|Last 10|Fix Version|Issue Type|Last Commit Time|Req Created|Commits|File|Requirement|Changed?\n");

        int reqCount = 0;
        int fileCount = 0;
        HashMap<String, Integer> fileMap = new HashMap<String, Integer>();
        HashMap<String, Integer> reqMap = new HashMap<String, Integer>();
        Queue<String> lastTickets = new ArrayBlockingQueue<String>(10);
        Set<String> versions = new HashSet<String>();

        for (Requirement req : reqs.stream().filter(r -> !"0.25".equals(r.getJiraFields().get("Fix Version/s").trim()))
                .collect(Collectors.toList())) {
            boolean bug = "Bug".equals(req.getJiraFields().get("Issue Type"));
            String reqText = req.getJiraFields().get("Description").replaceAll("\n", " ");
            LocalDateTime lastCommit = getLastCommitTime(req.getGitMetadatas());
            String last10 = String.join(",", Arrays.asList(lastTickets.toArray(new String[10])).stream().filter(f -> f != null)
                    .collect(Collectors.toList()));
            int rid = reqCount;

            versions.add(req.getJiraFields().get("Fix Version/s"));

            for (String file : addChangedFiles(req.getGitMetadatas())) {
                int fid = fileCount;


                if (fileMap.get(file) != null) {
                   fid = fileMap.get(file);
                }
                else {
                    fileMap.put(file, fileCount);
                    fileWriter.write(String.format("%d||%s\n", fileCount, file));
                    fileCount++;
                }
                if (reqMap.get(reqText) != null) {
                    rid = reqMap.get(reqText);
                }
                else {
                    reqMap.put(reqText, reqCount);
                    reqWriter.write(String.format("%d||%s\n", reqCount, reqText ));
                    reqCount++;

                }

                serializeReq(req, bug?bugWriter:writer, lastCommit.toString(), last10, fid, rid, 1);
            }


            for (String file: filterFiles(req, map)) {

                int fid = fileCount;


                if (fileMap.get(file) != null) {
                    fid = fileMap.get(file);
                }
                else {
                    fileMap.put(file, fileCount);
                    fileWriter.write(String.format("%d||%s\n", fileCount, file));
                    fileCount++;
                }
                serializeReq(req, bug?bugWriter:writer, lastCommit.toString(), last10, fid, rid, 0);
            }

            if (lastTickets.size() >= 10)
                lastTickets.poll();
            lastTickets.offer(req.getId());
        }

        System.out.println(versions);
        reqWriter.close();
        fileWriter.close();
        writer.close();
    }

    private static String getTicketId(String concreteLine) {
        List<String> allMatches = new ArrayList<String>();

        Pattern pattern = Pattern.compile(".*qpid-([0-9]+).*");
        Matcher matcher = pattern.matcher(concreteLine);

        while (matcher.find()) {
            allMatches.add(matcher.group(1));
        }

        return allMatches.size() > 0 ? allMatches.get(0) : null;
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

 //           if (i > 1000)
 //               break;
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
        FindBugsExtractor ex = new FindBugsExtractor(gitRepo + "/" + subProject);
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

    private static void serializeStatic(TreeMap<String, ClassData> treeMapList) throws FileNotFoundException {
        PrintWriter writer = new PrintWriter("statics.txt");
        for (Map.Entry<String, ClassData> entry : treeMapList.entrySet()) {
            for (ArrayList<TreeMap> treeMaps : entry.getValue().getStaticMetrics()) {
                for (TreeMap row : treeMaps) {
                    writer.write(String.format("%s, %s,%s,%s,%s\n", entry.getKey(), row.get("category"), row.get("classname"), row.get("classpath"), row.get("rank")));
                }
            }
        }
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

    public static String parseFindBugs(FindBugsExtractor ex) throws FindBugsException {
        List<TreeMap> rows = ex.execute(new File(ex.xmlFile));
        System.out.println("commit, category, classname, classpath, rank");

        String str = "";

        for (TreeMap row : rows) {
            str += getRow(row, "HEAD");
        }

        return str;
    }

    public static void printOutput(Process pr) throws IOException {
        BufferedReader stdInput = new BufferedReader(new
                InputStreamReader(pr.getInputStream()));

        // read the output from the command
        System.out.println("Here is the standard output of the command:\n");
        String s = null;
        while ((s = stdInput.readLine()) != null) {
            //just idle here. We can print if we want to debug
        }
    }


    public static String getRow(TreeMap<String, String> row, String commit) {
        return String.format("%s, %s,%s,%s,%s\n", commit, row.get("category"), row.get("classname"), row.get("classpath"), row.get("rank"));
    }
}
