package com.jroll.driver;
import com.jroll.exception.FindBugsException;
import com.jroll.extractors.FindBugsExtractor;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.XMLConfiguration;


import java.io.File;

public class RunCodeExtractor {

    public static void main(String[] args) throws FindBugsException, ConfigurationException {
        FindBugsExtractor ex = new FindBugsExtractor();

        XMLConfiguration config = new XMLConfiguration("config.xml");
        ex.execute(new File("/Users/jroll/dev/thesis/qpid-java/client/target/findbugsXml.xml"));

    }
}
