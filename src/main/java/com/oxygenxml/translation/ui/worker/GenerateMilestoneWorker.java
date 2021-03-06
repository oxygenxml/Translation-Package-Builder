package com.oxygenxml.translation.ui.worker;

import java.io.File;
import java.net.URL;

import com.oxygenxml.translation.support.core.ChangePackageGenerator;
import com.oxygenxml.translation.support.core.resource.ResourceFactory;
/**
 * Creates an AbstractWorker for generating the milestone file.
 * 
 * @author Bivolan Dalina
 */
public class GenerateMilestoneWorker extends AbstractWorker<File> {
  /**
   * The root map.
   */
  private URL rootMap;
  
  /**
   * Constructor.
   * 
   * @param rootMap System ID of the DITA map.
   */
  public GenerateMilestoneWorker(URL rootMap) {
    this.rootMap = rootMap;
  }
  
  /**
   * Main task. Executed in background thread.
   */
  @Override
  protected File doInBackground() throws Exception {
    ChangePackageGenerator packageBuilder = new ChangePackageGenerator(listeners);
    return packageBuilder.generateChangeMilestone(ResourceFactory.getInstance().getResource(rootMap));
  }
}
