package com.oxygenxml.translation.support.core.models;

import java.util.ArrayList;
import java.util.Date;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * 
 * This class makes an object that contains a list with Resource Info objects.
 *
 */
@XmlRootElement(name = "resources")
@XmlAccessorType (XmlAccessType.FIELD)
public class InfoResources {
  /**
   * The date and time of the last milestone creation.
   */
  @XmlAttribute(name = "date")
  private Date milestoneCreation;
  
	/**
	 * list Gathers the generated ResourceInfo objects.
	 */
	@XmlElement(name = "info-resource")
	private ArrayList<ResourceInfo> list;
	
	public Date getMilestoneCreation() {
    return milestoneCreation;
  }
  public void setMilestoneCreation(Date milestoneCreation) {
    this.milestoneCreation = milestoneCreation;
  }
	
	public ArrayList<ResourceInfo> getList() {
		return list;
	}
	
	public void setList(ArrayList<ResourceInfo> list) {
		this.list = list;
	}
	
	public InfoResources(ArrayList<ResourceInfo> list, Date milestoneCreation) {
    this.list = list;
    this.milestoneCreation = milestoneCreation;
  }
	
	public InfoResources(){	}
	
	public InfoResources(ArrayList<ResourceInfo> list) {
		this.list = list;
	}
		
}
