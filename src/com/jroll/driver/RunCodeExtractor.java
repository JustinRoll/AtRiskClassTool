package com.jroll.driver;
import com.jroll.exception.FindBugsException;
import com.jroll.findbugs_extractor.Extractor;
import mulan.data.MultiLabelInstances;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.XMLConfiguration;
import weka.core.Instances;
import weka.experiment.InstanceQuery;


import java.io.File;

public class RunCodeExtractor {

    public static void main(String[] args) throws FindBugsException, ConfigurationException {
        Extractor ex = new Extractor();

        XMLConfiguration config = new XMLConfiguration("config.xml");
        ex.execute(new File("/Users/jroll/dev/thesis/qpid-java/client/target/findbugsXml.xml"));

    }
}
