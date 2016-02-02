package com.jroll.data;

import com.jroll.data.GitMetadata;
import sun.security.krb5.internal.Ticket;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeMap;

/**
 * Created by jroll on 11/4/15.
 *
 * A  ticket should be associated with one or more git commits
 *
 */
public class Requirement implements Serializable {
    private String id;
    private Ticket ticket;
    private ArrayList<GitMetadata> gitMetadatas;
    private ArrayList<String> currentFilesInRepo;
    private TreeMap<String, String> jiraFields;
    private LocalDateTime createDate;
    private LocalDateTime lastCommitDate;

    public Requirement () {

    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public TreeMap<String, String> getJiraFields() {
        return jiraFields;
    }

    public void setJiraFields(TreeMap<String, String> jiraFields) {
        this.jiraFields = jiraFields;
    }

    public ArrayList<GitMetadata> getGitMetadatas() {
        return gitMetadatas;
    }

    public void setGitMetadatas(ArrayList<GitMetadata> gitMetadatas) {
        this.gitMetadatas = gitMetadatas;
    }

    public ArrayList<String> getCurrentFilesInRepo() {
        return currentFilesInRepo;
    }

    public void setCurrentFilesInRepo(ArrayList<String> currentFilesInRepo) {
        this.currentFilesInRepo = currentFilesInRepo;
    }

    public LocalDateTime getCreateDate() {
        return createDate;
    }

    public void setCreateDate(LocalDateTime createDate) {
        this.createDate = createDate;
    }

    public LocalDateTime getLastCommitDate() {
        return lastCommitDate;
    }

    public void setLastCommitDate(LocalDateTime lastCommitDate) {
        this.lastCommitDate = lastCommitDate;
    }
}
