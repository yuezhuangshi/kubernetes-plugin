package org.csanchez.jenkins.plugins.kubernetes.pipeline;

/**
 * Created by iocanel on 7/29/16.
 */
public class Constants {

    public static final String EXIT = "exit";
    public static final String NEWLINE = "\n"; // seems to work even on Windows
    public static final char CTRL_C = '\u0003';
    public static final String SPACE = " ";
    public static final String JOB_FULL_NAME_ANNOTATION = "jobFullName";
}
