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


public class AnalysisFileDescriptor extends DetailObject implements Serializable {
  private static final double    KB = Math.pow(2, 10);
  private static final double    MB = Math.pow(2, 20);
  private static final double    GB = Math.pow(2, 30);


  private String    displayName;
  private String    analysisNumber;
  private long     fileSize;
  private String    fileName;
  private Date      lastModifyDate;
  private String    type;
  private String    zipEntryName;
  private String    qualifiedFilePath;
  private String    baseFilePath;
  private List      children = new ArrayList();
  private Date      uploadDate;
  private String    comments;
  private Integer   idAnalysis;
  private String    idAnalysisFileString;
  private Integer   idLab;
  
  private boolean   found = false;
  
  public AnalysisFileDescriptor() {    
  }
  
  public AnalysisFileDescriptor(String analysisNumber, String displayName, File file, String baseDir) {
    this.analysisNumber = analysisNumber;
    this.displayName = displayName;
    
    this.fileSize = file.length();
    this.lastModifyDate  = new Date(file.lastModified());
    try {
      this.fileName = file.getCanonicalPath();      
    } catch (Exception e) {
      System.err.println("IO Exception occurred when trying to get absolute path for file " + file.toString());
      this.fileName = file.getAbsolutePath().replace("\\", "/");
    }
    this.zipEntryName = PropertyDictionaryHelper.parseAnalysisZipEntryName(baseDir, fileName);  
    
    
    String ext = "";
 
    //first scan DataTrack types, some are xxx.vcf.gz or xxx.vcf.gz.tbi
    //Nix
    for (String t: Constants.DATATRACK_FILE_EXTENSIONS){
    	if (fileName.toLowerCase().endsWith(t)){
    		ext = t.substring(1);
    		//watch out for .bam.bai
    		if (ext.equals("bam.bai")) ext = "bai";
    		break;
    	}
    }
    //any found? if not then do as before
    if (ext.equals("")){
    	String[] fileParts = file.getName().split("\\.");
        if (fileParts != null && fileParts.length >= 2) {
          ext = fileParts[fileParts.length - 1];
        }
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
        AnalysisFileDescriptor fd = (AnalysisFileDescriptor)i.next();
        total += fd.getChildFileSize();            
      }      
      return total;
      
    } else {
      return fileSize;
    }
    
  }
  
  private Boolean isDirectory() {
    return (this.type != null && this.type.equals("dir"));
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

  
  public String getAnalysisNumber() {
    return analysisNumber;
  }
  
  public String getNumber() {
    return getAnalysisNumber();
  }

  
  public void setAnalysisNumber(String analysisNumber) {
    this.analysisNumber = analysisNumber;
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
  

  public String getDirectoryNumber(int analysisFileDirectoryLength) {
    String analysisNumber = "";
    if (fileName != null && !fileName.equals("")) {
      // Get the directory name starting after the year
      String relativePath = fileName.substring(analysisFileDirectoryLength + 5);
      String tokens[] = relativePath.split("/", 2);
      if (tokens == null || tokens.length == 1) {
        tokens = relativePath.split("/", 2);
      }
      if (tokens.length == 2) {
        analysisNumber = tokens[0];
      }
    }
    return analysisNumber;
  }

  
  public List getChildren() {
    return children;
  }

  
  public void setChildren(List children) {
    this.children = children;
  }

  
  public String getQualifiedFilePath() {
    return qualifiedFilePath;
  }

  public void setQualifiedFilePath(String qualifiedFilePath) {
    this.qualifiedFilePath = qualifiedFilePath;
  }

  public String getFilePathName() {
    String fullPathName = "";
    
    if (qualifiedFilePath != null && qualifiedFilePath.length() != 0) {
      fullPathName += getQualifiedFilePath() + "/"; 
    }
    fullPathName += getDisplayName();

//    String fullPath = fullPathName.replaceAll("/", "\\\\");
    String fullPath = fullPathName.replace("\\", "/");
    
//    return fullPath;
    return fullPathName;
  }
  
  public String getQualifiedFileName() {
    String fullPathName = "";
    
    if (qualifiedFilePath != null && qualifiedFilePath.length() != 0) {
      fullPathName += getQualifiedFilePath() + "/"; 
    }
    fullPathName += getDisplayName();

//    String fullPath = fullPathName.replaceAll("/", "\\\\");
    String fullPath = fullPathName.replace("\\", "/");
    
//    return fullPath;
    return fullPathName;
  }
  
  public String getBaseFilePath()
  {
    return baseFilePath;
  }

  public void setBaseFilePath(String baseFilePath)
  {
    this.baseFilePath = baseFilePath;
  }

  public String getLastModifyDateDisplay() {
    if (this.lastModifyDate != null) {
      return this.formatDate(this.lastModifyDate, DATE_OUTPUT_SQL);
    } else {
      return "";
    }
  }
  
  public String getIsUCSCViewerAllowed() {
    boolean found = false;
    if (type != null) {
      String extension = "." + type;
      for (int x = 0; x < Constants.FILE_EXTENSIONS_FOR_UCSC_LINKS_NO_INDEX.length; x++) {
        if (extension.equalsIgnoreCase(Constants.FILE_EXTENSIONS_FOR_UCSC_LINKS_NO_INDEX[x])) {
          found = true;
          break;
        }
      }
      
    }
    return (found ? "Y" : "N");
  }

  public String getIsIGVViewerAllowed() {
	    boolean found = false;
	    if (type != null) {
	      String extension = "." + type;
	      for (int x = 0; x < Constants.FILE_EXTENSIONS_FOR_IGV_LINKS_NO_INDEX.length; x++) {
	        if (extension.equalsIgnoreCase(Constants.FILE_EXTENSIONS_FOR_IGV_LINKS_NO_INDEX[x])) {
	          found = true;
	          break;
	        }
	      }
	      
	    }
	    return (found ? "Y" : "N");
	  }
	  
  public String getIsBAMIOBIOViewerAllowed() {
	    boolean found = false;
	    if (type != null) {
	      String extension = "." + type;
	      for (int x = 0; x < Constants.FILE_EXTENSIONS_FOR_BAMIOBIO_LINKS_NO_INDEX.length; x++) {
	        if (extension.equalsIgnoreCase(Constants.FILE_EXTENSIONS_FOR_BAMIOBIO_LINKS_NO_INDEX[x])) {
	          found = true;
	          break;
	        }
	      }
	      
	    }
	    return (found ? "Y" : "N");
	  }
	  
  public String getIsURLLinkAllowed() {
	    boolean found = false;
	    if (type != null) {
	      String extension = "." + type;
	      for (int x = 0; x < Constants.DATATRACK_FILE_EXTENSIONS_NO_INDEX.length; x++) {
	        if (extension.equalsIgnoreCase(Constants.DATATRACK_FILE_EXTENSIONS_NO_INDEX[x])) {
	          found = true;
	          break;
	        }
	      }
	      
	    }
	    return (found ? "Y" : "N");
	  }
	  
  
  public String getIsSupportedDataTrack() {
	    boolean found = false;
	    if (type != null) {
	      String extension = "." + type;
	      for (int x = 0; x < Constants.DATATRACK_FILE_EXTENSIONS.length; x++) {
	        if (extension.equalsIgnoreCase(Constants.DATATRACK_FILE_EXTENSIONS[x])) {
	          found = true;
	          break;
	        }
	      }
	      
	    }
	    return (found ? "Y" : "N");
	  }
	  

  public String getIsSelected() {
    return "false";
  }

  
  public Date getUploadDate() {
    return uploadDate;
  }

  
  public void setUploadDate(Date uploadDate) {
    this.uploadDate = uploadDate;
  }

  
  public String getComments() {
    return comments;
  }

  
  public void setComments(String comments) {
    this.comments = comments;
  }

  
  public String getIdAnalysisFileString() {
    return idAnalysisFileString;
  }

  
  public void setIdAnalysisFileString(String idAnalysisFileString) {
    this.idAnalysisFileString = idAnalysisFileString;
  }

  
  public Integer getIdAnalysis() {
    return idAnalysis;
  }

  
  public void setIdAnalysis(Integer idAnalysis) {
    this.idAnalysis = idAnalysis;
  }

  
  public void isFound(boolean isFound) {
    this.found = isFound;
  }
  
  public boolean isFound() {
    return this.found;
  }
  
  public Integer getIdLab() {
    return idLab;
  }

  public void setIdLab(Integer idLab) {
    this.idLab = idLab;
  }

  public String getViewURL() {
    String viewURL = "";
    String dirParm = this.getQualifiedFilePath() != null  ? "&dir=" + this.getQualifiedFilePath() : "";
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
            viewURL = Constants.DOWNLOAD_ANALYSIS_SINGLE_FILE_SERVLET + "?idAnalysis=" + idAnalysis + "&fileName=" + this.getDisplayName() + "&view=Y" + dirParm;    
        }
      }
    }
    return viewURL;
  }
  
}
