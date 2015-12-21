package com.jroll.util;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;

/**
 * Created by jroll on 12/10/15.
 */
public class CustomFileUtil {

    /* example
    public static void main(String[] args) {
        ArrayList<File> files = findFileSub("/Users/jroll/dev/thesis/qpid-java", "target", "findBugsXml.xml");
        System.out.println(files);
    }
    */

    public static String trimSlash(String f) {
        return f.toString().substring(0, f.toString().lastIndexOf("/"));
    }

    /* simple list subdirs */
    public static ArrayList<File> findFilesInSub(String dirName, String folder, String fileName) {
        File[] directories = new File(dirName).listFiles(File::isDirectory);
        ArrayList<File> matchFiles = new ArrayList<File>();
        for (File file : directories) {
            File[] subDirectory = new File(file.toString()).listFiles(File::isDirectory);
            for (File sub : subDirectory) {
                //System.out.println(sub);
                File searchFile = null;
                if (sub.toString().contains(folder) &&  (searchFile = dirContains(sub, fileName)) != null) {
                    matchFiles.add(searchFile);
                }
            }
        }
        return matchFiles;
    }

    /* simple list subdirs */
    public static File findFileInSub(File sub, String folder, String fileName) {
        ArrayList<File> directories = new ArrayList<File>(Arrays.asList(sub.listFiles(File::isDirectory)));
        File target = directories.stream().filter(f -> f.isDirectory() && f.toString().contains(folder)).findFirst().orElse(null);
        File xml = null;
        if (target != null) {
            ArrayList<File> targetFiles = new ArrayList<File>(Arrays.asList(target.listFiles()));
            xml = targetFiles.stream().filter(f -> f.toString().toLowerCase().contains(fileName.toLowerCase())).findFirst().orElse(null);
        }

        return xml;
    }

    public static void findAllFilesWithExtension(String extension, File[] files, Set<String> dirMatches) {
        for (File file : files) {
            if (file.isDirectory()) {
                findAllFilesWithExtension(extension, file.listFiles(), dirMatches); // Calls same method again.
            } else if (file.getName().toLowerCase().contains(extension)) {
                dirMatches.add(file.getAbsolutePath());
            }
        }
    }
    /* Now for each subdir, see if it contains a folder named target */
    public static File dirContains(File dir, String name) {
        File[] subDirectory = new File(dir.toString()).listFiles();
        for (File f : subDirectory) {
            if (f.getName().toLowerCase().contains(name.toLowerCase()))
                return f;
        }
        return null;
    }

}
