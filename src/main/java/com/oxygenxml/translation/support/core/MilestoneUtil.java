package com.oxygenxml.translation.support.core;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.List;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import org.apache.commons.io.FilenameUtils;
import org.apache.log4j.Logger;
import com.oxygenxml.translation.exceptions.StoppedByUserException;
import com.oxygenxml.translation.support.core.resource.IRootResource;
import com.oxygenxml.translation.support.storage.InfoResources;
import com.oxygenxml.translation.support.storage.ResourceInfo;
import ro.sync.exml.workspace.api.PluginWorkspace;
import ro.sync.exml.workspace.api.PluginWorkspaceProvider;

public class MilestoneUtil {
  
  /**
   * Private constructor.
   */
  private MilestoneUtil() {
    // Avoid instantiation.
  }
  
  /**
   *  Logger for logging.
   */
  private static Logger logger = Logger.getLogger(MilestoneUtil.class);

  /**
   * Predefined suffix of each generated milestone.
   */
  public static final String MILESTONE_FILE_NAME = "_translation_milestone.xml";

  /**
   * Reads a file and generates an MD5 from its content.
   * 
   * @param file The file to read.
   * 
   * @return An unique MD5 hash.
   * 
   * @throws NoSuchAlgorithmException The MD5 algorithm is not available.
   * @throws FileNotFoundException The file doesn't exist.
   * @throws IOException Problems reading the file.
   */
  public static String generateMD5(File file) throws NoSuchAlgorithmException, IOException {
    return generateMD5(new java.io.FileInputStream(file));
  }
  
  /**
   * Reads a file and generates an MD5 from its content.
   * 
   * @param resource The resource to read.
   * 
   * @return An unique MD5 hash.
   * 
   * @throws NoSuchAlgorithmException The MD5 algorithm is not available.
   * @throws FileNotFoundException The file doesn't exist.
   * @throws IOException Problems reading the file.
   */
  public static String generateMD5(URL resource) throws NoSuchAlgorithmException, IOException {
    return generateMD5(resource.openStream());
  }
  
  /**
   * Reads a file and generates an MD5 from its content.
   * @param resource The resource to read.
   * 
   * @return An unique MD5 hash.
   * 
   * @throws NoSuchAlgorithmException The MD5 algorithm is not available.
   * @throws FileNotFoundException The file doesn't exist.
   * @throws IOException Problems reading the file.
   */
  public static String generateMD5(InputStream stream) throws NoSuchAlgorithmException, IOException {
    MessageDigest md = MessageDigest.getInstance("MD5");
  
    byte[] dataBytes = new byte[8 * 1024];
    BufferedInputStream bis = new BufferedInputStream(stream);
    int nread = 0;
    try {
      while ((nread = bis.read(dataBytes)) != -1) {
        md.update(dataBytes, 0, nread);
      }
    } finally {
      try {
        bis.close();
      } catch (IOException e) {
        logger.error(e, e);
      }
    }
  
    return toHexString(md.digest());
  
  }

  /**
   * Loads the information about file changes from disk.
   * 
   * @param iRootResource The location of the "special file"(milestone file).
   * 
   * @return	The content of the "special file"(milestone).
   * 
   * @throws JAXBException	 Problems with JAXB, serialization/deserialization of a file.
   */
  public static List<ResourceInfo> loadMilestoneFile(IRootResource iRootResource) throws JAXBException, IOException {
    File milestoneFile = iRootResource.getMilestoneFile();
  
    if (!milestoneFile.exists()) {
      throw new IOException("No milestone was created.");
    }
  
    JAXBContext jaxbContext = JAXBContext.newInstance(InfoResources.class); 
  
    Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();   
  
    InfoResources resources = (InfoResources) jaxbUnmarshaller.unmarshal(milestoneFile);    
  
    return resources.getList();
  }
  

  /**
   * Saves the information about file changes on disk. 
   * 
   * @param info  An object of type InfoResources,this object will be serialized.
   * @param rootDir The milestome file where to store the information.
   * 
   * @throws JAXBException   Problems with JAXB, serialization/deserialization of a file.
   * @throws FileNotFoundException  The file doesn't exist.
   * @throws StoppedByUserException The user pressed the cancel button.
   */
  public static void storeMilestoneFile(InfoResources info, File milestoneFile) throws JAXBException{
    JAXBContext context = JAXBContext.newInstance(InfoResources.class);  

    Marshaller marshaller = context.createMarshaller();  
    marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);  

    marshaller.marshal(info, milestoneFile);        
  }
  
  /**
   * Returns a string representation of the argument as a bytes array. 
   * 
   * @param bytes A bytes array.
   * @return      Returns a string representation of the argument as a bytes array.
   */
  public static String toHexString(byte[] bytes) {
    StringBuilder hexString = new StringBuilder();

    for (int i = 0; i < bytes.length; i++) {
      String hex = Integer.toHexString(0xFF & bytes[i]);
      if (hex.length() == 1) {
        hexString.append('0');
      }
      hexString.append(hex);
    }

    return hexString.toString();
  }
  
  
  /**
   * Loads the last creation date of the milestone file.
   * 
   * @param rootDir The location of the "special file"(milestone file).
   * 
   * @return  The last creation date of the "special file"(milestone).
   * 
   * @throws JAXBException   Problems with JAXB, serialization/deserialization of a file.
   */
  public static Date getMilestoneCreationDate(URL rootMap, File milestoneFile) throws JAXBException, IOException {
    File rootMapFile = getFile(rootMap);
    if (milestoneFile == null) {
      milestoneFile = new File(rootMapFile.getParentFile(),MilestoneUtil.getMilestoneFileName(rootMapFile));
    }

    if (!milestoneFile.exists()) {
      throw new IOException("No milestone was created.");
    }

    JAXBContext jaxbContext = JAXBContext.newInstance(InfoResources.class); 
    Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();   
    InfoResources resources = (InfoResources) jaxbUnmarshaller.unmarshal(milestoneFile);    

    return resources.getMilestoneCreation();
  }

  /**
   * Gets the file where the milestone information was saved for the given resource.
   * 
   * @param resource The root map.
   * 
   * @return The file where the milestone information was saved for the given resource.
   */
  public static File getMilestoneFile(URL resource) {
    File rootMapFile = getFile(resource);
    
    return getMilestoneFile(rootMapFile);
  }

  /**
   * Gets the file pointed by the given URL.
   * 
   * @param resource The URL of the resource.
   * 
   * @return The local file or <code>null</code> if the URL doesn't represent a local file.
   */
  public static File getFile(URL resource) {
    PluginWorkspace pluginWorkspace = PluginWorkspaceProvider.getPluginWorkspace();
    File rootMapFile = null;
    if (pluginWorkspace != null) {
      rootMapFile = pluginWorkspace.getUtilAccess().locateFile(resource);
    } else {
      rootMapFile = new File(resource.getPath());
    }
    return rootMapFile;
  }

  /**
   * Gets the file where the milestone information was saved for the given resource.
   * 
   * @param rootMapFile The root map.
   * 
   * @return The file where the milestone information was saved for the given resource.
   */
  public static File getMilestoneFile(File rootMapFile) {
    return new File(rootMapFile.getParentFile(), MilestoneUtil.getMilestoneFileName(rootMapFile));
  }
  
  /**
   * @param rootMapFile Current DITA file.
   * @return Returns the corresponding milestone of the current dita map file.
   */
  public static String getMilestoneFileName(File rootMapFile) {
    String name = FilenameUtils.removeExtension(rootMapFile.getName());
    return name + MILESTONE_FILE_NAME;
  }
  
  public static List<ResourceInfo> loadMilestoneContentFromFile(File milestone){
    List<ResourceInfo> list = null;
    try {
      JAXBContext jaxbContext = JAXBContext.newInstance(InfoResources.class);
      Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();   
      InfoResources resources = (InfoResources) jaxbUnmarshaller.unmarshal(milestone);    
      list = resources.getList();
    } catch (JAXBException e) {
      logger.error(e, e);
    } 
    return list;
  }
}
