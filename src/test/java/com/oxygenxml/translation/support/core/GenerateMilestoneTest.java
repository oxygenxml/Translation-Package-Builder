package com.oxygenxml.translation.support.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;

import javax.xml.bind.JAXBException;

import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Test;

import com.oxygenxml.translation.support.util.PathOption;
import com.oxygenxml.translation.ui.StoppedByUserException;

public class GenerateMilestoneTest {
  private PathOption pathOption = new PathOption();
  
  @Test
  public void testMd5_File() throws Exception {
    File file = pathOption.getPath("md5Test.txt");
    
    String cksum = PackageBuilder.generateMD5(file);
    System.out.println(cksum);
    Assert.assertEquals("c439e0812a8e0a5434bffa6f063d4bec", cksum);
    
    file = pathOption.getPath("generateMD5-test.txt");

    cksum = PackageBuilder.generateMD5(file);
    System.out.println(cksum);
    Assert.assertEquals("95bcd2d5a06b5f63b84551ddd8ec1483", cksum);
  }

	@Test
	public void testChangeMilestone() throws NoSuchAlgorithmException, FileNotFoundException, IOException, JAXBException, StoppedByUserException {
		File rootDir = pathOption.getPath("generateMilestone-Test");

		PackageBuilder packageBuilder = new PackageBuilder();
		
		File file = packageBuilder.generateChangeMilestone(rootDir, true);
		
		
		String expectedResult = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" + 
				"<resources>\n" + 
				"    <info-resource>\n" + 
				"        <md5>1ea64c493f5278ec6ee5aaa7a35c77f6</md5>\n" + 
				"        <relativePath>testGenerate/md5.txt</relativePath>\n" + 
				"    </info-resource>\n" + 
				"    <info-resource>\n" + 
				"        <md5>80c28c189a32e6e60f9e43010bb10a9e</md5>\n" + 
				"        <relativePath>testGenerate/md5_no2.txt</relativePath>\n" + 
				"    </info-resource>\n" + 
				"    <info-resource>\n" + 
				"        <md5>1ea64c493f5278ec6ee5aaa7a35c77f6</md5>\n" + 
				"        <relativePath>testIteration/dir1/md5.txt</relativePath>\n" + 
				"    </info-resource>\n" + 
				"    <info-resource>\n" + 
				"        <md5>521304ca436443d97ccf68ee919c03b3</md5>\n" + 
				"        <relativePath>testIteration/dir1/md5_no2.txt</relativePath>\n" + 
				"    </info-resource>\n" + 
				"    <info-resource>\n" + 
				"        <md5>55047487acf9f525244b12cff4bfc49c</md5>\n" + 
				"        <relativePath>testIteration/dir2/md5.txt</relativePath>\n" + 
				"    </info-resource>\n" + 
				"    <info-resource>\n" + 
				"        <md5>5c24a78aec732e9626a4a7114efd98b1</md5>\n" + 
				"        <relativePath>testIteration/dir2/md5_no2.txt</relativePath>\n" + 
				"    </info-resource>\n" + 
				"</resources>\n" + 
				"";
		
		String actualResult = IOUtils.toString(new FileInputStream(new File(file.getPath())), "utf-8");
		
		Assert.assertEquals(expectedResult, actualResult);
	}

}
