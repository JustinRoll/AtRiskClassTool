package com.jroll.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeMap;

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

    public static TreeMap<String, String> readFile(String fileName, String delimiter) throws IOException {
        File file = new File(fileName);
        BufferedReader reader = null;
        TreeMap<String, String> commitMap = new TreeMap<String, String>();

        reader = new BufferedReader(new FileReader(file));
        String text = null;

        while ((text = reader.readLine()) != null) {

            String[] line = text.split(delimiter);

            if (line.length >= 1) {
                commitMap.put(line[0], line[1]);
            }
        }

        return commitMap;

    }

    public static String trimCustom(String inputPath, String replaceString) {
        int index = inputPath.indexOf(replaceString);
        return index >= 0 ? inputPath.substring(index) : inputPath;
    }

    public static TreeMap<String, TreeMap<String, Double>> readVsm(File file) throws IOException {
        BufferedReader reader = null;
        TreeMap<String, TreeMap<String, Double>> lines = new TreeMap<String, TreeMap<String, Double>>();

        reader = new BufferedReader(new FileReader(file));
        String text = null;
        String[] header = reader.readLine().split(",");

        while ((text = reader.readLine()) != null) {
            String[] line = text.split(",");
            if (lines.get(line[0]) == null) {
                TreeMap<String, Double> map = new TreeMap<String, Double>();
                lines.put(line[1], map);
                for (int i = 2; i < line.length; i++) {
                    map.put(header[i], Double.parseDouble(line[i]));
                }
            }
        }
        return lines;
    }

    public static String getExtension(String fileName) {
        return fileName.substring(fileName.lastIndexOf(".") + 1, fileName.length());
    }

    /* read statics, run cloc metrics, read cloc metrics, read/run dependency info */
    public static TreeMap<String, Integer> readClocFile(String fileName) throws IOException {
        File file = new File(fileName);
        BufferedReader reader = null;
        TreeMap<String, Integer> commitMap = new TreeMap<String, Integer>();

        reader = new BufferedReader(new FileReader(file));
        String text = null;
        text = reader.readLine();

        while ((text = reader.readLine()) != null) {
            String[] line = text.split(",");
            if (line.length >= 5 && line[0].toLowerCase().equals("java") && line[1].contains("org/apache/qpid")) {
                commitMap.put(line[1].substring(line[1].indexOf("org/apache/qpid")), Integer.parseInt(line[4]));
            }
            else {
                System.out.println("bad class at " + line[1]);
            }
        }

        return commitMap;

    }


    public static TreeMap<String, TreeMap<String, Integer>> readStatics(String fileName) throws IOException {
        File file = new File(fileName);
        BufferedReader reader = null;

        TreeMap<String, TreeMap<String, Integer>> classStatics = new TreeMap<String, TreeMap<String, Integer>>();

        /*
           Map of fix version to map of classes with count of stuff
         */

        reader = new BufferedReader(new FileReader(file));
        String text = null;

        while ((text = reader.readLine()) != null) {
            String[] line = text.split(",");
            System.out.println(line);
            if (line.length >= 4 && !line[1].contains("SECURITY")) {
                if (classStatics.get(line[0]) == null) {
                    classStatics.put(line[0], new TreeMap<String, Integer>());
                }
                TreeMap<String, Integer> bugMap = classStatics.get(line[0]);
                if (bugMap.get(line[3]) == null) {
                    bugMap.put(line[3], 1);
                }
                else {
                    bugMap.put(line[3], bugMap.get(line[3]) + 1);
                }
            }
        }
        System.out.println(classStatics);
        return classStatics;

    }

}
