package com.jroll.util;

import com.jroll.data.ClassData;
import com.jroll.data.GitMetadata;
import com.jroll.data.Requirement;
import gr.spinellis.ckjm.ClassMetrics;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.XMLConfiguration;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Repository;

import java.io.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.stream.Collectors;

import static com.jroll.util.CustomFileUtil.trimCustom;

/**
 * Created by jroll on 12/31/15.
 */
public class Serializer {

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

        writer.write("Ticket|Last 10 Touched|Fix Version|Issue Type|Last Commit Time|Req Created|Commits|File|Requirement|Changed?\n");

        int reqCount = 0;
        int fileCount = 0;
        HashMap<String, Integer> fileMap = new HashMap<String, Integer>();
        HashMap<String, Integer> reqMap = new HashMap<String, Integer>();
        HashMap<String, ArrayBlockingQueue<String>> lastTicketsQueueMap = new HashMap<String, ArrayBlockingQueue<String>>();
        Set<String> versions = new HashSet<String>();
        ArrayBlockingQueue<String> lastTickets = null;

        for (Requirement req : reqs.stream().filter(r -> !"0.25".equals(r.getJiraFields().get("Fix Version/s").trim()))
                .collect(Collectors.toList())) {

            boolean bug = "Bug".equals(req.getJiraFields().get("Issue Type"));
            String reqText = req.getJiraFields().get("Description").replaceAll("\n", " ");
            LocalDateTime lastCommit = getLastCommitTime(req.getGitMetadatas());

            int rid = reqCount;
            //if (reqCount++ > 500)
            //    break;

            versions.add(req.getJiraFields().get("Fix Version/s"));

            for (String file : addChangedFiles(req.getGitMetadatas())) {
                int fid = fileCount;

                lastTickets = lastTicketsQueueMap.get(file);
                if (lastTickets == null) {
                    lastTickets = new ArrayBlockingQueue<String>(10);
                    lastTicketsQueueMap.put(file, lastTickets);
                }

                String last10 = String.join(",", Arrays.asList(lastTickets.toArray(new String[10])).stream().filter(f -> f != null)
                        .collect(Collectors.toList()));

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

                serializeReq(req, bug ? bugWriter : writer, lastCommit.toString(), last10, fid, rid, 1);
                lastTickets.offer(req.getId());
                if (lastTickets.size() >= 10)
                    lastTickets.poll();
            }


            for (String file: filterFiles(req, map)) {

                int fid = fileCount;
                lastTickets = lastTicketsQueueMap.get(file);
                if (lastTickets == null) {
                    lastTickets = new ArrayBlockingQueue<String>(10);
                    lastTicketsQueueMap.put(file, lastTickets);
                }

                String last10 = String.join(",", Arrays.asList(lastTickets.toArray(new String[10])).stream().filter(f -> f != null)
                        .collect(Collectors.toList()));

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



        }

        //System.out.println(versions);
        reqWriter.close();
        fileWriter.close();
        writer.close();
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


    private static String stringifyCommits(ArrayList<GitMetadata> metas) {
        String s = "";
        for (GitMetadata meta : metas) {
            s += meta.getCommitId();
            s += " ";
        }
        return s.trim();
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
        TreeMap<String, TreeMap<String, Double>> scores = CustomFileUtil.readVsm(sims);

        BufferedReader reader = null;
        TreeMap<String, String> commitMap = new TreeMap<String, String>();
        HashMap<String, Integer> headerMap = new HashMap<String, Integer>();
        TreeMap<String, String> fileMap = CustomFileUtil.readFile("fileMap.txt", "\\|\\|");
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


}
