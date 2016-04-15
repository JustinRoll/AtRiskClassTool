package com.jroll.util;

import com.jroll.data.ClassData;
import com.jroll.data.GitMetadata;
import com.jroll.data.Requirement;
import gr.spinellis.ckjm.ClassMetrics;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.XMLConfiguration;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Repository;

import java.io.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.stream.Collectors;

import static com.jroll.driver.MainDriver.getStaticCount;
import static com.jroll.extractors.GitExtractor.addChangedFiles;
import static com.jroll.extractors.GitExtractor.filterFiles;
import static com.jroll.extractors.GitExtractor.getLastCommitTime;
import static com.jroll.util.CustomFileUtil.getHeader;
import static com.jroll.util.CustomFileUtil.readStatics;
import static com.jroll.util.CustomFileUtil.trimCustom;

/**
 * Created by jroll on 12/31/15.
 */
public class Serializer {

    FinalConfig config;

    public Serializer(FinalConfig config) {
        this.config = config;
    }

    public static void serializeReq(Requirement req, PrintWriter writer, String lastCommit, String last10, int fid, int rid, boolean firstReq, int changed) {
        //System.out.printf("%s||%s||%s||%s||%d", req.getId(), req.getGitMetadatas().get(0).getCommitId(), req.getJiraFields().get("Description"), file, 1);
        writer.write(String.format("%s|%s|%s|%s|%s|%s|%s|%d|%d|%d|%d\n", req.getId(), last10, req.getJiraFields().get("Fix Version/s"), req.getJiraFields().get("Issue Type"), lastCommit.toString(), req.getJiraFields().get("Created"), stringifyCommits(req.getGitMetadatas()),
                fid, rid, firstReq?1:0, changed));
    }

    /* Open the file. Check all columns where the firstReq column is set to 1.
        Create a new file with those at the beginning.
        Put all other tickets at the end
     */
    public void splitFile(FinalConfig config, String splitName) throws IOException {
        File inputBigTable = new File(config.finalOutTable);
        String FIRST_REQS = "temp_first_reqs.txt";
        String LAST_REQS = "temp_last_reqs.txt";
        PrintWriter firstReqs = new PrintWriter(FIRST_REQS);
        PrintWriter lastReqs = new PrintWriter(LAST_REQS);
        int firstCount = 0;
        int total = 0;
        final String FIRST_REQ = "First Req?";

        BufferedReader reader = null;
        TreeMap<String, String> commitMap = new TreeMap<String, String>();

        reader = new BufferedReader(new FileReader(inputBigTable));
        String text = null;
        String headerLine = reader.readLine();
        HashMap<String, Integer> header = CustomFileUtil.getHeader(headerLine);

        while ((text = reader.readLine()) != null) {
            String[] lineArray = text.split("\t");
            if (lineArray[header.get(FIRST_REQ)].equals("1")) {
                firstReqs.write(text + "\n");
                firstCount++;
            }
            else {
                lastReqs.write(text + "\n");
                total++;
            }
        }
        firstReqs.flush();
        firstReqs.close();
        lastReqs.flush();
        lastReqs.close();
        PrintWriter finalFile = new PrintWriter(String.format("%s_%f.txt", splitName, ((100.0 * firstCount)/total)));

        File firstReqsRead = new File(FIRST_REQS);
        File lastReqsRead = new File(LAST_REQS);

        BufferedReader firstReader = new BufferedReader(new FileReader(firstReqsRead));
        BufferedReader lastReader = new BufferedReader(new FileReader(lastReqsRead));

        finalFile.write(headerLine + "\n");
        while ((text = firstReader.readLine()) != null) {
            finalFile.write(text + "\n");
        }
        while ((text = lastReader.readLine()) != null) {
            finalFile.write(text + "\n");
        }
        finalFile.flush();
        finalFile.close();

    }

    public void serializeReqs(ArrayList<Requirement> reqs, HashMap<String, LocalDateTime> map, String extension) throws FileNotFoundException, UnsupportedEncodingException {
        PrintWriter reqWriter = new PrintWriter(config.reqMap, "UTF-8");
        PrintWriter fileWriter = new PrintWriter(config.fileMap, "UTF-8");
        PrintWriter writer = new PrintWriter(config.outReqFile, "UTF-8");
        PrintWriter bugWriter = new PrintWriter(config.bugFile, "UTF-8");
        PrintWriter noExt = new PrintWriter(config.noExt, "UTF-8");

        String header = "Ticket|Last 10 Touched|Fix Version|Issue Type|Last Commit Time|Req Created|Commits|File|Requirement|First Req?|Changed?";
        writer.write(header + "\n");

        int reqCount = 0;
        int fileCount = 0;
        HashMap<String, Integer> fileMap = new HashMap<String, Integer>();
        HashMap<String, Integer> reqMap = new HashMap<String, Integer>();
        HashMap<String, ArrayBlockingQueue<String>> lastTicketsQueueMap = new HashMap<String, ArrayBlockingQueue<String>>();
        Set<String> versions = new HashSet<String>();
        ArrayBlockingQueue<String> lastTickets = null;
        List<Requirement> newReqs = reqs.stream().filter(r ->  !reqs.get(0).getJiraFields().get("Fix Version/s").equals(r.getJiraFields().get("Fix Version/s").trim()))
                .collect(Collectors.toList());

        System.out.printf("Reqs vs new Reqs: %d %d\n", reqs.size(), newReqs.size());

        for (Requirement req : reqs) {
            Boolean firstReq = false;
            boolean bug = "Bug".equals(req.getJiraFields().get("Issue Type"));
            String reqText = req.getJiraFields().get("Description").replaceAll("\n", " ");
            LocalDateTime lastCommit = getLastCommitTime(req.getGitMetadatas());
            Set<String> changedFiles = addChangedFiles(req.getGitMetadatas(), extension);

            int rid = reqCount;

            if (!versions.contains(req.getJiraFields().get("Fix Version/s"))) {
                versions.add(req.getJiraFields().get("Fix Version/s"));
                firstReq = true;
            }

            if (changedFiles.size() == 0)
                noExt.write(req.getId() + "\n");

            for (String file : changedFiles) {
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

                serializeReq(req, bug ? bugWriter : writer, lastCommit.toString(), last10, fid, rid, firstReq, 1);
                lastTickets.offer(req.getId());
                if (lastTickets.size() >= 10)
                    lastTickets.poll();
            }


            for (String file: filterFiles(req, map, extension)) {

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
                serializeReq(req, bug?bugWriter:writer, lastCommit.toString(), last10, fid, rid, firstReq, 0);
            }
        }

        reqWriter.close();
        fileWriter.close();
        writer.close();
    }

    public  void serializeStatic(TreeMap<String, ClassData> treeMapList) throws FileNotFoundException {
        PrintWriter writer = new PrintWriter(config.staticsFile);
        for (Map.Entry<String, ClassData> entry : treeMapList.entrySet()) {
            for (ArrayList<TreeMap> treeMaps : entry.getValue().getStaticMetrics()) {
                for (TreeMap row : treeMaps) {
                    writer.write(String.format("%s, %s,%s,%s,%s\n", entry.getKey(), row.get("category"), row.get("classname"), row.get("classpath"), row.get("rank")));
                }
            }
        }
    }


    public static String stringifyCommits(ArrayList<GitMetadata> metas) {
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
    public void serializeFixVersions(TreeMap<String, String> fixVersions, String firstFix) throws IOException, ConfigurationException, GitAPIException, InterruptedException {
        PrintWriter fixWriter = new PrintWriter(config.fixFile, "UTF-8");
        PrintWriter firstFixWriter = new PrintWriter(config.firstFix, "UTF-8");
        /* initialize gitrepo. check out commit. copy over the fix directory to
        a directory name of .24, .26
         */

        firstFixWriter.write(firstFix);
        System.out.println(config.fixDataDirectory);
        //String cpFix = "cp -r " + config.gitRepo + " " + config.fixDataDirectory;

        //String delNonJava = "find ~/Downloads/tika_data/ -type f ! -name '*.java' -print0 | xargs -0 rm -vf"
        //String delNonJava = String.format("find %s -type f ! -name '*.java' -print0 | xargs -0 rm -vf", config.fixDataDirectory);
        Repository localRepo = new FileRepository(config.gitRepo + "/.git");
        Runtime rt = Runtime.getRuntime();

        for (Map.Entry<String, String> fixVersion : fixVersions.entrySet()) {
            Git git = new Git(localRepo);
            git.reset().setMode( ResetCommand.ResetType.HARD ).call();
            Thread.sleep(1000);
            git.checkout().setName(fixVersion.getValue()).call();
             //pr = rt.exec(delNonJava, null, new File(String.format("%s/", config.gitRepo)));
            //System.out.println(delNonJava);

            fixWriter.write(fixVersion.getKey() + "," + fixVersion.getValue() + "\n");
        }
        fixWriter.close();
    }

    public void copyFixVersions(TreeMap<String, String> fixVersions, String extension) throws IOException, ConfigurationException, GitAPIException, InterruptedException {


        Repository localRepo = new FileRepository(config.gitRepo + "/.git");
        Runtime rt = Runtime.getRuntime();

        for (Map.Entry<String, String> fixVersion : fixVersions.entrySet()) {
            PrintWriter pw = new PrintWriter("../../rsync_temp");
            String cpFix = "-avm --include='*" + extension + "' -f 'hide,! */' " + config.gitRepo + " " + config.fixDataDirectory;
            System.out.println(fixVersion.getValue());
            pw.write(cpFix + "/" + fixVersion.getKey());
            pw.close();
            Git git = new Git(localRepo);
            git.reset().setMode( ResetCommand.ResetType.HARD ).call();
            Thread.sleep(1000);
            git.checkout().setName(fixVersion.getValue()).call();
            CustomFileUtil.copyAllExtension(config.gitRepo, config.fixDataDirectory + "/" + fixVersion.getKey(), extension);

        }

    }

    public void runBigTable(TreeMap<String, ClassData> fixToClassData) throws Exception {

        TreeMap<String, TreeMap<String, Integer>> staticMap = readStatics(config.staticsFile);
        TreeMap<String, Integer> frequencyMap = new TreeMap<String, Integer>();

        //This field will map a class to its frequency of change
        float ticketCount = 0.0f;
        String prevTicket = null;
        PrintWriter writer = new PrintWriter(config.finalOutTable);


        File file = new File(config.reqMap);
        File sims = new File(config.similarityFile);
        TreeMap<String, TreeMap<String, Double>> scores = CustomFileUtil.readVsm(sims);

        BufferedReader reader = null;
        TreeMap<String, String> commitMap = new TreeMap<String, String>();
        HashMap<String, Integer> headerMap = new HashMap<String, Integer>();
        TreeMap<String, String> fileMap = CustomFileUtil.readFile(config.fileMap, "\\|\\|");
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


    public static String buildLine(ArrayList<Object> values) {
        String[] converted = new String[values.size()];
        int count = 0;
        String finalLine;

        for (Object val : values) {
            String strVal;
            if (val instanceof Integer) {

                strVal = String.format("%d", (Integer) val);
            }
            else if (val instanceof Double || val instanceof Float) {
                Double dVal = (Double) val;
                dVal = dVal.isNaN() ? 0 : dVal;
                strVal = String.format("%.5f", (Double) dVal);
            }
            else if (val instanceof String) {
                strVal = (String) val;
                strVal = strVal.replaceAll(",", "");
                strVal = strVal.equals("") ? "?" : strVal;
                strVal = strVal.equals("NaN") ? "0" : strVal;
            }
            else {
                Boolean b = (Boolean) val;
                if (b == null) {
                    strVal = "?";
                }
                else {
                    strVal = b ? "true" : "false";
                }
            }
            converted[count++] = strVal;
        }
        return String.join("\t", converted) + "\n";
    }

    public void runBigTableNew(TreeMap<String, ClassData> fixToClassData, String language, boolean excludeMissing) throws IOException {
        String currentReqLine = null;
        String currentCodeLine = null;

        //TreeMap<String, TreeMap<String, Integer>> staticMap = readStatics(config.staticsFile);
        TreeMap<String, Integer> frequencyMap = new TreeMap<String, Integer>();
        TreeMap<String, List<AbstractMap.SimpleEntry<Integer,LocalDateTime>>> frequencyByDate = new TreeMap<String, List<AbstractMap.SimpleEntry<Integer,LocalDateTime>>>();

        //This field will map a class to its frequency of change
        Integer ticketCount = 0;
        String prevTicket = null;
        PrintWriter writer = new PrintWriter(config.finalOutTable);
        System.out.println("\nlanguage " + language + "\n");
        File file = new File(config.outReqFile);
        File reqSims = new File(config.reqSimilarity);
        File codeSims = new File(config.codeSimilarity);

        BufferedReader reader = null;
        BufferedReader reqSimReader = new BufferedReader(new FileReader(reqSims));
        BufferedReader codeSimReader = new BufferedReader(new FileReader(codeSims));

        //Map of header item to array index
        HashMap<String, Integer> headerMap = new HashMap<String, Integer>();
        TreeMap<String, String> fileMap = CustomFileUtil.readFile(config.fileMap, "\\|\\|");
        TreeMap<String, String> trimmedFileMap = new TreeMap<String, String>();

        for (Map.Entry<String, String> entry : fileMap.entrySet()) {
            trimmedFileMap.put(entry.getKey(), trimCustom(entry.getValue(), "org/apache"));
        }
        System.out.println(" file map \n" + fileMap.toString());
        System.out.println(" trimmed file map" + trimmedFileMap.toString());
        System.out.println(fixToClassData);
        reader = new BufferedReader(new FileReader(file));
        String headerLine = reader.readLine();
        String[] mainHeader = headerLine.split("\\|");
        String[] vsmHeader = reqSimReader.readLine().split("\\|");
        String[] codeSimHeader = codeSimReader.readLine().split("\\|");
        String[] sonarHeader = config.sonarMetrics;
        String[] historyHeader = {"simple", "logarithmic", "linear"};



        ArrayList<Object> headerList = new ArrayList<Object>();

        for (int i = 0; i < mainHeader.length; i++) {
            headerMap.put(mainHeader[i], i);
        }
        String text = null;

        String[] ckjmHeader = {"ckjm_noc","ckjm_wmc","ckjm_rfc","ckjm_cbo","ckjm_dit","ckjm_lcom","ckjm_ca","ckjm_npm"};
        HashSet<String> missing = new HashSet<String>();
        HashSet<String> added = new HashSet<String>();
        //Load initial line
        //Load code history
        //Load req history
        //Load History Scores
        //Load SonarQube Metrics
        //Load CKJM
        //Write file
        for (int i = 0; i < mainHeader.length - 1; i++) {
            headerList.add(mainHeader[i]);
        }
        for (String s : codeSimHeader) {
            headerList.add("code_sim_" + s);
        }
        for (String s : vsmHeader) {
            headerList.add("req_sim_" + s);
        }
        for (String hist : historyHeader) {
            headerList.add(hist);
        }
        for (String qube : sonarHeader) {
            headerList.add(qube);
        }

        if (language.equals("java")) {
            for (String ckjm : ckjmHeader) {
                headerList.add(ckjm);
            }
        }
        headerList.add(mainHeader[mainHeader.length - 1]);
        System.out.println(headerList);

        String finishLine = buildLine(headerList);
        writer.write(finishLine);



        int count = 0;
        while ((text = reader.readLine()) != null && (currentCodeLine = codeSimReader.readLine()) != null && (currentReqLine = reqSimReader.readLine()) != null) {
            ArrayList<Object> values = new ArrayList<Object>();
            //System.out.println(text);
            count++;
            //System.out.println(text);

            String[] line = text.replaceAll("\t", " ").split("\\|");
            String fix = line[headerMap.get("Fix Version")];
            if (!language.equals("java") && (fix == null || fix.equals("Fix Version/s")))
                fix = line[0];
            String changed  = line[headerMap.get("Changed?")].trim();

            ClassData currentFix = fixToClassData.get(fix);

            if (currentFix == null) {
                System.out.println(String.format("fix: %s", fix));
                missing.add(fix);
                continue;
            }
            else {
                //System.out.println("fix found!");
                added.add(fix);
            }


            if (!line[0].equals(prevTicket)) {
                ticketCount++;
            }
            if (ticketCount % 10 == 0)
                System.out.printf("%d tickets done and %d requirements\n", ticketCount, count);

            prevTicket = line[0];

            String className = trimmedFileMap.get(line[7]);
            //System.out.println(className);
            //System.out.println(frequencyMap);
            Integer freq = frequencyMap.get(className) == null ? 0 : frequencyMap.get(className);

            //System.out.println("got here");

            if (frequencyByDate.get(className) == null)
                frequencyByDate.put(className, new ArrayList<AbstractMap.SimpleEntry<Integer, LocalDateTime>>());

            double baseHistory = Math.min(1.0, freq / (1.0 * ticketCount));
            double logHistory = HistoryCalculator.getLogHistory(ticketCount, frequencyByDate.get(className));
            double wtHistory = HistoryCalculator.getWeightedHistory(ticketCount, frequencyByDate.get(className));

            if (changed.equals("1")) {
                frequencyMap.put(className, freq + 1);

                List<AbstractMap.SimpleEntry<Integer, LocalDateTime>> dateFreqs = frequencyByDate.get(className);
                LocalDateTime lastCommitTime = TextParser.parseDateStringAlt(line[headerMap.get("Last Commit Time")]);
                dateFreqs.add(new AbstractMap.SimpleEntry<Integer, LocalDateTime>(ticketCount, lastCommitTime));
                //System.out.println(line[0] + " " + line[7] + "freq increase");
            }


            HashMap<String, Double> sonars = currentFix.getSonarMetrics().get(className);
            if (sonars == null || sonars.size() < 1) {
                sonars = new HashMap<String, Double>();
            }
            //System.out.println("Here is the class name");
            //System.out.println(className);
            String indexedClassName = className.replaceAll(".java", "").replaceAll("/", ".");
            indexedClassName = indexedClassName.contains("src.org.apache")?indexedClassName.substring(indexedClassName.indexOf("src.org.apache")):indexedClassName;

            ClassMetrics cm = currentFix.getCkjmMetrics().get(indexedClassName);
            if (cm == null) {
                //System.out.println("got here new class metrics");
                cm = new ClassMetrics();
            }

            //System.out.println("indexed class name: " + indexedClassName);
            String[] codeSim = currentCodeLine.split("\\|");
            //Load CodeSimilarity Scores
            String endVal = "yes";

            for (int i = 0; i < line.length; i++) {
                if (i == 1) {
                    line[i].replaceAll(",", " ");
                    values.add("\"" + line[i] + "\"");
                }
                else if (i == line.length - 1) {
                    endVal = line[line.length - 1];
                    endVal = endVal.equals("1")?"yes":"no";
                }
                else {
                    values.add(line[i]);
                }
            }

            for (String sim : codeSim) {
                values.add(sim);
            }
            String[] reqSim = currentReqLine.split("\\|");
            for (String req : reqSim) {
                values.add(req);
            }
            //Load initial line
            //load code sim
            //load req sim
            //Load History Scores
            //Load SonarQube Metrics
            //Load CKJM
            //Load initial line
            //Write file


                Double[] scores = {baseHistory, logHistory, wtHistory};

                for (double score : scores)
                    values.add(score);

                //for (double qube : currentFix.getSonarMetrics().get(className).values())
                //    values.add(qube);
                for (String s : sonarHeader) {
                    Double val = sonars.get(s);
                    values.add(val);
                }
                if (language.equals("java")) {
                    Integer[] ckjmValues = {cm.getNoc(),
                            cm.getWmc(), cm.getRfc(), cm.getCbo(), cm.getDit(), cm.getLcom(), cm.getCa(), cm.getNpm()};
                    for (Integer ckjmVal : ckjmValues)
                        values.add(ckjmVal);
                }

                values.add(endVal);
                finishLine = buildLine(values);
                if (!excludeMissing || (excludeMissing && !finishLine.contains("?")))
                    writer.write(finishLine);


        }
        System.out.println("got here");
        System.out.println(missing);
        System.out.println(missing.size());
        System.out.println("Added");
        System.out.println(added);
        System.out.println(added.size());
        writer.flush();
        writer.close();
    }

    /* for this method, we will just open up each similarity file and tack on the columns to our output file */
    public void runBigTableDumb(TreeMap<String, ClassData> fixToClassData) throws Exception {
        String currentReqLine = null;
        String currentCodeLine = null;
        //TreeMap<String, TreeMap<String, Integer>> staticMap = readStatics(config.staticsFile);
        TreeMap<String, Integer> frequencyMap = new TreeMap<String, Integer>();
        TreeMap<String, List<AbstractMap.SimpleEntry<Integer,LocalDateTime>>> frequencyByDate = new TreeMap<String, List<AbstractMap.SimpleEntry<Integer,LocalDateTime>>>();

        //This field will map a class to its frequency of change
        Integer ticketCount = 0;
        String prevTicket = null;
        PrintWriter writer = new PrintWriter(config.finalOutTable);


        File file = new File(config.outReqFile);
        File reqSims = new File(config.reqSimilarity);
        File codeSims = new File(config.codeSimilarity);

        BufferedReader reader = null;
        BufferedReader reqSimReader = new BufferedReader(new FileReader(reqSims));
        BufferedReader codeSimReader = new BufferedReader(new FileReader(codeSims));

        TreeMap<String, String> commitMap = new TreeMap<String, String>();
        HashMap<String, Integer> headerMap = new HashMap<String, Integer>();
        TreeMap<String, String> fileMap = CustomFileUtil.readFile(config.fileMap, "\\|\\|");
        TreeMap<String, String> trimmedFileMap = new TreeMap<String, String>();

        for (Map.Entry<String, String> entry : fileMap.entrySet()) {
            trimmedFileMap.put(entry.getKey(), trimCustom(entry.getValue(), "org/apache"));
            //change this to use a filter
        }
        System.out.println(fileMap.toString() + " file map");
        System.out.println(trimmedFileMap.toString() + " trimmed file map");
        reader = new BufferedReader(new FileReader(file));
        String[] mainHeader = reader.readLine().split("|");
        String[] vsmHeader = reqSimReader.readLine().split(",");
        String[] codeSimHeader = codeSimReader.readLine().split(",");
        String[] sonarHeader = config.sonarMetrics;

        for (int i = 0; i < mainHeader.length; i++) {
            headerMap.put(mainHeader[i], i);
        }
        String text = null;

        String ckjmHeader = "ckjm_noc\tckjm_wmc\tckjm_rfc\tckjm_cbo\tckjm_dit\tckjm_lcom\tckjm_ca\tckjm_npm";

        String HEADER = "Ticket\tLast 10\tFix Version\tIssue Type\tLast Commit Time\tReq Created\tCommits\tFile\tRequirement\tBugCount\tChangeHistory\t";
        HEADER += String.join("\t", vsmHeader);
        HEADER += "\t" + String.join("\t", codeSimHeader);
        HEADER += "\t" + ckjmHeader;
        //HEADER += "\tloc\t"; just got rid of this
        HEADER += String.join("\t", sonarHeader) + "\t";
        HEADER += "Changed?\n";

        HashMap<String, Integer> header = getHeader(HEADER.replace("\t", ","));
        writer.write(HEADER);

        int count = 0;
        while ((text = reader.readLine()) != null && (currentCodeLine = codeSimReader.readLine()) != null && (currentReqLine = reqSimReader.readLine()) != null) {
            count++;
            String[] line = text.replaceAll("\t", " ").split("\\|");
            String fix = line[headerMap.get("Fix Version")];
            String changed  = line[headerMap.get("Changed?")].trim();

            ClassData currentFix = fixToClassData.get(fix);


            if (!line[0].equals(prevTicket)) {
                ticketCount++;
            }
            prevTicket = line[0];

            String className = trimmedFileMap.get(line[7]);
            Integer freq = frequencyMap.get(className) == null ? 0 : frequencyMap.get(className);
            if (changed.equals("1")) {
                frequencyMap.put(className, freq + 1);
                if (frequencyByDate.get(className) == null)
                    frequencyByDate.put(className, new ArrayList<AbstractMap.SimpleEntry<Integer, LocalDateTime>>());
                List<AbstractMap.SimpleEntry<Integer, LocalDateTime>> dateFreqs = frequencyByDate.get(className);
                LocalDateTime lastCommitTime = TextParser.parseDateString(line[headerMap.get("Last Commit Time")]);
                dateFreqs.add(new AbstractMap.SimpleEntry<Integer, LocalDateTime>(ticketCount, lastCommitTime));
                //System.out.println(line[0] + " " + line[7] + "freq increase");
            }
            int staticCount = 0; //fix this
            double baseHistory = Math.min(1.0, freq / ticketCount);
            double logHistory = HistoryCalculator.getLogHistory(ticketCount, frequencyByDate.get(className));
            double wtHistory = HistoryCalculator.getWeightedHistory(ticketCount, frequencyByDate.get(className));

            String last10 = line[1].replaceAll(",", " ");
            String firstFields = String.format("%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%.5f\t", line[0], last10, line[2].replaceAll(",", "|"), line[3], line[4], line[5], line[6], line[7], line[8], staticCount, baseHistory);


            ClassMetrics cm = currentFix.getCkjmMetrics().get(className.replaceAll(".java", "").replaceAll("/", "."));
            Integer loc = currentFix.getLinesOfCode().get(className);
            //System.out.println(className);
            String ckjmMetrics = "";
            if (cm != null) {
                ckjmMetrics += String.format("%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d", cm.getNoc(),
                        cm.getWmc(), cm.getRfc(), cm.getCbo(), cm.getDit(), cm.getLcom(), cm.getCa(), cm.getNpm());

            }
            else {
                //"ckjm_noc\tckjm_wmc\tckjm_rfc\tckjm_cbo\tckjm_dit\tckjm_lcom\tckjm_ca\tckjm_npm";
                ckjmMetrics += "-1\t-1\t-1\t-1\t-1\t-1\t-1\t-1";
            }
            ckjmMetrics += String.format("\t%d", loc != null ? loc : -1);
            String[] reqs = currentReqLine.split(",");
            for (int i = 0; i < reqs.length; i++) {
                firstFields += String.format("%s\t", reqs[i]);
            }
            for (String codeSim : currentCodeLine.split(",")) {
                firstFields += String.format("%s\t", codeSim);
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


    public void convertArff(String file) throws Exception {
        ARFFGenerator.convertFile(file == null ? config.finalOutTable : file, config.finalOutArff, config.subProject);
    }

    public void serializeReqsUnorganized(ArrayList<Requirement> reqs, HashMap<String, LocalDateTime> map, String extension) throws FileNotFoundException, UnsupportedEncodingException {
        PrintWriter reqWriter = new PrintWriter(config.reqMap, "UTF-8");
        PrintWriter fileWriter = new PrintWriter(config.fileMap, "UTF-8");
        PrintWriter writer = new PrintWriter(config.outReqFile, "UTF-8");
        PrintWriter bugWriter = new PrintWriter(config.bugFile, "UTF-8");

        String header = "Ticket|Last 10 Touched|Fix Version|Issue Type|Last Commit Time|Req Created|Commits|File|Requirement|First Req?|Changed?";
        writer.write(header + "\n");

        int reqCount = 0;
        int fileCount = 0;
        HashMap<String, Integer> fileMap = new HashMap<String, Integer>();
        HashMap<String, Integer> reqMap = new HashMap<String, Integer>();
        HashMap<String, ArrayBlockingQueue<String>> lastTicketsQueueMap = new HashMap<String, ArrayBlockingQueue<String>>();
        Set<String> versions = new HashSet<String>();
        ArrayBlockingQueue<String> lastTickets = null;
        Collections.reverse(reqs);
        System.out.println("Checking requirements");
        for (Requirement req: reqs) {
            System.out.printf("%s: %s\n", req.getId(), req.getJiraFields().get("Description").replaceAll("\n", " ") );
        }
        System.out.println("Done");

        System.out.println(reqs.size());
        for (Requirement req : reqs) {
            Boolean firstReq = false;
            boolean bug = "Bug".equals(req.getJiraFields().get("Issue Type"));
            String reqText = req.getJiraFields().get("Description").replaceAll("\n", " ");
            LocalDateTime lastCommit = getLastCommitTime(req.getGitMetadatas());

            int rid = reqCount;
            Set<String> files = addChangedFiles(req.getGitMetadatas(), extension);
            System.out.printf("this many files: %d\n", files.size());
            if (files.size() == 0)
                System.out.println("SIZE ISSUE");

            String combinedText = reqText + req.getId();
            if (reqMap.get(combinedText) != null) {
                rid = reqMap.get(combinedText);
            }
            else {
                reqMap.put(combinedText, reqCount);
                System.out.println(reqCount + " going up");
                reqWriter.write(String.format("%d||%s\n", reqCount, reqText ));
                reqCount++;
            }

            for (String file : addChangedFiles(req.getGitMetadatas(), extension)) {
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


                serializeReq(req, bug ? bugWriter : writer, lastCommit.toString(), last10, fid, rid, firstReq, 1);
                lastTickets.offer(req.getId());
                if (lastTickets.size() >= 10)
                    lastTickets.poll();
            }


            for (String file: filterFiles(req, map, extension)) {

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
                serializeReq(req, bug?bugWriter:writer, lastCommit.toString(), last10, fid, rid, firstReq, 0);
            }
        }

        reqWriter.close();
        fileWriter.close();
        writer.close();
    }


    public ArrayList<Object> tweakVals(Boolean header, String text, int firstReqIndex) {
        String[] fields = text.split("\t");
        ArrayList<Object> vals = new ArrayList<Object>();
        vals = new ArrayList<Object>();
        String firstReq = null;
        String changed = fields[fields.length - 1];


        for (int i = 0; i < fields.length; i++) {
            if (i == firstReqIndex) {
                firstReq = fields[firstReqIndex];
            }
            else if (i != fields.length - 1){
                vals.add(fields[i]);
            }
        }
        vals.add(firstReq);
        if (header)
            vals.add(changed);
        else
            vals.add(changed.equals("yes") ? 1 : 0);
        return vals;
    }
    /*  last_req, a, b, c, d, no
        a, b, c, d, last_req, yes

        Open the file again. Put the First Req? column second to last.
        Then replace the "yes" in the last column with a 1, no with a 0
     */
    public void runTweaks(FinalConfig config) throws IOException {
        String FINAL_STRING = config.finalOutTable.substring(0, config.finalOutTable.indexOf(".")) + "_final.txt";
        PrintWriter fileWriter = new PrintWriter(FINAL_STRING, "UTF-8");
        File f = new File(config.finalOutTable);
        BufferedReader reader = new BufferedReader(new FileReader(f));
        String text = reader.readLine();
        String[] headerText = text.split("\n");
        HashMap<String, Integer> header = getHeader(text);
        String finalLine = null;

        int firstReqIndex = header.get("First Req?");


        writeLine(fileWriter, tweakVals(true, text, firstReqIndex));

        while ((text = reader.readLine()) != null) {


            header.put("First Req?", header.keySet().size() - 2);


            writeLine(fileWriter, tweakVals(false, text, firstReqIndex));

        }
        fileWriter.flush();
        fileWriter.close();

    }

    private void writeLine(PrintWriter fileWriter, ArrayList<Object> objects) {
        fileWriter.write(buildLine(objects));
    }

    public void deleteNoTouch() throws IOException {
        File readFile = new File(config.finalOutTable);
        BufferedReader reader = new BufferedReader(new FileReader(readFile));
        File writeFile = new File("temp_file_table");
        BufferedWriter writer = new BufferedWriter(new FileWriter(writeFile));
        HashSet<String> noTouchTickets = new HashSet<String>();
        HashSet<String> totalTickets = new HashSet<String>();
        String text = reader.readLine(); //skip header
        int touched = 0;
        String prevTicket = null;

        while ((text = reader.readLine()) != null) {
            String[] lineArr = text.split("\t");
            String ticket = lineArr[0].trim();

            totalTickets.add(ticket);

            if (prevTicket != null && !ticket.equals(prevTicket)) {
                if (touched == 0) {
                    //System.out.println(prevTicket);
                    noTouchTickets.add(prevTicket);
                }
                touched = 0;

            }

            if ("yes".equals(lineArr[lineArr.length - 1].trim())) {
                touched++;
                //System.out.println("got yes");
            }
            prevTicket = ticket;

        }

        reader.close();
        reader = new BufferedReader(new FileReader(readFile));
        while ((text = reader.readLine()) != null) {
            String[] lineArr = text.split("\t");
            String ticket = lineArr[0].trim();
            if (!noTouchTickets.contains(ticket))
                writer.write(text + "\n");
        }
        writer.close();
        writeFile.renameTo(readFile);
    }
}
