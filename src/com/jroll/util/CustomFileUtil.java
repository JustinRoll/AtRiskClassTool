package com.jroll.util;

import com.jroll.data.ClassData;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeMap;
import java.nio.file.*;

import static com.jroll.driver.MainDriver.readClasses;
import static java.lang.Math.toIntExact;
import static java.nio.file.StandardCopyOption.*;
import java.nio.file.attribute.*;
import static java.nio.file.FileVisitResult.*;
import java.io.IOException;
import java.util.*;

/**
 * Created by jroll on 12/10/15.
 */
public class CustomFileUtil {



    /**
     * Copy source file to target location. If {@code prompt} is true then
     * prompt user to overwrite target if it exists. The {@code preserve}
     * parameter determines if file attributes should be copied/preserved.
     */
    static void copyFile(Path source, Path target) {
        CopyOption[] options =
                new CopyOption[] { COPY_ATTRIBUTES, REPLACE_EXISTING };

            try {
                Files.copy(source, target, options);
            } catch (IOException x) {
                System.err.format("Unable to copy: %s: %s%n", source, x);
            }

    }

    /*
    # Required metadata
    sonar.projectKey=java-sonar-runner-simple
    sonar.projectName=Simple Java project analyzed with the SonarQube Runner
    sonar.projectVersion=1.0

# Comma-separated paths to directories with sources (required)
sonar.sources=src

# Language
sonar.language=java

# Encoding of the source files
sonar.sourceEncoding=UTF-8
     */
    public static File generateSonarProperties(String dir, String projectKey, String projectVersion, String language) throws FileNotFoundException, UnsupportedEncodingException {
        String sonar = String.format("%s/sonar-project.properties", dir);
        PrintWriter writer = new PrintWriter(sonar, "UTF-8");

        String baseString = "sonar.projectKey=" + projectKey + "\n";

        baseString += "sonar.projectName=" + projectKey + "\n";
        baseString += "sonar.projectVersion=" + projectVersion + "\n";
        baseString+= "sonar.sources=.\n";
        baseString+= "sonar.language=" + language + "\n";
        baseString+= "sonar.sourceEncoding=UTF-8\n";

        writer.write(baseString);
        writer.close();

        return new File(sonar);
    }


    /**
     * A {@code FileVisitor} that copies a file-tree ("cp -r")
     */
    static class TreeCopier implements FileVisitor<Path> {
        private final Path source;
        private final Path target;

        private String extension;

        TreeCopier(Path source, Path target, String extension) {
            this.source = source;
            this.target = target;
            this.extension = extension;

        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
            // before visiting entries in a directory we copy the directory
            // (okay if directory already exists).
            CopyOption[] options = (
                    new CopyOption[] { COPY_ATTRIBUTES });

            Path newdir = target.resolve(source.relativize(dir));
            try {
                Files.copy(dir, newdir, options);
            } catch (FileAlreadyExistsException x) {
                // ignore
            } catch (IOException x) {
                System.err.format("Unable to create: %s: %s%n", newdir, x);
                return SKIP_SUBTREE;
            }
            return CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            if (file.toString().contains(extension)) {
                copyFile(file, target.resolve(source.relativize(file)));

            }
            return CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
            // fix up modification time of directory when done
            if (exc == null) {
                Path newdir = target.resolve(source.relativize(dir));
                try {
                    FileTime time = Files.getLastModifiedTime(dir);
                    Files.setLastModifiedTime(newdir, time);
                } catch (IOException x) {
                    System.err.format("Unable to copy all attributes to: %s: %s%n", newdir, x);
                }
            }
            return CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) {
            if (exc instanceof FileSystemLoopException) {
                System.err.println("cycle detected: " + file);
            } else {
                System.err.format("Unable to copy: %s: %s%n", file, exc);
            }
            return CONTINUE;
        }
    }

    public static void copyAllExtension(String fromDirectory, String toDirectory, String extension) throws IOException {
        Path source = Paths.get(fromDirectory);
        Path dest = Paths.get(toDirectory);


                // follow links when copying files
        EnumSet<FileVisitOption> opts = EnumSet.of(FileVisitOption.FOLLOW_LINKS);
        TreeCopier tc = new TreeCopier(source, dest, extension);
        Files.walkFileTree(source, opts, Integer.MAX_VALUE, tc);
    }

    public static Integer filesInPath(String path, String extension) throws IOException {
        Integer count = 0;

        Object[] paths = Files.walk(Paths.get(path)).toArray();
        for (Object filePath : paths) {
            Path realPath = (Path) filePath;
            if (realPath.toString().endsWith(extension)) {
                count++;
            }
            else {
                //System.out.println("Not a java file " + realPath.toString());
            }
        }
        return count;
    }
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

            if (line.length > 1) {
                commitMap.put(line[0], line[1]);
            }
        }

        return commitMap;

    }

    public static TreeMap<String, ArrayList<String>> readFileRev(String fileName, String delimiter) throws IOException {
        File file = new File(fileName);
        BufferedReader reader = null;
        TreeMap<String, ArrayList<String>> commitMap = new TreeMap<String, ArrayList<String>>();

        reader = new BufferedReader(new FileReader(file));
        String text = null;

        while ((text = reader.readLine()) != null) {

            String[] line = text.split(delimiter);

            if (line.length > 1) {
                String ticket = line[0].trim();
                String commit = line[1].trim();
                if (!commitMap.containsKey(commit)) {
                    commitMap.put(commit, new ArrayList<String>());
                }

                commitMap.get(commit).add(ticket);
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

    /* return a map from the header line */
    public static HashMap<String, Integer> getHeader(String line) {
        String[] lineArray = line.split("\t");
        HashMap<String, Integer> headerMap = new HashMap<String, Integer>();

        for (int i = 0; i < lineArray.length; i++) {
            headerMap.put(lineArray[i], i);
        }

        return headerMap;
    }

    /* read statics, run cloc metrics, read cloc metrics, read/run dependency info */
    public static TreeMap<String, Integer> readClocFile(String project, String fileName) throws IOException {
        File file = new File(fileName);
        BufferedReader reader = null;
        TreeMap<String, Integer> commitMap = new TreeMap<String, Integer>();
        String projectAbbrev = String.format("org/apache/%s", project);

        reader = new BufferedReader(new FileReader(file));
        String text = null;
        text = reader.readLine();

        while ((text = reader.readLine()) != null) {
            String[] line = text.split(",");
            if (line.length >= 5 && line[0].toLowerCase().equals("java") && line[1].contains(projectAbbrev)) {
                commitMap.put(line[1].substring(line[1].indexOf(projectAbbrev)), Integer.parseInt(line[4]));
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
