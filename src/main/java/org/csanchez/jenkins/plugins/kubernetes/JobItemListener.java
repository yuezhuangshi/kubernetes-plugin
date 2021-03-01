package org.csanchez.jenkins.plugins.kubernetes;

import com.cloudbees.hudson.plugins.folder.Folder;
import hudson.Extension;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.TopLevelItem;
import hudson.model.listeners.ItemListener;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.client.KubernetesClient;
import jenkins.model.Jenkins;
import org.csanchez.jenkins.plugins.kubernetes.volumes.workspace.DynamicJobPVCWorkspaceVolume;
import org.jenkinsci.plugins.kubernetes.auth.KubernetesAuthException;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * delete DynamicJobPVCWorkspaceVolume when job deleted if using
 * @author jasper
 */
@Extension
public class JobItemListener extends ItemListener {

    private static final Logger LOGGER = Logger.getLogger(JobItemListener.class.getName());

    @Override
    public void onLocationChanged(Item item, String oldFullName, String newFullName) {
        if (item instanceof Job)  {
            deletePVC(oldFullName);
        }
    }

    @Override
    public void onDeleted(Item item) {
        if (item instanceof Job) {
            deletePVC(item.getFullName());
        }
    }

    private void deletePVC(String jobFullName) {
        for (KubernetesCloud cloud : Jenkins.get().clouds.getAll(KubernetesCloud.class)) {
            try {
                KubernetesClient client = KubernetesClientProvider.createClient(cloud);
                List<PersistentVolumeClaim> persistentVolumeClaims = client.persistentVolumeClaims().list().getItems();
                PersistentVolumeClaim pvc = persistentVolumeClaims.stream().filter(p ->
                    Objects.equals(p.getMetadata().getName(), DynamicJobPVCWorkspaceVolume.getPVCName(jobFullName))
                ).findFirst().orElse(null);

                if (null != pvc) {
                    String pvcName = pvc.getMetadata().getName();
                    String namespace = pvc.getMetadata().getNamespace();
                    Boolean delete = client.persistentVolumeClaims().delete(pvc);
                    if (delete) {
                        LOGGER.log(Level.INFO, "Removed pvc {0} in namespace {1} for job {2}",
                            new Object[] { pvcName, namespace, jobFullName });
                    } else {
                        LOGGER.log(Level.WARNING, "Failed to removed pvc {0} in namespace {1} for job {2}",
                            new Object[] { pvcName, namespace, jobFullName });
                    }
                }
            } catch (KubernetesAuthException | IOException e) {
                String msg = String.format(
                    "Failed to connect to cloud %s. There may be leftover resources on the Kubernetes cluster.",
                    cloud.getDisplayName()
                );
                LOGGER.log(Level.SEVERE, msg);
            }
        }
    }

}
