package com.oxygenxml.translation.support.core;


import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import com.oxygenxml.translation.support.storage.ResourceInfo;
import com.oxygenxml.translation.support.util.ArchiveBuilder;
import com.oxygenxml.translation.ui.PackResult;

/**
 * Tests the creation of the package that is sent to translation. 
 */
public class PackagingTest {
	File rootDir = TestUtil.getPath("packageZip-Test");
	File tempDir = new File(rootDir.getParentFile(), "tempZip");
	
	/**
	 * We create a zip with all the modified files of a directory
	 * Then unzip it and compare the relative paths of the modified files
	 * @throws Exception 
	 */
	@Test
	public void testPackageUnzip() throws Exception {
		File packageLocation = new File(tempDir, "changedFiles.zip");
		
		/* 
		 * Generate the milestone for rootDir.
		 */
		ArrayList<ResourceInfo> modifiedResources = new ArrayList<ResourceInfo>();
		modifiedResources.add(new ResourceInfo("testGenerate/newAdded.txt"));
		modifiedResources.add(new ResourceInfo("testIteration/dir1/md5.txt"));
		PackResult nr = new PackResult();
		ChangePackageGenerator packageBuilder = new ChangePackageGenerator();
		nr = packageBuilder.generateChangedFilesPackage(rootDir, packageLocation, modifiedResources, true);

		
		ArrayList<String> actualResults = new ArchiveBuilder().unzipDirectory(packageLocation , tempDir, true);

		ArrayList<String> expectedResults = new ArrayList<String>();
		expectedResults.add("testGenerate/newAdded.txt");
		expectedResults.add("testIteration/dir1/md5.txt");
		
		Assert.assertEquals(expectedResults, actualResults);
		System.out.println(actualResults);
		System.out.println(nr.getNumber() + " files were modified.");
		
	}
	
	//Delete the "temp" dir
	@After
	public void deleteTempDir() throws IOException{
		FileUtils.deleteDirectory(tempDir);
	}

}
