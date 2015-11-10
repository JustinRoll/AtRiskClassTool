package com.jroll.extractors;

import org.codehaus.plexus.util.StringUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.DepthWalk;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

    /**
     * Returns the list of files in the specified folder at the specified
     * commit. If the repository does not exist or is empty, an empty list is
     * returned.
     *
     * @param repository
     * @param path
     *            if unspecified, root folder is assumed.
     * @param commit
     *            if null, HEAD is assumed.
     * @return list of files in specified path
     */
    public static ArrayList<String> getAllFiles(Repository repository,
                                                 RevCommit commit) throws Exception {
        ArrayList<String> list = new ArrayList<String>();

        final TreeWalk tw = new TreeWalk(repository);
        try {
            tw.addTree(commit.getTree());

                tw.setRecursive(true);
                while (tw.next()) {
                    list.add(getPathModel(tw, null, commit));
                }

        } catch (IOException e) {
            throw new Exception("IO issue");
        } finally {

        }
        Collections.sort(list);
        return list;
    }



    /**
     * Returns the list of files changed in a specified commit. If the
     * repository does not exist or is empty, an empty list is returned.
     *
     * @param repository
     * @param commit
     *            if null, HEAD is assumed.
     *            if true, each PathChangeModel will have insertions/deletions
     * @return list of files changed in a commit
     */
    public static ArrayList<String> getChangedFiles(Repository repository, RevCommit commit) throws Exception {
        ArrayList<String> list = new ArrayList<String>();

        RevWalk rw = new RevWalk(repository);
        try {

            if (commit.getParentCount() == 0) {
                TreeWalk tw = new TreeWalk(repository);
                tw.reset();
                tw.setRecursive(true);
                tw.addTree(commit.getTree());
                while (tw.next()) {
                    list.add(tw.getPathString());
                }

            } else {
                RevCommit parent = rw.parseCommit(commit.getParent(0).getId());
                ObjectReader reader = repository.newObjectReader();
                CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
                //System.out.println(parent.getName());

                ObjectId oldTree = repository.resolve( String.format("%s^{tree}", parent.getName()) );
                oldTreeIter.reset( reader, oldTree );
                CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
                ObjectId newTree = repository.resolve(String.format("%s^{tree}", commit.getName()));
                newTreeIter.reset( reader, newTree );

                DiffFormatter diffFormatter = new DiffFormatter( DisabledOutputStream.INSTANCE );
                diffFormatter.setRepository( repository );
                List<DiffEntry> entries = diffFormatter.scan( oldTreeIter, newTreeIter );

                for( DiffEntry entry : entries ) {
                    //System.out.println( entry.getNewPath() );
                    list.add(entry.getNewPath());
                }


            }
        } catch (Exception e) {
            throw new Exception(e);

        } finally {
            rw.dispose();
        }
        return list;
    }

    /**
     * Returns a path model of the current file in the treewalk.
     *
     * @param tw
     * @param basePath
     * @param commit
     * @return a path model of the current file in the treewalk
     */
    public static String getPathModel(TreeWalk tw, String basePath, RevCommit commit) throws Exception {
        String name;
        long size = 0;
        if (StringUtils.isEmpty(basePath)) {
            name = tw.getPathString();
        } else {
            name = tw.getPathString().substring(basePath.length() + 1);
        }
        ObjectId objectId = tw.getObjectId(0);
        try {
            if (!tw.isSubtree() && (tw.getFileMode(0) != FileMode.GITLINK)) {
                size = tw.getObjectReader().getObjectSize(objectId, Constants.OBJ_BLOB);
            }
        } catch (Throwable t) {
            throw new Exception("failed to retrieve blob size for " + tw.getPathString());
        }
        return name;
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

