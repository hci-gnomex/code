package hci.gnomex.utility;

import hci.framework.model.DetailObject;
import hci.gnomex.constants.Constants;
import hci.gnomex.model.PropertyDictionary;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.hibernate.Session;


public class FileDescriptor extends DetailObject implements Serializable {

  private static final double    KB = Math.pow(2, 10);
  private static final double    MB = Math.pow(2, 20);
  private static final double    GB = Math.pow(2, 30);


  private String    displayName;
  private String    requestNumber;
  private long     fileSize;
  private String    fileName;
  private Date      lastModifyDate;
  private String    type;
  private String    zipEntryName;
  private String    directoryName;
  private String    flowCellIndicator;
  private String    absolutePath;
  private String    simpleName;
  private List      children = new ArrayList();
  
  private boolean   found = false;
  
  public FileDescriptor() {    
  }
  
  public FileDescriptor(String requestNumber, String displayName, File file, String zipEntryName) {
    this.requestNumber = requestNumber;
    this.displayName = displayName;
    this.absolutePath = file.getAbsolutePath();
    this.simpleName = file.getName();

    this.fileSize = file.length();
    this.lastModifyDate  = new Date(file.lastModified());
    try {
      this.fileName = file.getCanonicalPath();   
      this.fileName = this.fileName.replaceAll("\\\\", "/");
    } catch (Exception e) {
      System.err.println("IO Exception occurred when trying to get absolute path for file " + file.toString());
      this.fileName = file.getAbsolutePath().replaceAll("\\", "/");
    }
    this.zipEntryName = zipEntryName;
    
    String ext = "";
    String[] fileParts = file.getName().split("\\.");
    if (fileParts != null && fileParts.length >= 2) {
      ext = fileParts[fileParts.length - 1];
    }
    type = ext;
    
    
    
    
  }
  
  public String getDisplayName() {
    return displayName;
  }
  
  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }
  

  public String getFileSizeText() {
    
    long theFileSize = getFileSize();

    
    long size = 0;
    String sizeTxt = "";
    if (theFileSize > GB ) {
      size = Math.round(theFileSize / GB);
      sizeTxt = size + " " + " gb";
    }  else if (theFileSize > MB ) {
      size = Math.round(theFileSize / MB);
      sizeTxt = size + " " + " mb";
    } else if (theFileSize > KB ) {
      size = Math.round(theFileSize / KB);
      sizeTxt = size + " " + " kb";
    } else {
      sizeTxt = theFileSize + " b";
    }
    return sizeTxt;

  }
  
  public long getChildFileSize() {
    
    if (isDirectory()) {
      long total = 0;
      for(Iterator i = children.iterator(); i.hasNext();) {
        FileDescriptor fd = (FileDescriptor)i.next();
        total += fd.getChildFileSize();            
      }      
      return total;
      
    } else {
      return fileSize;
    }
    
  }
  
  public long getFileSize() {
    
    if (isDirectory()) {
      long theFileSize = 0;
      theFileSize = this.getChildFileSize();
      return theFileSize;
    } else {      
      return fileSize;
    }
  }

  public String getType() {
    return type;
  }
  
  public java.util.Date getLastModifyDate() {
    return lastModifyDate;
  }
  
  public String getFileName() {
    return fileName;
  }
  
  public String getZipEntryName() {
    return zipEntryName;
  }

  
  public String getRequestNumber() {
    return requestNumber;
  }
  
  public String getNumber() {
    return getRequestNumber();
  }

  
  public void setRequestNumber(String requestNumber) {
    this.requestNumber = requestNumber;
  }


  public void setFileSize(long fileSize) {
    this.fileSize = fileSize;
  }

  
  public void setFileName(String fileName) {
    this.fileName = fileName;
  }

  
  public void setLastModifyDate(Date lastModifyDate) {
    this.lastModifyDate = lastModifyDate;
  }

  
  public void setType(String type) {
    this.type = type;
  }

  
  public void setZipEntryName(String zipEntryName) {
    this.zipEntryName = zipEntryName;
  }
  
  public String getMainFolderName(Session sess, String serverName, Integer idCoreFacility) {
    if (fileName != null && !fileName.equals("")) {
      return PropertyDictionaryHelper.getInstance(sess).parseMainFolderName(serverName, fileName, idCoreFacility);
    } else {
      return "";
    }
  }

  
  public List getChildren() {
    return children;
  }

  
  public void setChildren(List children) {
    this.children = children;
  }

  
  
  public String getLastModifyDateDisplay() {
    if (this.lastModifyDate != null) {
      return this.formatDate(this.lastModifyDate, DATE_OUTPUT_SQL);
    } else {
      return "";
    }
  }
  

  public String getIsSelected() {
    return "false";
  }

  
  public String getDirectoryName() {
    return directoryName;
  }

  
  public void setDirectoryName(String directoryName) {
    this.directoryName = directoryName;
  }

  
  public String getFlowCellIndicator() {
    return flowCellIndicator;
  }

  
  public void setFlowCellIndicator(String flowCellIndicator) {
    this.flowCellIndicator = flowCellIndicator;
  }
  
  private Boolean isDirectory() {
    return (this.type != null && this.type.equals("dir"));
  }
  
  public String getViewURL() {
    String viewURL = "";
    String dirParm = this.getDirectoryName() != null && !this.getDirectoryName().equals("") ? "&dir=" + this.getDirectoryName() : "";
    dirParm.replace("/", "&#47;");
    if (!isDirectory()) {
      Boolean found = false;
      for(String ext : Constants.FILE_EXTENSIONS_FOR_VIEW) {
        if (this.fileName.toLowerCase().endsWith(ext)) {
          found = true;
          break;
        }
      }
      if (found) {
        Double maxSize =  Math.pow(2, 20) * 50;
        try {
          maxSize = Math.pow(2, 20) * Double.parseDouble(PropertyDictionaryHelper.getInstance(null).getProperty(PropertyDictionary.FILE_MAX_VIEWABLE_SIZE));
        } catch(Exception ex) {
        }
          
        if (this.fileSize < maxSize) {   // Only allow viewing for files under specified max MB     
            viewURL = Constants.DOWNLOAD_SINGLE_FILE_SERVLET + "?requestNumber=" + requestNumber + "&fileName=" + this.getDisplayName() + "&view=Y" + dirParm;    
        }
      }
    }
    return viewURL;
  }

  public void isFound(boolean isFound) {
    this.found = isFound;
  }
  
  public boolean isFound() {
    return this.found;
  }

  public void registerMethodsToExcludeFromXML() {
    this.excludeMethodFromXML("getCreateDate");
  }
}
