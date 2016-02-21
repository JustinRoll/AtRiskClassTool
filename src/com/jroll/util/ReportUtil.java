package com.jroll.util;

import com.jroll.data.ClassData;
import com.jroll.extractors.GitExtractor;
import com.jroll.extractors.JiraExtractor;
import gr.spinellis.ckjm.ClassMetrics;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import javax.xml.soap.Text;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.*;

import static com.jroll.driver.MainDriver.readClasses;

/**
 * Created by jroll on 1/10/16.
 */
public class ReportUtil {

    /* Read all classes from the serialized file.
    Report on how many classes contain valid values.
 */
    public static void testSerializedFile(FinalConfig config) throws FileNotFoundException {
        TreeMap<String, ClassData> classes = readClasses(config);
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
                pw.write(String.format("\tSonar:%s %s\n", sonar.getKey(), sonar.getValue()));
            }


        }
        pw.flush();
        pw.close();
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
}
