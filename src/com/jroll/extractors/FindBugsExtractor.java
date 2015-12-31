package com.jroll.extractors;

import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import com.jroll.exception.FindBugsException;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomBuilder;

/*
checkout commit
Run  mvn build script with clean
run findBugs
Output the static analysis stuff
 */

/**
 * @goal stats
 */
public class FindBugsExtractor extends Extractor {

    public String xmlFile;

public FindBugsExtractor(String dir, String findBugsRelative) {
    this.xmlFile = dir + findBugsRelative;
}

    /**
     * Where to read the findbugs stats from
     *
     * @parameter expression="${findbugsChecks}"
     *            default-value="${project.build.directory}/findbugsCheck.xml"
     */

    /**
     * Output the Findbus stats for the project to the console.
     */
    public ArrayList<TreeMap> execute(File findbugsChecks) throws FindBugsException {
        ArrayList<TreeMap> classProperties = new ArrayList<TreeMap>();

        if (findbugsChecks != null && findbugsChecks.exists()) {
            System.out.println("file found");
            try {
                Xpp3Dom dom = Xpp3DomBuilder.build(new FileReader(
                        findbugsChecks));

                // get the summary and output it
                Xpp3Dom summaryDom = dom.getChild("FindBugsSummary");

                // output any information needed
                System.out.println(
                        "Total bug count:"
                                + summaryDom.getAttribute("total_bugs"));

                Xpp3Dom[] packageDoms = summaryDom.getChildren("PackageStats");

                Xpp3Dom[] children = dom.getChildren("BugInstance");

                for (Xpp3Dom child : children) {
                    TreeMap<String, String> propertiesMap = new TreeMap<String, String>();
                    propertiesMap.put("category", child.getAttribute("category"));
                    propertiesMap.put("rank", child.getAttribute("rank"));
                    propertiesMap.put("classname", child.getChild("Class").getAttribute("classname"));
                    propertiesMap.put("classpath", child.getChild("Class").getChild("SourceLine").getAttribute("sourcepath"));
                    //System.out.println(propertiesMap);
                    classProperties.add(propertiesMap);
                    //<SourceLine start="57" classname="org.apache.qpid.client.AMQDestination" sourcepath="org/apache/qpid/client/AMQDestination.java" sourcefile="AMQDestination.java" end="1104">

                }

                System.out.println(packageDoms.length + " package(s)");
                for (int i = 0; i < packageDoms.length; i++) {
                    String info = new StringBuilder().append("package ")
                            .append(packageDoms[i].getAttribute("package"))
                            .append(": types:").append(
                                    packageDoms[i].getAttribute("total_types"))
                            .append(", bugs:").append(
                                    packageDoms[i].getAttribute("total_bugs"))
                            .toString();
                }

            } catch (Exception e) {
                //System.out.println(e.getMessage());
                throw new FindBugsException(
                        "Findbugs checks file missing", e);
            }
        }
        else {
            System.out.println("static file not exists");
        }
    return classProperties;
    }

    public String parseFindBugs() throws FindBugsException {
        List<TreeMap> rows = execute(new File(this.xmlFile));
        System.out.println("commit, category, classname, classpath, rank");

        String str = "";

        for (TreeMap row : rows) {
            str += getRow(row, "HEAD");
        }

        return str;
    }

    public static String getRow(TreeMap<String, String> row, String commit) {
        return String.format("%s, %s,%s,%s,%s\n", commit, row.get("category"), row.get("classname"), row.get("classpath"), row.get("rank"));
    }
}