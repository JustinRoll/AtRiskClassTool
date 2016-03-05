package com.jroll.extractors;

import com.jroll.data.GitMetadata;
import com.jroll.data.Requirement;
import org.codehaus.plexus.util.StringUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.DepthWalk;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.jroll.util.CustomFileUtil.getExtension;

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
     *            if unspecified, root folder is assumed.
     * @param commit
     *            if null, HEAD is assumed.
     * @return list of files in specified path
     */
    public static ArrayList<String> getAllFiles(Repository repository,
                                                 RevCommit commit, HashMap<String, LocalDateTime> map) throws Exception {
        ArrayList<String> list = new ArrayList<String>();

        final TreeWalk tw = new TreeWalk(repository);
        try {
            tw.addTree(commit.getTree());

                tw.setRecursive(true);
                while (tw.next()) {
                    String fName = getPathModel(tw, null, commit);
                    list.add(fName);
                    /* if (map.get(fName) == null) {
                        map.put(fName, getCreationTime(fName, repository));
                    } */
                    LocalDateTime mappedTime = map.get(fName);
                    LocalDateTime commitTime = getCommitTime(commit);
                    if (mappedTime  == null || mappedTime.isAfter(commitTime)) {
                        map.put(fName, commitTime);
                    }
                }

        } catch (IOException e) {
            throw new Exception("IO issue");
        } finally {

        }
        Collections.sort(list);
        return list;
    }

    public static LocalDateTime getCommitTime(RevCommit commit) {
        return LocalDateTime.ofInstant(commit.getAuthorIdent().getWhen().toInstant(), ZoneId.systemDefault());
    }

    public static LocalDateTime getFirstCommitTime(Repository repo) throws IOException {
        RevWalk rw = new RevWalk(repo);
        RevCommit c = null;
        AnyObjectId headId;
        headId = repo.resolve(Constants.HEAD);
        RevCommit headCommit = rw.parseCommit(headId);

        rw.sort(RevSort.REVERSE);
        rw.markStart(headCommit);
        RevCommit oldest = rw.next();

        return LocalDateTime.ofInstant(oldest.getAuthorIdent().getWhen().toInstant(), ZoneId.systemDefault());
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
                System.out.println("full tree walk");
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
            name = tw.getPathString().intern();

        } else {
            name = tw.getPathString().substring(basePath.length() + 1).intern();
        }
        /*ObjectId objectId = tw.getObjectId(0);
        try {
            if (!tw.isSubtree() && (tw.getFileMode(0) != FileMode.GITLINK)) {
                size = tw.getObjectReader().getObjectSize(objectId, Constants.OBJ_BLOB);

            }
        } catch (Throwable t) {
            throw new Exception("failed to retrieve blob size for " + tw.getPathString());
        } */
        return name;
    }

    /* Get creation date of the file. Pretty slow */
    public static LocalDateTime getCreationTime(String fileName, Repository repository) throws IOException {
        RevWalk revWalk = new RevWalk( repository );
        revWalk.markStart(revWalk.parseCommit(repository.resolve(Constants.HEAD)));
        revWalk.setTreeFilter(PathFilter.create(fileName));
        System.out.println(fileName);
        revWalk.sort( RevSort.COMMIT_TIME_DESC );
        revWalk.sort(RevSort.REVERSE, true);
        RevCommit commit = revWalk.next();
        revWalk.dispose();
        return commit == null ? null : LocalDateTime.ofInstant(commit.getAuthorIdent().getWhen().toInstant(), ZoneId.systemDefault());
    }


    public Iterable<RevCommit> getAllCommits() throws IOException, GitAPIException {
        return git.log()
                .all()
                .call();
        }

    public static Set<String> addChangedFiles(ArrayList<GitMetadata> commits, String extension) {
        ArrayList<String> fileList = new ArrayList<String>();

        for (GitMetadata commit : commits) {

            fileList.addAll(commit.getChangedFiles().stream().filter(f -> extension.equals(getExtension(f))).collect(Collectors.toList()));
        }
        return new HashSet<String>(fileList);
    }

    public static LocalDateTime getLastCommitTime(ArrayList<GitMetadata> metas) {
        LocalDateTime lastCommit = null;

        for (GitMetadata meta : metas) {
            if (lastCommit == null || meta.getCommitDate().isAfter(lastCommit)) {
                lastCommit = meta.getCommitDate();
            }
        }
        return lastCommit;
    }


    /* Filter out all files that were created AFTER the requirement time */
    public static Set<String> filterFiles(Requirement req, HashMap<String, LocalDateTime> map, String extension) {
        Set<String> allFiles = new HashSet<String>();
        Set<String> changedFiles = new HashSet<String>();

        for (GitMetadata meta : req.getGitMetadatas()) {
            //System.out.println(meta.getChangedFiles());
            //System.out.println(meta.getAllFiles());

            List<String> files = meta.getAllFiles().stream().filter(f -> extension.equals(getExtension(f).toLowerCase()) &&
                    req.getCreateDate().isAfter(map.get(f))).collect(Collectors.toList());
            allFiles.addAll(files);
            changedFiles.addAll(meta.getChangedFiles());
        }

        System.out.println(allFiles.size());
        return new HashSet<String>(allFiles.stream().filter(f -> !changedFiles.contains(f)).collect(Collectors.toList()));
    }

    }

