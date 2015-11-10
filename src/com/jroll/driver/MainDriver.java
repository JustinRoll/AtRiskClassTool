package com.jroll.driver;

import com.jroll.exception.FindBugsException;
import com.jroll.extractors.FindBugsExtractor;
import com.jroll.extractors.GitExtractor;
import com.jroll.extractors.JiraExtractor;
import com.jroll.util.GitMetadata;
import com.jroll.util.Requirement;
import org.apache.commons.configuration.XMLConfiguration;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Created by jroll on 9/28/15.
 */
public class MainDriver {

    public static void main(String[] args) throws Exception {
        /* Gather all Jira Data first */
        XMLConfiguration config = new XMLConfiguration("config.xml");
        List<HashMap<String, String>> jiraRows = JiraExtractor.parseExcel(config.getString("jira_file"));
        HashMap<String, GitMetadata>  gitCommits = gatherCommits();
        ArrayList<Requirement> reqs = new ArrayList<Requirement>();

        System.out.println(gitCommits.keySet().size());
        for (HashMap<String, String> row : jiraRows) {
            String ticketId = row.get("Key").replaceAll("QPID-", "");
            //System.out.println(ticketId);
            if (gitCommits.get(ticketId) != null) {//Look up each mapped commit {

                Requirement req = new Requirement();
                req.setJiraFields(row);
                req.setId(row.get("Key"));
                ArrayList<GitMetadata> metas = new ArrayList<>();

                metas.add(gitCommits.get(ticketId));
                req.setGitMetadatas(metas);
                reqs.add(req);
                //System.out.println("Found a match");
        }

            //Check to see if there is a ticket match
        }
        serializeReqs(reqs);
    }

    private static void serializeReqs(ArrayList<Requirement> reqs) throws FileNotFoundException, UnsupportedEncodingException {
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
            for (String file : req.getGitMetadatas().get(0).getChangedFiles()) {
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
                writer.write(String.format("%s||%s||%s||%d||%d||%d\n", req.getId(), req.getJiraFields().get("Created"), req.getGitMetadatas().get(0).getCommitId(),
                        fid, rid, 1));
            }


            for (String file: req.getGitMetadatas().get(0).getAllFiles().stream().filter
                    (the_f -> !req.getGitMetadatas().get(0).getChangedFiles().contains(the_f)).collect(Collectors.toList())) {

                int fid = fileCount;


                if (fileMap.get(file) != null) {
                    fid = fileMap.get(file);
                }
                else {
                    fileMap.put(file, fileCount);
                    fileWriter.write(String.format("%d||%s\n", fileCount, file));
                    fileCount++;
                }
                       writer.write(String.format("%s||%s||%s||%d||%d||%d\n", req.getId(), req.getJiraFields().get("Created"),
                               req.getGitMetadatas().get(0).getCommitId(),
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
    public static HashMap<String, GitMetadata> gatherCommits () throws Exception {
        XMLConfiguration config = new XMLConfiguration("config.xml");
        String gitRepo = config.getString("git_repo");
        String buildCommand = config.getString("build_command");
        String staticAnalysis = config.getString("static_analysis_command");
        String subProject = config.getString("subproject");
        Repository localRepo = new FileRepository(gitRepo + "/.git");
        Runtime rt = Runtime.getRuntime();
        HashMap<String, GitMetadata> gitMetas = new HashMap<String, GitMetadata>();

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
            meta.setAllFiles(extractor.getAllFiles(localRepo, commit));
            //System.out.println(meta.getAllFiles());

            if (getTicketId(meta.getCommitMessage().toLowerCase()) != null) {
                gitMetas.put(getTicketId(meta.getCommitMessage().toLowerCase()), meta);
            }


            //System.out.println(commit.getCommitTime());
            //System.out.println(commit.getAuthorIdent().getName());
            //System.out.println("checking out commit");
            //System.out.println(commit.getName());
            //System.out.printf("Parents:%d\n", commit.getParentCount());
           // System.out.println("Commit date: " + commit.getAuthorIdent().getWhen());
            //extractor.checkout(commit);

            /*
            Process pr = rt.exec(buildCommand, null, new File(String.format("%s/", gitRepo)));
            printOutput(pr);
            int exitCode = pr.waitFor();
            System.out.println("now performing static analysis");
            System.out.println(ex.xmlFile);

            Process pr2 = rt.exec(staticAnalysis, null, new File(String.format("%s/%s/", gitRepo, subProject)));
            printOutput(pr2);

            exitCode = pr2.waitFor();
            meta.setStaticMetrics(ex.execute(new File(ex.xmlFile)));
            */
        }
        return gitMetas;
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
            //System.out.println(s);
        }
    }


    public static String getRow(TreeMap<String, String> row, String commit) {
        return String.format("%s, %s,%s,%s,%s\n", commit, row.get("category"), row.get("classname"), row.get("classpath"), row.get("rank"));
    }
}
