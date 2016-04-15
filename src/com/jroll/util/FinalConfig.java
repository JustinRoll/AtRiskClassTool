package com.jroll.util;

import org.apache.commons.configuration.XMLConfiguration;

/**
 * Created by jroll on 1/2/16.
 */
public class FinalConfig {

    public String buildCommand;

    public String bugFile;

    public String staticAnalysisCommand;

    public String clocCommand;

    public String clocFile;

    public String codeSimilarity;

    public String outReqFile;

    public String fileMap;

    public String reqMap;

    public String fixFile;

    public String finalOutTable;

    public String finalOutArff;

    public String findBugsRelative;

    public String fixDataDirectory;

    public String gitRepo;

    public String jiraFile;

    public String jiraDirectory;

    public String jiraUrl;

    public String language = "java";

    public Integer reqLimit;

    public String reqSimilarity;

    public String similarityFile;

    public String staticsFile;

    public String subProject;

    public String sonarHost;

    public String[] sonarProject;

    public String[] sonarMetrics;

    public String sonarCommand;
    public String firstFix;

    public String noExt;


    public FinalConfig(XMLConfiguration config) {
        this.bugFile = config.getString("bug_file");
        this.buildCommand = config.getString("build_command");
        this.staticAnalysisCommand = config.getString("static_analysis_command");
        this.clocCommand = config.getString("cloc_command");
        this.clocFile = config.getString("cloc_file");
        this.outReqFile = config.getString("out_req_file");
        this.reqMap = config.getString("req_map");
        this.fileMap = config.getString("file_map");
        this.firstFix = config.getString("first_fix_file");
        this.fixDataDirectory = config.getString("fix_data_dir");
        this.fixFile = config.getString("fix_file");
        this.finalOutTable = config.getString("final_out_table");
        this.finalOutArff = config.getString("final_out_arff");
        this.findBugsRelative = config.getString("find_bugs_relative");
        this.gitRepo = config.getString("git_repo");
        this.jiraFile = config.getString("jira_file");
        this.jiraDirectory = config.getString("jira_directory");
        this.jiraUrl = config.getString("jira_url");
        this.reqSimilarity = config.getString("req_similarity");
        this.codeSimilarity = config.getString("code_similarity");
        this.reqLimit = config.getInt("req_limit");
        this.staticsFile = config.getString("statics_file");
        this.subProject = config.getString("subproject");
        this.sonarHost = config.getString("sonar_host");
        this.sonarProject = config.getStringArray("sonar_project");
        this.sonarMetrics = config.getStringArray("sonar_metrics");
        this.sonarCommand = config.getString("sonar_command");

        this.noExt = config.getString("no_ext");
    }
    /*
            <build_command>
            /Users/jroll/Downloads/apache-maven-3.3.3/bin/mvn clean install -DskipTests
        </build_command>
        <static_analysis_command>
            /Users/jroll/Downloads/apache-maven-3.3.3/bin/mvn findbugs:findbugs
        </static_analysis_command>
        <cloc_command>
            /Users/jroll/Downloads/cloc-1.64/cloc ~/dev/thesis/qpid-java/ --by-file --csv --out=/Users/jroll/IdeaProjects/Thesis/src/cloc.csv
        </cloc_command>
        <cloc_file>
            /Users/jroll/IdeaProjects/Thesis/src/cloc.csv
        </cloc_file>
        <req_file>
            out_only_req.txt
        </req_file>
        <file_map>
            file_map.txt
        </file_map>
        <fix_file>
            fix.txt
        </fix_file>
        <final_out_table>
            big_table.txt
        </final_out_table>
    <final_out_arff>
        big_table.arff
    </final_out_arff>
    <find_bugs_relative>
        /target/findbugsXml.xml
    </find_bugs_relative>
    <git_repo>
        /Users/jroll/dev/thesis/qpid-java
    </git_repo>
    <jira_file>
        /Users/jroll/IdeaProjects/Thesis/src/master_sheet_excel.xls
    </jira_file>
    <req_limit>
        0
    </req_limit>
    <similarity_file>
        ReqSimilarity_12.csv
    </similarity_file>
    <statics_file>
        statics.txt
    </statics_file>
     */
}
