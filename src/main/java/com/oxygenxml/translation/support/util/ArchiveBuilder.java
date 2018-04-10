package com.oxygenxml.translation.support.util;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.apache.log4j.Logger;
import com.oxygenxml.translation.exceptions.StoppedByUserException;
import com.oxygenxml.translation.ui.ProgressChangeAdapter;
import com.oxygenxml.translation.ui.ProgressChangeEvent;
import com.oxygenxml.translation.ui.ProgressChangeListener;
import com.oxygenxml.translation.ui.Tags;

import ro.sync.exml.workspace.api.PluginResourceBundle;
import ro.sync.exml.workspace.api.PluginWorkspaceProvider;
import ro.sync.exml.workspace.api.standalone.StandalonePluginWorkspace;
import ro.sync.io.FileSystemUtil;

/**
 * 
 * This class contains 3 methods : 
 * - zipDirectory - this method makes an archive of a directory at the specified location
 * - unzipDirectory - this method extracts the content of an archive at a specified location
 * - copyDirectory - this method copies the content of a source directory into a destination directory.
 */
public final class ArchiveBuilder {
  
  /**
   * Logger for logging.
   */
  private static final Logger logger = Logger.getLogger(ArchiveBuilder.class.getName());

  /**
   * A list of ProgressChangeListener listeners.
   */
  private List<ProgressChangeListener> listeners;
  
  /**
   * Constructor.
   * 
   * @param listeners A list of listeners. Not <code>null</code>.  
   */
  public ArchiveBuilder(List<ProgressChangeListener> listeners) {
    if (listeners == null) {
      this.listeners = new ArrayList<>();
    } else {
      this.listeners = listeners;
    }
  }

  /**
   * Packs a directory.
   * 
   * @param dir  The location of the file/directory we want to zip.
   * @param zipFile  The location of the package.
   * @param isFromTest True if this method is called by a JUnit test class.
   * 
   * @throws IOException  Problems reading the file.
   * @throws StoppedByUserException The user pressed the Cancel button.
   */
  public void zipDirectory(File dir, File zipFile) throws IOException, StoppedByUserException {
    zipFile.getParentFile().mkdirs();

    try (
        FileOutputStream fout = new FileOutputStream(zipFile);
        ZipOutputStream zout = new ZipOutputStream(fout) ) {
      zipSubDirectory("", dir, zout, 0);
    } catch (Exception e) {
      logger.error(e, e);
    }
  }

  /**
   *  Packs a directory.
   * 
   * @param basePath  It helps us create the relative path of every file from dir.
   * @param dir  The location of the file/directory we want to zip.
   * @param zout  Where we create the archive.
   * @param resourceCounter Counts the number of resources added in the archive.
   * @param isFromTest True if this method is called by a JUnit test class.
   * 
   * @throws IOException  Problems reading the files.
   * @throws StoppedByUserException The user pressed the Cancel button.
   */
  private void zipSubDirectory(String basePath, File dir, ZipOutputStream zout, int resourceCounter)
      throws IOException, StoppedByUserException {
    byte[] buffer = new byte[4096];
    File[] files = dir.listFiles();
    if (files != null) {
      for (File file : files) {
        if (file.isDirectory()) {
          zipFolderInternal(basePath, zout, resourceCounter, file);
        } else {
          zipFileInternal(basePath, zout, resourceCounter, buffer, file);
        }
      }
    }
  }
  
  /**
   * ZIP a folder internal.
   */
  private void zipFolderInternal(String basePath, ZipOutputStream zout, int resourceCounter, File file)
      throws IOException, StoppedByUserException {
    String path = basePath + file.getName() + '/';
    zout.putNextEntry(new ZipEntry(path));
    if(isCanceled()){
      throw new StoppedByUserException();
    }
    zipSubDirectory(path, file, zout, resourceCounter);
    zout.closeEntry();
  }
  
  /**
   * ZIP a file internal.
   */
  private void zipFileInternal(String basePath, ZipOutputStream zout, int resourceCounter, 
      byte[] buffer, File file) throws IOException, StoppedByUserException {
    
    try( FileInputStream fin = new FileInputStream(file) ){
      zout.putNextEntry(new ZipEntry(basePath + file.getName()));
      int length;
      while ((length = fin.read(buffer)) > 0) {
        zout.write(buffer, 0, length);
      }
      
      if(isCanceled()){
        throw new StoppedByUserException();
      }
      
      PluginResourceBundle resourceBundle = ((StandalonePluginWorkspace)PluginWorkspaceProvider.getPluginWorkspace()).getResourceBundle();
      resourceCounter++;
      ProgressChangeEvent progress = new ProgressChangeEvent(resourceCounter, 
          resourceCounter + resourceBundle.getMessage(Tags.ZIPDIR_PROGRESS_TEXT));
      fireChangeEvent(progress);

    } catch (Exception e) {
      logger.error(e, e);
    }
  }



  /**
   * UnZips an archive into a given directory.
   *    
   * @param packageLocation  The location of the package.
   * @param destDir Where to extract the package content.
   * 
   * @return A list with the relative path of every extracted file.
   * 
   * @throws StoppedByUserException The user pressed the Cancel button.
   */
  public List<String> unzipDirectory(File packageLocation, File destDir) throws StoppedByUserException {

    List<String> nameList = new ArrayList<>();
    int counter = 0;
    try {
      // Open the zip file
      ZipFile zipFile = new ZipFile(packageLocation);
      Enumeration<?> enu = zipFile.entries();
      try {
        while (enu.hasMoreElements()) {
          ZipEntry zipEntry = (ZipEntry) enu.nextElement();
          String name = zipEntry.getName();
          //We create the directories 
          File file = new File(destDir, name);
          if (name.endsWith("/")) {
            file.mkdirs();
            continue;
          }

          unzipInternal(zipEntry, zipFile, file);

          nameList.add(name);

          if(isCanceled()){
            throw new StoppedByUserException();
          }

          PluginResourceBundle resourceBundle = ((StandalonePluginWorkspace)PluginWorkspaceProvider.getPluginWorkspace()).getResourceBundle();
          counter++;
          ProgressChangeEvent progress = new ProgressChangeEvent(counter, counter + resourceBundle.getMessage(Tags.UNZIPDIR_PROGRESS_TEXT));
          fireChangeEvent(progress);

        }
      } finally {
        zipFile.close();
      }
      
    } catch (IOException e) {
      fireOperationFailed(e);
    }

    return nameList;
  }
  
  /**
   * Copy the input stream of the selected zip entry to disk.
   * 
   * @param zipEntry Current ZIP entry.
   * @param zipFile  The ZIP file (source).
   * @param file     target file.
   *  
   * @throws IOException  If unZipping fails.
   */
  private void unzipInternal(ZipEntry zipEntry, ZipFile zipFile, File file) throws IOException {

    File parent = file.getParentFile();
    if (parent != null) {
      parent.mkdirs();
    }

    //  Extract the file
    try(
        InputStream is = zipFile.getInputStream(zipEntry);                  
        FileOutputStream fos = new FileOutputStream(file) ) {
      FileSystemUtil.copyInputStreamToOutputStream(is, fos, true);
    } catch (Exception e) {
      logger.error(e, e);
    }
  }

  /**
   *  Copies all the files from a source directory to a destination directory.
   * 
   * @param sourceLocation The location of the files that are about to be copied.
   * @param targetLocation  Where to copy the files.
   * @param counter  Computes the number of copied files.
   * @param isFromTest True if this method is called by a JUnit test class.
   * 
   * @throws IOException Problems reading the files.
   * @throws StoppedByUserException  The user pressed the Cancel button.
   */
  public void copyDirectory(File sourceLocation , File targetLocation, int counter, boolean isFromTest) throws IOException, StoppedByUserException {

    if (sourceLocation.isDirectory()) {
      if (!targetLocation.exists()) {
        targetLocation.mkdir();
      }

      String[] children = sourceLocation.list();
      for (int i=0; i<children.length; i++) {
        copyDirectory(new File(sourceLocation, children[i]),
            new File(targetLocation, children[i]), counter, isFromTest);
        if(isCanceled()){
          throw new StoppedByUserException();
        }
      }
    } else {
      FileSystemUtil.copyFile(sourceLocation, targetLocation, true);
      counter++;
      if(isCanceled()){
        throw new StoppedByUserException();
      }
      
      PluginResourceBundle resourceBundle = ((StandalonePluginWorkspace)PluginWorkspaceProvider.getPluginWorkspace()).getResourceBundle();
      ProgressChangeEvent progress = new ProgressChangeEvent(counter, 
          String.valueOf(counter) + resourceBundle.getMessage(Tags.COPYDIR_PROGRESS_TEXT));
      fireChangeEvent(progress);

    }
  }

  /**
   * Notifies all listeners to update the progress of the task.
   * 
   * @param progress A ProgressChangeEvent object.
   */
  private void fireChangeEvent(ProgressChangeEvent progress) {
    for (ProgressChangeListener progressChangeListener : listeners) {
      progressChangeListener.change(progress);
    }
  }
  /**
   * Notifies all listeners that the task was canceled.
   * 
   * @return True if the worker was canceled, false otherwise.
   */
  private boolean isCanceled() {
    boolean result = false;
    for (ProgressChangeListener progressChangeListener : listeners) {
      if (progressChangeListener.isCanceled()) {
        result =  true;
      }
    }
    return result;
  }
  /**
   * Notifies all listeners that the task has failed.
   * 
   * @param ex An Exception.
   */
  private void fireOperationFailed(Exception ex) {
    for (ProgressChangeListener progressChangeListener : listeners) {
      progressChangeListener.operationFailed(ex);
    }
  }
  
  /**
   * Sets the progress listeners.
   */
  public void addListener(ProgressChangeAdapter progressChangeAdapter) {
    if (listeners == null) {
      listeners = new ArrayList<>();
    }
    listeners.add(progressChangeAdapter);
  }

}


