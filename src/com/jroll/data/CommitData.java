package com.jroll.data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by jroll on 11/11/15.
 */
public class CommitData {
    public HashMap<String, ArrayList<GitMetadata>> gitMetas;
    public HashMap<String, LocalDateTime> fileCommitDates;

    public CommitData(HashMap<String, ArrayList<GitMetadata>> gitMetas, HashMap<String, LocalDateTime> fileCommitDates) {
        this.gitMetas = gitMetas;
        this.fileCommitDates = fileCommitDates;
    }
}
