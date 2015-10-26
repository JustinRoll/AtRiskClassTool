package com.jroll.extractors;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.DepthWalk;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.IOException;
import java.util.TreeMap;

/**
 * Created by jroll on 9/27/15.
 */
public class GitExtractor extends Extractor {
    Repository repo;
    Git git;


    public GitExtractor(Repository repo) {
        this.repo = repo;
        this.git = new Git(this.repo);
    }

    public void checkout(RevCommit commit) throws Exception{
        git.checkout().setName(commit.getName()).call();
    }

    public TreeMap getFeatures(RevCommit commit) {
        TreeMap treeMap = null;

        return treeMap;
    }

    public Iterable<RevCommit> getAllCommits() throws IOException, GitAPIException {
        return git.log()
                .all()
                .call();
        /*count = 0;
        for (RevCommit rev : logs) {
            System.out.println("Commit: " + rev  + ", name: " + rev.getName() + ", id: " + rev.getId().getName() );
            count++; */

        }
    }

