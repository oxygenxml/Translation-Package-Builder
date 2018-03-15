package com.oxygenxml.translation.support.core;

import com.oxygenxml.translation.support.core.resource.IResource;
import com.oxygenxml.translation.support.core.resource.IRootResource;
import com.oxygenxml.translation.support.storage.InfoResources;
import com.oxygenxml.translation.support.storage.ResourceInfo;
import com.oxygenxml.translation.support.util.ArchiveBuilder;
import com.oxygenxml.translation.support.util.MessagePresenter;
import com.oxygenxml.translation.support.util.PathUtil;
import com.oxygenxml.translation.ui.PackResult;
import com.oxygenxml.translation.ui.ProgressChangeAdapter;
import com.oxygenxml.translation.ui.ProgressChangeEvent;
import com.oxygenxml.translation.ui.ProgressChangeListener;
import com.oxygenxml.translation.ui.StoppedByUserException;
import com.oxygenxml.translation.ui.Tags;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import javax.xml.bind.JAXBException;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import ro.sync.document.DocumentPositionedInfo;
import ro.sync.exml.workspace.api.PluginResourceBundle;
import ro.sync.exml.workspace.api.PluginWorkspaceProvider;
import ro.sync.exml.workspace.api.results.ResultsManager.ResultType;
import ro.sync.exml.workspace.api.standalone.StandalonePluginWorkspace;

/**
 * What this class does:
 * 
 * 1. generateChangeMilestone   - iterates over a directory, computes MD5s and writes them in a marker file.
 * 2. collectModifiedResources  - iterates over a directory, computes MD5s and compares them with the ones from the milestone. 
 * 3. generateChangedFilesPackage - puts the modified files in ZIP
 */
public class ChangePackageGenerator {
  /**
   *  Logger for logging.
   */
  private static Logger logger = Logger.getLogger(ChangePackageGenerator.class); 
  /**
   *  A list of custom listeners.
   */
  private List<ProgressChangeListener> listeners = new ArrayList<ProgressChangeListener>();
  
  /**
   * A list containing all the resources that were unable to pack to archive.
   */
  private List<String> filesNotCopied = new ArrayList<String>();
  
  /**
   * The common ancestor of all the DITA resources referred in the DITA map tree. 
   * Either the DITA map folder or an ancestor of it.
   */
  private String commonPath;
  
  /**
   * Constructor.
   */
  public ChangePackageGenerator(List<ProgressChangeListener> listeners) {
    this.listeners = listeners; 
  }

  /**
   * Iterates over the descendants of the given files and computes a hash and a relative path.
   * 
   * The computed relative paths are relative to the entry point. For example:
   * Entry point: c:testIteration
   * Relative path: dir1/test.txt
   * Relative path: dir2/test.txt
   * Relative path: dir2/dir21/test.txt
   * 
   * @param dirs A stack used to compute a path relative to an ancestor.
   * @param list A list of ResourceTnfo objects which contains an unique MD5 and a relative path for
   *        every file inside dirPath.
   * 
   * @throws NoSuchAlgorithmException The MD5 algorithm is not available.
   * @throws FileNotFoundException  The file doesn't exist.
   * @throws IOException Problems reading the file/directory.
   * @throws StoppedByUserException The user pressed the cancel button.
   */
  public void computeResourceInfo(IResource resource, List<ResourceInfo> list, Set<URL> visited) 
      throws NoSuchAlgorithmException, IOException, StoppedByUserException {
    
    Iterator<IResource> referredResources = resource.iterator();

    if (referredResources != null) {
      while (referredResources.hasNext()) {
        if(isCanceled()){
          throw new StoppedByUserException();
        }
        
        IResource iResource = referredResources.next();
        URL currentUrl = iResource.getCurrentUrl();
        computeInternal(resource, list, visited, iResource, currentUrl);
        // Go deep.
        computeResourceInfo(iResource, list, visited);
      }
    }
  }
  
  /**
   * 
   * @param resource
   * @param list
   * @param visited
   * @param iResource
   * @param currentUrl
   * @throws NoSuchAlgorithmException
   */
  private void computeInternal(IResource resource, List<ResourceInfo> list, Set<URL> visited, IResource iResource,
      URL currentUrl) throws NoSuchAlgorithmException {
    if (currentUrl != null && !visited.contains(currentUrl)) {
      visited.add(currentUrl);
      if ("file".equals(currentUrl.getProtocol())) {
        // #15 Pack just the local files into the achive. 
        try {
          // Collect the milestone related info.
          ResourceInfo resourceInfo = iResource.getResourceInfo();
          if (resourceInfo != null) {
            list.add(resourceInfo);
          }
        } catch (IOException e) {
          MessagePresenter.showInResultsPanel(DocumentPositionedInfo.SEVERITY_WARN, 
              e.getMessage(), 
              resource.getCurrentUrl().toExternalForm(), 
              ResultType.PROBLEM);
        }
      }
    }
  }

  /**
   * Computes what resources were changed since the last created milestone.
   *  
   * @return A list of modified resources.
   * 
   * @throws JAXBException	 Problems with JAXB, serialization/deserialization of a file.
   * @throws NoSuchAlgorithmException	The MD5 algorithm is not available.
   * @throws IOException	Problems reading the file/directory.
   * @throws StoppedByUserException The user pressed the cancel button.
   */
  public List<ResourceInfo> collectModifiedResources(IRootResource resource) 
      throws JAXBException, NoSuchAlgorithmException, IOException, StoppedByUserException{
    /*
     * 1. Loads the milestone XML from rootDIr using JAXB
     * 2. Calls generateCurrentMD5() to get the current MD5s
     * 3. Compares the current file MD5 with the old ones and collects the changed resources.
     */
    // Store state.
    Set<ResourceInfo> resources = new HashSet<ResourceInfo>(MilestoneUtil.loadMilestoneFile(resource));
    
    //Current states.
    List<ResourceInfo> currentStates = new ArrayList<ResourceInfo>();
    
    // Add the root map.
    ResourceInfo rootResource = resource.getResourceInfo();
    if (rootResource != null) {
      currentStates.add(rootResource);
    }
    
    Set<URL> visited = new HashSet<URL>(); //NOSONAR
    computeResourceInfo(resource, currentStates, visited);
    if (resource.getCurrentUrl() != null) {
      visited.add(resource.getCurrentUrl());
    }
    
    commonPath = PathUtil.commonPath(visited);
    
    // A list to hold the modified resources.
    ArrayList<ResourceInfo> modifiedResources = new ArrayList<ResourceInfo>();
    int counter = 0;
    // Compare serializedResources with newly generated hashes.
    for (ResourceInfo newInfo : currentStates) {
      // Use a HasSet to ensure better search performance.
      if (!resources.contains(newInfo)) {
        modifiedResources.add(newInfo);
      }
      
      PluginResourceBundle resourceBundle = ((StandalonePluginWorkspace)PluginWorkspaceProvider.getPluginWorkspace()).getResourceBundle();
      counter++;
      if(isCanceled()){
        throw new StoppedByUserException();
      }
      ProgressChangeEvent progress = new ProgressChangeEvent(counter, 
          resourceBundle.getMessage(Tags.GENERATE_MODIFIED_FILES_PROGRESS_MESSAGE1) + 
          counter + 
          resourceBundle.getMessage(Tags.GENERATE_MODIFIED_FILES_PROGRESS_MESSAGE2), 
          currentStates.size());
      fireChangeEvent(progress);
    }
    
    return modifiedResources;
  }
  /**
   * Entry point. Detect what files were modified and put them in a ZIP.
   * 
   * @param rootDir Folder of the DITA map.
   * @param packageLocation The location of the generated ZIP file.
   * @param modifiedResources The list with all the modified files.
   * @param isFromTest True if this method is called by a JUnit test class.
   * @param topLocationInFileSystem The common ancestor of all the DITA resources referred in the DITA map tree. Either the DITA map folder or an ancestor of it.
   * 
   * @return How many files were modified.
   * 
   * @throws IOException  Problems reading the file/directory.
   * @throws StoppedByUserException The user pressed the Cancel button.
   */
  public PackResult generateChangedFilesPackage(
      // TODO Adrian Pass just one Interator<URL> instead of "rootDir" and "modifiedResources"
      File rootDir,
      File packageLocation,
      List<ResourceInfo> modifiedResources,
      boolean isFromTest,
      String topLocationInFileSystem) throws IOException, StoppedByUserException  {

    /**
     * 1. Inside a temporary "destinationDir" creates a file structure and copies the changed files.
     * 2. ZIP the "destinationDir" at "packageLocation".
     * 3. Delete the "destinationDir".
     */
    PackResult result = new PackResult();

    int nrModFiles = 0;
    final int totalModifiedfiles = modifiedResources.size();
    // If there are modified resources
    if (!modifiedResources.isEmpty()) {
        final File tempDir = new File(rootDir, "toArchive");
        //We iterate over the list above, build the sistem of files in a temporary directory and copy the 
        //files in the right directory
        //Then we compress the tempDir and delete it.
        try{
          for(ResourceInfo aux : modifiedResources){
            String relativePath = aux.getRelativePath();
            /*
             * #15 - the relative paths can be path/to.file.dita#ID
             * We have to remove the anchors to allow file copy.
             */
            int indexOf = relativePath.indexOf('#');
            if(indexOf != -1){
              relativePath = relativePath.substring(0, indexOf);
            }
            
            URL url = new URL(rootDir.toURI().toURL(), relativePath);
            
            // TODO Adrian Make it relative to the TOP dir.
            String relativeLocationToRootDir = url.toExternalForm().replaceAll(topLocationInFileSystem, "");
            
            File dest = new File(tempDir, relativeLocationToRootDir);
            dest.getParentFile().mkdirs();
            
            try {
              FileUtils.copyURLToFile(url, dest);
            } catch (IOException e) {
              filesNotCopied.add(relativePath);
            }

            if(isCanceled()){
              throw new StoppedByUserException();
            }
            
            PluginResourceBundle resourceBundle = ((StandalonePluginWorkspace)PluginWorkspaceProvider.getPluginWorkspace()).getResourceBundle();
            nrModFiles++;
            ProgressChangeEvent progress = new ProgressChangeEvent(nrModFiles, resourceBundle.getMessage(Tags.PACKAGEBUILDER_PROGRESS_TEXT1) + nrModFiles + resourceBundle.getMessage(Tags.PACKAGEBUILDER_PROGRESS_TEXT2), 2*totalModifiedfiles);
            fireChangeEvent(progress);
            
          }

          result.setModifiedFilesNumber(nrModFiles);

          ArchiveBuilder archiveBuilder = new ArchiveBuilder(null);
          archiveBuilder.addListener(new ProgressChangeAdapter() {
            @Override
            public boolean isCanceled() {
              return ChangePackageGenerator.this.isCanceled();
            }
            
            @Override
            public void done() {
              fireDoneEvent();
            }
            
            @Override
            public void change(ProgressChangeEvent progress) {
              ProgressChangeEvent event = new ProgressChangeEvent(progress.getCounter() + totalModifiedfiles, progress.getMessage(), 2*totalModifiedfiles);
              fireChangeEvent(event);
            }
          });
          archiveBuilder.zipDirectory(tempDir, packageLocation);
        } finally {
          FileUtils.deleteDirectory(tempDir);
        }
    }

    return result;
  }

  /**
   * Entry point. Compute a hash for each file in the given directory and store this information
   * inside the directory (as a "special file"). 
   * 
   * @return	The "special file"(translation_builder_milestone.xml).
   * 
   * @throws NoSuchAlgorithmException	The MD5 algorithm is not available.
   * @throws FileNotFoundException	The file/directory doesn't exist.
   * @throws IOException	Problems reading the file/directory.
   * @throws JAXBException	 Problems with JAXB, serialization/deserialization of a file.
   * @throws StoppedByUserException The user pressed the "Cancel" button.
   */
  public File generateChangeMilestone(IRootResource resource) 
      throws NoSuchAlgorithmException, IOException, JAXBException, StoppedByUserException {
    List<ResourceInfo> list = new ArrayList<ResourceInfo>();
    
    // Add the root map
    ResourceInfo rootResourceInfo = resource.getResourceInfo();
    if (rootResourceInfo != null) {
      list.add(rootResourceInfo);
    }
    
    computeResourceInfo(resource, list, new HashSet<URL>());
    File milestoneFile = resource.getMilestoneFile();
    /**
     * TODO Adrian check functionality of the date and time of the milestone creation.
     */
    long lastModified = milestoneFile.lastModified();
    if (lastModified == 0) {
      lastModified = new Date().getTime();
    }
    MilestoneUtil.storeMilestoneFile(
        new InfoResources(list, new Date(lastModified)), 
        milestoneFile);
    if(isCanceled()){
      throw new StoppedByUserException();
    }
    
    PluginResourceBundle resourceBundle = ((StandalonePluginWorkspace)PluginWorkspaceProvider.getPluginWorkspace()).getResourceBundle();
    ProgressChangeEvent progress = new ProgressChangeEvent(resourceBundle.getMessage(Tags.CHANGE_MILESTONE_PROGRESS_TEXT) + "...");
    fireChangeEvent(progress);

    return milestoneFile;
  }

  /**
   * Notifies all listeners to update the progress of the task.
   * @param progress A ProgressChangeEvent object.
   */
  private void fireChangeEvent(ProgressChangeEvent progress) {
    if (listeners != null) {
      for (ProgressChangeListener progressChangeListener : listeners) {
        progressChangeListener.change(progress);
      } 
    }
  }
  
  /**
   * Notifies all listeners that the task has finished.
   */
  private void fireDoneEvent() {
    if (listeners != null) {
      for (ProgressChangeListener progressChangeListener : listeners) {
        progressChangeListener.done();
      }
    }
  }
  /**
   * Notifies all listeners that the task was canceled.
   * 
   * @return True if the worker was canceled, false otherwise.
   */
  private boolean isCanceled() {
    boolean result = false;
    if (listeners != null) {
      for (ProgressChangeListener progressChangeListener : listeners) {
        if (progressChangeListener.isCanceled()) {
          result =  true;
        }
      }
    }
    return result;
  }
  
  /**
   * @return The common ancestor of all the DITA resources referred in the DITA map tree or <code>null</code>.
   */
  public String getCommonPath() {
    return commonPath;
  }
  
  /**
   * @return The list of resource that were unable to copy.
   */
  public List<String> getFilesNotCopied() {
    return filesNotCopied;
  }
 
}
