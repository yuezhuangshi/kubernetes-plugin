package org.csanchez.jenkins.plugins.kubernetes.volumes.workspace;

import com.google.common.collect.ImmutableMap;
import hudson.Extension;
import hudson.Util;
import hudson.model.Descriptor;
import hudson.util.ListBoxModel;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.csanchez.jenkins.plugins.kubernetes.PodAnnotation;
import org.csanchez.jenkins.plugins.kubernetes.pipeline.Constants;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.interceptor.RequirePOST;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.logging.Level.INFO;
import static org.csanchez.jenkins.plugins.kubernetes.PodTemplateUtils.substituteEnv;

/**
 * @author jasper
 */
public class DynamicJobPVCWorkspaceVolume extends WorkspaceVolume {
    private String storageClassName;
    private String requestsSize;
    private String accessModes;
    private String jobFullName;
    private static final Logger LOGGER = Logger.getLogger(DynamicJobPVCWorkspaceVolume.class.getName());

    @DataBoundConstructor
    public DynamicJobPVCWorkspaceVolume() {}

    public DynamicJobPVCWorkspaceVolume(String storageClassName,
                                        String requestsSize, String accessModes) {
        this.storageClassName = storageClassName;
        this.requestsSize = requestsSize;
        this.accessModes = accessModes;
    }

    @CheckForNull
    public String getAccessModes() {
        return accessModes;
    }

    @DataBoundSetter
    public void setAccessModes(@CheckForNull String accessModes) {
        this.accessModes = Util.fixEmpty(accessModes);
    }

    @CheckForNull
    public String getRequestsSize() {
        return requestsSize;
    }

    @DataBoundSetter
    public void setRequestsSize(@CheckForNull String requestsSize) {
        this.requestsSize = Util.fixEmpty(requestsSize);
    }

    @CheckForNull
    public String getStorageClassName() {
        return storageClassName;
    }

    @DataBoundSetter
    public void setStorageClassName(@CheckForNull String storageClassName) {
        this.storageClassName = Util.fixEmpty(storageClassName);
    }

    @Override
    public Volume buildVolume(String volumeName, String podName) {
        return new VolumeBuilder()
                .withName(volumeName)
                .withNewPersistentVolumeClaim()
                .withClaimName(normalizedJobFullName(jobFullName))
                .withReadOnly(false)
                .and()
                .build();
    }

    @Override
    public void processAnnotations(@Nonnull Collection<PodAnnotation> annotations) {
        jobFullName = annotations.stream()
            .filter(a -> Constants.JOB_FULL_NAME_ANNOTATION.equals(a.getKey()))
            .findFirst()
            .orElseThrow(() ->
                new IllegalArgumentException("job full name not found, please use DynamicJobPVCWorkspaceVolume in pipeline")
            )
            .getValue();
    }

    @Override
    public PersistentVolumeClaim createVolume(KubernetesClient client, ObjectMeta podMetaData){
        String namespace = podMetaData.getNamespace();
        String podName = podMetaData.getName();
        String pvcName = normalizedJobFullName(jobFullName);
        LOGGER.log(Level.FINE, "Adding workspace volume {0} from pod: {1}/{2}", new Object[] { pvcName, namespace, podName });

        // try to use exist pvc, or else create one
        List<PersistentVolumeClaim> persistentVolumeClaims = client.persistentVolumeClaims().list().getItems();
        PersistentVolumeClaim pvc = persistentVolumeClaims.stream().filter(p ->
            Objects.equals(p.getMetadata().getName(), pvcName)
        ).findFirst().orElse(null);

        if (null == pvc) {
            pvc = new PersistentVolumeClaimBuilder()
                .withNewMetadata()
                .withName(pvcName)
                .withAnnotations(ImmutableMap.of("jenkins/job-full-name", jobFullName))
                .endMetadata()
                .withNewSpec()
                .withAccessModes(getAccessModesOrDefault())
                .withNewResources()
                .withRequests(getResourceMap())
                .endResources()
                .withStorageClassName(getStorageClassNameOrDefault())
                .endSpec()
                .build();
            pvc = client.persistentVolumeClaims().inNamespace(podMetaData.getNamespace()).create(pvc);
            LOGGER.log(INFO, "Created PVC: {0}/{1}", new Object[] { namespace, pvc.getMetadata().getName() });
        } else {
            LOGGER.log(INFO, "Reused PVC: {0}/{1}", new Object[] { namespace, pvc.getMetadata().getName() });
        }

         return pvc;
    }

    public String getStorageClassNameOrDefault(){
        if (getStorageClassName() != null) {
            return getStorageClassName();
        }
        return null;
    }

    public String getAccessModesOrDefault() {
        if (getAccessModes() != null) {
            return getAccessModes();
        }
        return "ReadWriteOnce";
    }

    public String getRequestsSizeOrDefault() {
        if (getRequestsSize() != null) {
            return getRequestsSize();
        }
        return "10Gi";
    }

    protected Map<String, Quantity> getResourceMap() {
        ImmutableMap.Builder<String, Quantity> builder = ImmutableMap.<String, Quantity>builder();
        String actualStorage = substituteEnv(getRequestsSizeOrDefault());
            Quantity storageQuantity = new Quantity(actualStorage);
            builder.put("storage", storageQuantity);
        return builder.build();
    }

    public static String normalizedJobFullName(String jobFullName) {
        return "pvc-" + jobFullName.trim()
            .replace(' ', '-')
            .replace('_', '-')
            .replace('/', '-')
            .toLowerCase();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DynamicJobPVCWorkspaceVolume that = (DynamicJobPVCWorkspaceVolume) o;
        return Objects.equals(storageClassName, that.storageClassName) &&
                Objects.equals(requestsSize, that.requestsSize) &&
                Objects.equals(accessModes, that.accessModes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(storageClassName, requestsSize, accessModes);
    }

    @Extension
    @Symbol("dynamicJobPVC")
    public static class DescriptorImpl extends Descriptor<WorkspaceVolume> {

        private static final ListBoxModel ACCESS_MODES_BOX = new ListBoxModel()
                .add("ReadWriteOnce")
                .add("ReadOnlyMany")
                .add("ReadWriteMany");

        @Override
        public String getDisplayName() {
            return "Dynamic Job Persistent Volume Claim Workspace Volume";
        }

        @SuppressWarnings("unused") // by stapler
        @RequirePOST
        @Restricted(DoNotUse.class) // stapler only
        public ListBoxModel doFillAccessModesItems(){
            return ACCESS_MODES_BOX;
        }
    }
}
