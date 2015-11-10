package com.jroll.util;

import sun.security.krb5.internal.Ticket;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;

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
    private HashMap<String, String> jiraFields;

    public Requirement () {

    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public HashMap<String, String> getJiraFields() {
        return jiraFields;
    }

    public void setJiraFields(HashMap<String, String> jiraFields) {
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
}
