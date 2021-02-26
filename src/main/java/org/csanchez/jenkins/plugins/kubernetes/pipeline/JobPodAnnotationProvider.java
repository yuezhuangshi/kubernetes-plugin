package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import hudson.Extension;
import hudson.model.Run;
import org.csanchez.jenkins.plugins.kubernetes.PodAnnotation;

import java.util.Arrays;
import java.util.Collection;

/**
 * @author jasper
 */
@Extension
public class JobPodAnnotationProvider extends PodAnnotationProvider {

    @Override
    public Collection<PodAnnotation> buildFor(Run<?, ?> run) {
        return Arrays.asList(
            new PodAnnotation(Constants.JOB_FULL_NAME_ANNOTATION, run.getParent().getFullName())
        );
    }

}
