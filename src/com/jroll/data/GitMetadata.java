package com.jroll.data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.TreeMap;

/**
 * Created by jroll on 11/4/15.
 */
public class GitMetadata implements Serializable {
    private String commitMessage;
    private String author;
    private String commitId;
    private LocalDateTime commitDate;
    private ArrayList<GitMetadata> previousCommits;
    private Requirement req;
    private ArrayList<TreeMap> staticMetrics;
    private ArrayList<String> changedFiles;
    private ArrayList<String> allFiles;
    private ArrayList<String> packages;
    private TreeMap<String, Integer> filesToLinesOfCode;

    public String toString() {
        return getCommitMessage() + " " + getAuthor() + " ";
    }

    public String getCommitMessage() {
        return commitMessage;
    }

    public void setCommitMessage(String commitMessage) {
        this.commitMessage = commitMessage;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getCommitId() {
        return commitId;
    }

    public void setCommitId(String commitId) {
        this.commitId = commitId;
    }

    public LocalDateTime getCommitDate() {
        return commitDate;
    }

    public void setCommitDate(LocalDateTime commitDate) {
        this.commitDate = commitDate;
    }

    public ArrayList<GitMetadata> getPreviousCommits() {
        return previousCommits;
    }

    public void setPreviousCommits(ArrayList<GitMetadata> previousCommits) {
        this.previousCommits = previousCommits;
    }

    public Requirement getReq() {
        return req;
    }

    public void setReq(Requirement req) {
        this.req = req;
    }

    public ArrayList<TreeMap> getStaticMetrics() {
        return staticMetrics;
    }

    public void setStaticMetrics(ArrayList<TreeMap> staticMetrics) {
        this.staticMetrics = staticMetrics;
    }

    public ArrayList<String> getChangedFiles() {
        return changedFiles;
    }

    public void setChangedFiles(ArrayList<String> changedFiles) {
        this.changedFiles = changedFiles;
    }

    public ArrayList<String> getPackages() {
        return packages;
    }

    public void setPackages(ArrayList<String> packages) {
        this.packages = packages;
    }

    public TreeMap<String, Integer> getFilesToLinesOfCode() {
        return filesToLinesOfCode;
    }

    public void setFilesToLinesOfCode(TreeMap<String, Integer> filesToLinesOfCode) {
        this.filesToLinesOfCode = filesToLinesOfCode;
    }

    public ArrayList<String> getAllFiles() {
        return allFiles;
    }

    public void setAllFiles(ArrayList<String> allFiles) {
        this.allFiles = allFiles;
    }
}
