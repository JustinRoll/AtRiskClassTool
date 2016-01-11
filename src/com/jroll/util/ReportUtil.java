package com.jroll.util;

import com.jroll.extractors.GitExtractor;
import com.jroll.extractors.JiraExtractor;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import javax.xml.soap.Text;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;

/**
 * Created by jroll on 1/10/16.
 */
public class ReportUtil {

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
