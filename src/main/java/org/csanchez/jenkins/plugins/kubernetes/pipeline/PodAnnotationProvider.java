package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import hudson.EnvVars;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Run;
import org.csanchez.jenkins.plugins.kubernetes.PodAnnotation;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Collections;

/**
 * @author jasper
 */
public class PodAnnotationProvider implements ExtensionPoint {

    @Nonnull
    public Collection<PodAnnotation> buildFor(Run<?, ?> run) {
        return Collections.emptyList();
    }

    @Nonnull
    public Collection<PodAnnotation> buildFor(EnvVars environment) {
        return Collections.emptyList();
    }

    /**
     * All the registered {@link PodAnnotationProvider}s.
     */
    public static ExtensionList<PodAnnotationProvider> all() {
        return ExtensionList.lookup(PodAnnotationProvider.class);
    }

}
