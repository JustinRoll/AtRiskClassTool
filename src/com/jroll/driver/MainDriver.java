package com.jroll.driver;

import com.jroll.exception.FindBugsException;
import com.jroll.extractors.FindBugsExtractor;
import com.jroll.extractors.GitExtractor;
import com.jroll.extractors.JiraExtractor;
import com.jroll.util.CommitData;
import com.jroll.util.GitMetadata;
import com.jroll.util.Requirement;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.XMLConfiguration;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.CheckoutConflictException;
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

/**
 * Created by jroll on 9/28/15.
 */
public class MainDriver {

    public static TreeMap<String, String> readFile() throws IOException {
        File file = new File("fix.txt");
        BufferedReader reader = null;
        TreeMap<String, String> commitMap = new TreeMap<String, String>();

        reader = new BufferedReader(new FileReader(file));
        String text = null;

        while ((text = reader.readLine()) != null) {
            String[] line = text.split(",");
            if (line.length == 2) {
                commitMap.put(line[0], line[1]);
            }
        }

        return commitMap;

    }

    public static void main(String[] args) throws Exception {
        XMLConfiguration config = new XMLConfiguration("config.xml");
        TreeMap<String, String> fixVersions = readFile();
        analyzeCommits(fixVersions, config);
    }
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
        serializeFixVersions(fixVersions);

        analyzeCommits(fixVersions, config);
        //serializeReqs(reqs, data.fileCommitDates);
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
                fid, rid, 1));
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


    public static void analyzeCommits(TreeMap<String, String> fixVersions, XMLConfiguration config) throws Exception {
        String gitRepo = config.getString("git_repo");
        String buildCommand = config.getString("build_command");
        String staticAnalysis = config.getString("static_analysis_command");
        String subProject = config.getString("subproject");
        Repository localRepo = new FileRepository(gitRepo + "/.git");
        Runtime rt = Runtime.getRuntime();
        FindBugsExtractor ex = new FindBugsExtractor(gitRepo + "/" + subProject);
        GitExtractor extractor = new GitExtractor((localRepo));
        Git git = new Git(localRepo);
        TreeMap<String, ArrayList<TreeMap>> statics = new TreeMap<String, ArrayList<TreeMap>>();

        for (Map.Entry<String, String> fixVersion : fixVersions.entrySet()) {
            ArrayList<TreeMap> metrics = analyzeCommit(git, fixVersion.getValue(), rt, buildCommand, gitRepo, subProject, staticAnalysis, ex);
            System.out.println("metrics");
            System.out.println(metrics);
            statics.put(fixVersion.getKey(), metrics);
        }
        System.out.println("static metrics");
        System.out.println(statics);
        serializeStatic(statics);
    }

    private static void serializeStatic(TreeMap<String, ArrayList<TreeMap>> treeMaps) throws FileNotFoundException {
        PrintWriter writer = new PrintWriter("statics.txt");
        for (Map.Entry<String, ArrayList<TreeMap>> entry : treeMaps.entrySet()) {
            for (TreeMap row : entry.getValue()) {
                writer.write(String.format("%s, %s,%s,%s,%s\n", entry.getKey(), row.get("category"), row.get("classname"), row.get("classpath"), row.get("rank")));
            }
        }
    }

    /* perform static analysis for a single commit */
    public static ArrayList<TreeMap> analyzeCommit(Git git, String commit, Runtime rt, String buildCommand,
                                             String gitRepo, String subProject, String staticAnalysis,
                                             FindBugsExtractor ex) throws Exception {

        System.out.println("checking out commit");
        git.checkout().setName(commit).call();

        Process pr = rt.exec(buildCommand, null, new File(String.format("%s/", gitRepo)));
        printOutput(pr);
        int exitCode = pr.waitFor();
        System.out.println("now performing static analysis");
        System.out.println(ex.xmlFile);

        Process pr2 = rt.exec(staticAnalysis, null, new File(String.format("%s/%s/", gitRepo, subProject)));
        printOutput(pr2);

        exitCode = pr2.waitFor();
        return ex.execute(new File(ex.xmlFile));
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
