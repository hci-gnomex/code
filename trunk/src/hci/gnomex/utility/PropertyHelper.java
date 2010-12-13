package hci.gnomex.utility;

import hci.gnomex.model.Property;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.hibernate.Session;



public class PropertyHelper implements Serializable {
  private static PropertyHelper   theInstance;
  
  private Map                      propertyMap = new HashMap();

  private static final String    PROPERTY_PRODUCTION_SERVER                   = "production_server";
  
  private static final String    PROPERTY_EXPERIMENT_DIRECTORY                = "experiment_directory";
  private static final String    PROPERTY_EXPERIMENT_TEST_DIRECTORY           = "experiment_test_directory";
  private static final String    PROPERTY_ANALYSIS_DIRECTORY                  = "analysis_directory";
  private static final String    PROPERTY_ANALYSIS_TEST_DIRECTORY             = "analysis_test_directory";
  private static final String    PROPERTY_FLOWCELL_DIRECTORY                  = "flowcell_directory";
  private static final String    PROPERTY_FLOWCELL_TEST_DIRECTORY             = "flowcell_test_directory";

  
  public PropertyHelper() {    
  }
  
  public static synchronized PropertyHelper getInstance(Session sess) {
    if (theInstance == null) {
      theInstance = new PropertyHelper();
      theInstance.loadProperties(sess);
    }
    return theInstance;
    
  }
  
  public static synchronized PropertyHelper reload(Session sess) {
    theInstance = new PropertyHelper();
    theInstance.loadProperties(sess);  
    return theInstance;
    
  }
  
  
  private void loadProperties(Session sess)  {
    List properties = sess.createQuery("select p from Property as p").list();
    for (Iterator i = properties.iterator(); i.hasNext();) {
      Property prop = (Property)i.next();
      propertyMap.put(prop.getPropertyName(), prop.getPropertyValue());
    }   
  }
  
  

  public String getProperty(String name) {
    String propertyValue = "";
    if (name != null && !name.equals("")) {
      return (String)propertyMap.get(name);
    } else {
      return "";
    }
  }
  
  public boolean isProductionServer(String serverName) {
    if (this.getProperty(PROPERTY_PRODUCTION_SERVER) != null &&
        this.getProperty(PROPERTY_PRODUCTION_SERVER).contains(serverName)) {
      return true;
    } else {
      return false;
    }
  }
  
  public String getAnalysisDirectory(String serverName) {
    String property = "";
    String propertyName = null;
    
    // If this is the production server, first try to get property that is 
    // qualified by server name.  If that isn't found, get the property without
    // any qualification.
    // If this is not the production server, get the property for the analysis
    // test path.  First use the property qualified by server name.  If
    // it isn't found, get the property without any qualification.   
    if (isProductionServer(serverName)) {
      propertyName = PROPERTY_ANALYSIS_DIRECTORY + "_" + serverName;
      property = this.getProperty(propertyName);
      if (property == null || property.equals("")) {  
        propertyName = PROPERTY_ANALYSIS_DIRECTORY;
        property = this.getProperty(propertyName);
      }
    } else {
      propertyName = PROPERTY_ANALYSIS_TEST_DIRECTORY + "_" + serverName;
      property = this.getProperty(propertyName);
      if (property == null || property.equals("")) {  
        propertyName = PROPERTY_ANALYSIS_TEST_DIRECTORY;
        property = this.getProperty(propertyName);
      }
    }
    
    return property;
  }
  
  public String getFlowCellDirectory(String serverName) {
    String property = "";
    String propertyName = null;
    
    // If this is the production server, first try to get property that is 
    // qualified by server name.  If that isn't found, get the property without
    // any qualification.
    // If this is not the production server, get the property for the flowcell
    // test path.  First use the property qualified by server name.  If
    // it isn't found, get the property without any qualification.   
    if (isProductionServer(serverName)) {
      propertyName = PROPERTY_FLOWCELL_DIRECTORY + "_" + serverName;
      property = this.getProperty(propertyName);
      if (property == null || property.equals("")) {  
        propertyName = PROPERTY_FLOWCELL_DIRECTORY;
        property = this.getProperty(propertyName);
      }
    } else {
      propertyName = PROPERTY_FLOWCELL_TEST_DIRECTORY + "_" + serverName;
      property = this.getProperty(propertyName);
      if (property == null || property.equals("")) {  
        propertyName = PROPERTY_FLOWCELL_TEST_DIRECTORY;
        property = this.getProperty(propertyName);
      }
    }
    
    return property;
  }

  public String getMicroarrayDirectoryForWriting(String serverName) {
    String property = "";
    String propertyName = null;
    
    // If this is the production server, first try to get property that is 
    // qualified by server name.  If that isn't found, get the property without
    // any qualification.
    // If this is not the production server, get the property for the experiment
    // test path.  First use the property qualified by server name.  If
    // it isn't found, get the property without any qualification.   
    if (isProductionServer(serverName)) {
      propertyName = PROPERTY_EXPERIMENT_DIRECTORY + "_" + serverName;
      property = this.getProperty(propertyName);
      if (property == null || property.equals("")) {  
        propertyName = PROPERTY_EXPERIMENT_DIRECTORY;
        property = this.getProperty(propertyName);
      }
    } else {
      propertyName = PROPERTY_EXPERIMENT_TEST_DIRECTORY + "_" + serverName;
      property = this.getProperty(propertyName);
      if (property == null || property.equals("")) {  
        propertyName = PROPERTY_EXPERIMENT_TEST_DIRECTORY;
        property = this.getProperty(propertyName);
      }
    }
    
    return property;
  }

  public  String getMicroarrayDirectoryForReading(String serverName) {
    // First try to get property that is 
    // qualified by server name.  If that isn't found, get the property without
    // any qualification.
    String property = "";
    String propertyName = PROPERTY_EXPERIMENT_DIRECTORY + "_" + serverName;
    property = this.getProperty(propertyName);
    if (property == null || property.equals("")) {  
      propertyName = PROPERTY_EXPERIMENT_DIRECTORY;
      property = this.getProperty(propertyName);
    }
    return property;
     
  }
  
   
  public String parseMainFolderName(String serverName, String fileName) {
    String mainFolderName = "";
    String baseDir = "";
    
    String experimentDirectory = this.getMicroarrayDirectoryForReading(serverName);
    String flowCellDirectory   = this.getFlowCellDirectory(serverName);
    
    if (fileName.toLowerCase().indexOf(experimentDirectory.toLowerCase()) >= 0) {
      baseDir = experimentDirectory;
    } else if (fileName.toLowerCase().indexOf(flowCellDirectory.toLowerCase()) >= 0) {
      baseDir = flowCellDirectory;
    } else {
      throw new RuntimeException("Cannot determine base directory.  Neither flowcell directory or experiment directory match file name " + fileName);
    }
    
    
    // Strip off the leading part of the path, up through the year subdirectory,
    // to leave only the path that starts with the request number subdirectory.
    String relativePath = fileName.substring(baseDir.length() + (baseDir.endsWith("/") | baseDir.endsWith("\\") ? 5 : 6));
    
    String tokens[] = relativePath.split("/", 2);
    if (tokens == null || tokens.length == 1) {
      tokens = relativePath.split("\\\\", 2);
    }
    if (tokens.length == 2) {
      mainFolderName = tokens[0];
    }
    
    return mainFolderName;
  }
  
  
}
