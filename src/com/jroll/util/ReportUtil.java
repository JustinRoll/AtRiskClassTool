package com.jroll.util;

import com.jroll.data.ClassData;
import com.jroll.data.CommitData;
import com.jroll.data.GitMetadata;
import com.jroll.extractors.GitExtractor;
import com.jroll.extractors.JiraExtractor;
import gr.spinellis.ckjm.ClassMetrics;
import org.apache.commons.configuration.ConfigurationException;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import javax.xml.soap.Text;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

import static com.jroll.driver.MainDriver.readClasses;
import static com.jroll.driver.MainDriver.serializeStatic;
import static com.jroll.util.TextParser.getTicketId;

/**
 * Created by jroll on 1/10/16.
 */
public class ReportUtil {

    /* Read all classes from the serialized file.
    Report on how many classes contain valid values.
 */
    public static void testSerializedFile(FinalConfig config) throws FileNotFoundException {
        TreeMap<String, ClassData> classes = readClasses(config.staticsFile);
        int allMatchClass = 0;
        int nonMatching = 0;

        PrintWriter pw = new PrintWriter("static_report_temp.txt");

        pw.write(String.format("Number of fixes:%d\n", classes.entrySet().size()));
        for (Map.Entry<String, ClassData> cls : classes.entrySet()) {
            String currentFix = cls.getKey();
            ClassData cData = cls.getValue();
            pw.write(String.format("Fix Version:%s\n", currentFix));
            for (Map.Entry<String, ClassMetrics> ckjm : cData.getCkjmMetrics().entrySet()) {
                pw.write(String.format("\tCKJM:%s %s\n", ckjm.getKey(), ckjm.getValue()));
            }
            for (Map.Entry<String, HashMap<String, Double>> sonar : cData.getSonarMetrics().entrySet()) {
                pw.write(String.format("\tSonar:%s ,Value:%s\n", sonar.getKey(), sonar.getValue()));
            }


        }
        pw.flush();
        pw.close();
    }

    /* Read all the classes in the serialized file.
        Read all the fix names into the a Set
        check each file for the fix.
            if the fix is missing sonar data
                check the next one.
            if the fix has class data and sonarData, insert it to the final list.
     */
    public static void joinSerializedFiles(String[] files, FinalConfig config) throws IOException {

        int allMatchClass = 0;
        int nonMatching = 0;
        Set<String> fixes = new HashSet<String>();
        Set<String> missingFixes = new HashSet<String>();
        TreeMap<String, ClassData> finalMap = new TreeMap<String, ClassData>();
        TreeMap<String, String> offFixes = CustomFileUtil.readFile(config.fixFile, ",");

        ArrayList<TreeMap<String, ClassData>> list = new ArrayList<TreeMap<String, ClassData>>();
        for (String file : files) {
            list.add(readClasses(file));
            System.out.println(file);
        }

        for (TreeMap<String, ClassData> map : list) {
            for (String key : map.keySet())
                fixes.add(key);
        }

        for (String fix : fixes) {
            for (TreeMap<String, ClassData> map : list) {
                ClassData data = null;
                if ((data = map.get(fix)) != null && data.getSonarMetrics() != null && data.getSonarMetrics().size() > 0) {
                    finalMap.put(fix, data);
                    allMatchClass++;
                    continue;
                } else
                    nonMatching++;
            }
        }
        for (String fix : offFixes.keySet()) {
            if (!finalMap.keySet().contains(fix))
                missingFixes.add(fix);
        }
        System.out.println(fixes);
        System.out.println(fixes.size());
        System.out.println("Missing fixes");
        System.out.println(missingFixes);
        serializeStatic(config, finalMap);

    }

    /* Read all classes from the serialized file.
Report on how many classes contain valid values.
*/
    public static void cleanSerializedFile(FinalConfig config, String language) throws FileNotFoundException {
        TreeMap<String, ClassData> classes = readClasses(config.staticsFile);
        System.out.println("got here");
        System.out.println(language);

        for (Map.Entry<String, ClassData> cls : classes.entrySet()) {
            String currentFix = cls.getKey();
            ClassData cData = cls.getValue();


            TreeMap<String, HashMap<String, Double>> newSonar = new TreeMap<String, HashMap<String, Double>>();
            for (Map.Entry<String, HashMap<String, Double>> sonar : cData.getSonarMetrics().entrySet()) {
                if (language.toLowerCase().equals("java")) {
                    if (sonar.getKey().indexOf("src") != -1 && sonar.getKey().indexOf("org/apache") != -1) {
                        String newKey = sonar.getKey().substring(sonar.getKey().indexOf("src"));
                        newKey = sonar.getKey().substring(sonar.getKey().indexOf("org/apache"));
                        newSonar.put(newKey, sonar.getValue());
                    }
                } else {

                    String newKey = sonar.getKey().substring(sonar.getKey().indexOf(":") + 1);
                    System.out.println(newKey);
                    newSonar.put(newKey, sonar.getValue());
                }

            }
            cData.setSonarMetrics(newSonar);

        }
        serializeStatic(config, classes);

    }


    /* Get total list of tickets. Get total list of commits (hashmap).
        For each ticket, check if there is a linked commit
     */
    public static void tabulate_report(FinalConfig config) throws IOException, GitAPIException {
        Repository localRepo = new FileRepository(config.gitRepo + "/.git");
        GitExtractor gx = new GitExtractor(localRepo);

        ArrayList<TreeMap<String, String>> rows = JiraExtractor.parseCSVToMap(config.jiraFile);
        Set<String> totalTickets = new HashSet<String>();
        Set<String> linkedTickets = new HashSet<String>();


        for (TreeMap<String, String> row : rows) {
            if (row.get("Key") != null)
                totalTickets.add(TextParser.getTicketId(row.get("Key")));
        }

        for (RevCommit commit : gx.getAllCommits()) {
            String ticketId = TextParser.getTicketId(commit.getFullMessage());
            if (totalTickets.contains(ticketId)) {
                linkedTickets.add(ticketId);
            }
        }

        System.out.printf("Total Tickets: %d Linked Tickets: %d\n", totalTickets.size(), linkedTickets.size());
        System.out.printf("Linkedness:%.2f\n", 1.0 * linkedTickets.size() / totalTickets.size());
    }

    public static void reportFreqs(FinalConfig config) {
    }

    public static void reportReleases(FinalConfig config, String extension) throws IOException, GitAPIException, InterruptedException {
        //for each release, report the number of available java files in it.
        //open fix versions, check out each associated commit
        //count number of files
        TreeMap<String, String> fixVersions = CustomFileUtil.readFile(config.fixFile, ",");
        TreeMap<String, Integer> versionToFiles = new TreeMap<String, Integer>();

        Repository localRepo = new FileRepository(config.gitRepo + "/.git");
        Git git = new Git(localRepo);
        Runtime rt = Runtime.getRuntime();

        for (Map.Entry<String, String> fixVersion : fixVersions.entrySet()) {


            git.reset().setMode(ResetCommand.ResetType.HARD).call();
            Thread.sleep(1000);
            git.checkout().setName(fixVersion.getValue()).call();
            versionToFiles.put(fixVersion.getKey() + " " + fixVersion.getValue(), CustomFileUtil.filesInPath(config.gitRepo, extension));
            System.out.printf("%s,%s:,%d\n", config.gitRepo.substring(config.gitRepo.lastIndexOf("/") + 1), fixVersion.getKey() + " " + fixVersion.getValue(), CustomFileUtil.filesInPath(config.gitRepo, extension));
        }
    }

    public static void reportRequirements(FinalConfig config, String extension) throws Exception {
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
                meta.setChangedFiles(extractor.getChangedFiles(localRepo, commit).stream().filter(f -> f.endsWith(extension)).collect(Collectors.toCollection(ArrayList<String>::new)));
                meta.setAllFiles(extractor.getAllFiles(localRepo, commit, fileCommitDates).stream().filter(f -> f.endsWith(extension)).collect(Collectors.toCollection(ArrayList<String>::new)));

                String ticketId = getTicketId(meta.getCommitMessage().toLowerCase());

                System.out.printf("%s,%s,%d,%d,%s\n", ticketId, meta.getCommitId(), meta.getChangedFiles().size(), meta.getAllFiles().size(),
                        meta.getCommitMessage().replaceAll("\n", " "));

                if (ticketId != null) {
                    if (gitMetas.get(ticketId) == null) {
                        ArrayList<GitMetadata> metaList = new ArrayList<GitMetadata>();
                        gitMetas.put(ticketId, metaList);
                    }
                    gitMetas.get(ticketId).add(meta);

                }

            }

        }

}

