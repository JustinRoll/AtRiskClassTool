package com.jroll.driver;

import com.jroll.exception.FindBugsException;
import com.jroll.extractors.FindBugsExtractor;
import com.jroll.extractors.GitExtractor;
import com.jroll.extractors.JiraExtractor;
import com.jroll.util.CommitData;
import com.jroll.util.GitMetadata;
import com.jroll.util.Requirement;
import org.apache.commons.configuration.XMLConfiguration;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Created by jroll on 9/28/15.
 */
public class MainDriver {

    public static void main(String[] args) throws Exception {
        XMLConfiguration config = new XMLConfiguration("config.xml");
        List<HashMap<String, String>> jiraRows = JiraExtractor.parseExcel(config.getString("jira_file"));
        System.out.println("Jira Data Done");
        /* Gather all commit data */
        CommitData data = gatherCommits();
        System.out.println("Commit Data Done");
        ArrayList<Requirement> reqs = new ArrayList<Requirement>();

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
                //System.out.println("Found a match");
        }
            System.out.println("Finished Mapping Ticket Data to Commits");

            //Check to see if there is a ticket match
        }
        serializeReqs(reqs, data.fileCommitDates);
        System.out.println("Serialization complete");
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

    /* Filter out all files that were created AFTER the requirement time */
    public static Set<String> filterFiles(Requirement req, HashMap<String, LocalDateTime> map) {
        List<String> allFiles = new ArrayList<String>();
        for (GitMetadata meta : req.getGitMetadatas()) {
            List<String> files = meta.getAllFiles().stream().filter(f -> !meta.getChangedFiles().contains(f) &&
            req.getCreateDate().isAfter(map.get(f))).collect(Collectors.toList());
            allFiles.addAll(files);
        }
        return new HashSet<String>(allFiles);
    }

    public static Set<String> addChangedFiles(ArrayList<GitMetadata> commits) {
        ArrayList<String> fileList = new ArrayList<String>();
        for (GitMetadata commit : commits) {
            fileList.addAll(commit.getChangedFiles());
        }
        return new HashSet<String>(fileList);
    }

    private static void serializeReqs(ArrayList<Requirement> reqs, HashMap<String, LocalDateTime> map) throws FileNotFoundException, UnsupportedEncodingException {
        PrintWriter reqWriter = new PrintWriter("reqMap.txt", "UTF-8");
        PrintWriter fileWriter = new PrintWriter("fileMap.txt", "UTF-8");
        PrintWriter writer = new PrintWriter("out.txt", "UTF-8");
        writer.write("Ticket||Created||Commits||Requirement||File||Changed?\n");

        int reqCount = 0;
        int fileCount = 0;
        HashMap<String, Integer> fileMap = new HashMap<String, Integer>();
        HashMap<String, Integer> reqMap = new HashMap<String, Integer>();
        for (Requirement req : reqs) {
            String reqText = req.getJiraFields().get("Description").replaceAll("\n", " ");

            int rid = reqCount;
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

                //System.out.printf("%s||%s||%s||%s||%d", req.getId(), req.getGitMetadatas().get(0).getCommitId(), req.getJiraFields().get("Description"), file, 1);
                writer.write(String.format("%s,%s,%s,%d,%d,%d\n", req.getId(), req.getJiraFields().get("Created"), stringifyCommits(req.getGitMetadatas()),
                        fid, rid, 1));
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
                       writer.write(String.format("%s,%s,%s,%d,%d,%d\n", req.getId(), req.getJiraFields().get("Created"),
                               stringifyCommits(req.getGitMetadatas()),
                               rid, fid, 0));
            }

        }
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
            meta.setCommitId(commit.getName());
            meta.setCommitDate(LocalDateTime.ofInstant(commit.getAuthorIdent().getWhen().toInstant(), ZoneId.systemDefault()));
            meta.setChangedFiles(extractor.getChangedFiles(localRepo, commit));
            meta.setAllFiles(extractor.getAllFiles(localRepo, commit, fileCommitDates));

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

    public static void performStaticAnalysis(GitExtractor extractor, RevCommit commit, Runtime rt, String buildCommand,
                                             String gitRepo, String subProject, String staticAnalysis, GitMetadata meta,
                                             FindBugsExtractor ex) throws Exception {
        System.out.println(commit.getCommitTime());
        System.out.println(commit.getAuthorIdent().getName());
        System.out.println("checking out commit");
        System.out.println(commit.getName());
        System.out.printf("Parents:%d\n", commit.getParentCount());
        System.out.println("Commit date: " + commit.getAuthorIdent().getWhen());
        extractor.checkout(commit);


        Process pr = rt.exec(buildCommand, null, new File(String.format("%s/", gitRepo)));
        printOutput(pr);
        int exitCode = pr.waitFor();
        System.out.println("now performing static analysis");
        System.out.println(ex.xmlFile);

        Process pr2 = rt.exec(staticAnalysis, null, new File(String.format("%s/%s/", gitRepo, subProject)));
        printOutput(pr2);

        exitCode = pr2.waitFor();
        meta.setStaticMetrics(ex.execute(new File(ex.xmlFile)));
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
