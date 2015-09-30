package hci.gnomex.controller;

import hci.framework.control.Command;
import hci.framework.control.RollBackCommandException;
import hci.gnomex.constants.Constants;
import hci.gnomex.model.Analysis;
import hci.gnomex.model.AnalysisFile;
import hci.gnomex.model.GenomeBuild;
import hci.gnomex.model.Lab;
import hci.gnomex.security.SecurityAdvisor;
import hci.gnomex.utility.AnalysisFileParser;
import hci.gnomex.utility.BatchDataSource;
import hci.gnomex.utility.HibernateSession;

import java.io.Serializable;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.hibernate.Session;
import org.hibernate.Transaction;
import org.jdom.Document;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;

import hci.gnomex.model.DataTrack;
import hci.gnomex.model.DataTrackFile;
import hci.gnomex.model.DataTrackFolder;
import hci.gnomex.model.Institution;
import hci.gnomex.model.PropertyDictionary;
import hci.gnomex.utility.DataTrackComparator;
import hci.gnomex.utility.DataTrackUtil;
import hci.gnomex.utility.DictionaryHelper;
import hci.gnomex.utility.PropertyDictionaryHelper;
import hci.gnomex.utility.RequestParser;

import java.io.File;
import java.util.Date;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Level;
import org.hibernate.Hibernate;

/**
 * Given a list of analysis files, this routine creates data tracks for 
 * all of the appropriate files.  The data track folder structure mirrors
 * the analysis folder structure. 
 * 
 * Most of this code was stolen from CreateDataTracks
 * 
 */

public class CreateAllDataTracks extends GNomExCommand implements Serializable {
   
  // the static field for logging in Log4J
  private static org.apache.log4j.Logger log = org.apache.log4j.Logger.getLogger(CreateAllDataTracks.class);
  
  private String                baseDir;
  
  private String                analysisFilesXMLString;
  private Document              analysisFilesDoc;
  private AnalysisFileParser    analysisFileParser;

  private Integer                idAnalysis;
  
  private String                serverName;
  
  private Integer idLab;
  private Integer idGenomeBuild;
  private Integer idUser;

  private Transaction tx;
  private Session sess;

  private String baseDirDataTrack = null;
  private String baseDirAnalysis = null;

  
  
  public void validate() {
  }
  
  public void loadCommand(HttpServletRequest request, HttpSession session) {
    
    if (request.getParameter("idAnalysis") != null && !request.getParameter("idAnalysis").equals("")) {
      idAnalysis = Integer.valueOf(request.getParameter("idAnalysis"));
    } else {
      this.addInvalidField("idAnalysis", "idAnalysis is required.");
    }

    serverName = request.getServerName();
        
  }

  public Command execute() throws RollBackCommandException {
    
    try {
      sess = HibernateSession.currentSession(this.getUsername());
      Analysis analysis = (Analysis)sess.load(Analysis.class, idAnalysis);      
      
      // get what we need from the database
      String analysisName = analysis.getName();

      //lab
	  idLab = analysis.getIdLab();
	  Lab lab = analysis.getLab();
	  String labName = lab.getName();
	  
	  //genome build
	  Set<GenomeBuild> gbs = analysis.getGenomeBuilds();
	  GenomeBuild gb = gbs.iterator().next();
	  idGenomeBuild = gb.getIdGenomeBuild(); //Just pull the first one, should only be one.
	  
	  //fileList
	  ArrayList<AnalysisFile> fileList = new ArrayList<AnalysisFile>(analysis.getFiles());
	  ArrayList<AnalysisFile> bamFiles = new ArrayList<AnalysisFile>();
	  ArrayList<AnalysisFile> covFiles = new ArrayList<AnalysisFile>();
	  ArrayList<AnalysisFile> vcfFiles = new ArrayList<AnalysisFile>();
	  
	  //folderNames
	  for (AnalysisFile af: fileList) {
		  String afBaseFileName = fetchBaseName(af.getFileName(), Constants.DATATRACK_FILE_EXTENSIONS);

			  String afFileNameUpperCase = af.getFileName().toUpperCase();
				  if (afFileNameUpperCase.endsWith(".BAM")) {
						bamFiles.add(af);
				  } else if (afFileNameUpperCase.endsWith(".USEQ") || afFileNameUpperCase.endsWith(".USEQ")) {
						covFiles.add(af);
				  } else if (afFileNameUpperCase.endsWith(".VCF.GZ")) {
						vcfFiles.add(af);
				  }
	  }
	  
	  // if we found any files to make data tracks for, do the work
	  if (bamFiles.size() > 0 || covFiles.size() > 0 || vcfFiles.size() > 0) {
		  
    	  /*************************************
    	   * Create Subfolder structure
    	   **************************************/
//    	  System.out.println("Locating the root directory");
    	  // find the root folder
    	  DataTrackFolder rootFolder = gb.getRootDataTrackFolder();
    	      
		  if (rootFolder == null) {
			  System.out.println("Warning: Could not find the root data track folder for genome: " + gb.getGenomeBuildName());
		  }
		  
		  ArrayList<DataTrackFolder> existingFolders = new ArrayList<DataTrackFolder>(rootFolder.getFolders());
		  
		 
		  // Setup directory structure
		  ArrayList<String> toCreate = new ArrayList<String>();
		  
    	  toCreate.add(labName);
    	  toCreate.add(analysisName);
    	      	  
//    	  System.out.println("Creating directories");
    	  
    	  // Create Directories if they don't already exist
    	  Integer parentId = rootFolder.getIdDataTrackFolder();
    	  boolean isNew = false;
    	  for (String dir: toCreate) {
    		  if (!isNew) { // If we might find an existing folder
//    			  System.out.println("\tTesting if new");
    			  boolean exists = false;
    			  for (DataTrackFolder dtf: existingFolders) {
        			  if (dtf.getName().equals(dir)) {
//        				  System.out.println("\tNope, this directory exists " + dir);
        				  exists = true;
        				  existingFolders = new ArrayList<DataTrackFolder>(dtf.getFolders());
        				  parentId = dtf.getIdDataTrackFolder();
        				  break;
        			  }
        		  } // end of for
        		 
        		  if (!exists) {
//        			  System.out.println("\tDirectory didn't exist, lets create it " + dir);
        			  parentId = this.createDataTrackFolders(dir, parentId);
        			  isNew = true;
        		  }
    		  } else { // We are in new folder territory.
//    			  System.out.println("\tNew territory, creating " + dir);
    			  parentId = this.createDataTrackFolders(dir, parentId);
    			  isNew = true;
    		  }    		  
    	  } // end of for
		  		  
		       
	  /*************************************
	   * Create DataTracks
	   **************************************/	  
	  //Create directory and datatracks for each type
//	  System.out.println("Creating bam");
	  this.createDataTrackDriver("bam", parentId, bamFiles,existingFolders);
//	  System.out.println("Creating useq");
	  this.createDataTrackDriver("useq",parentId,covFiles,existingFolders);
//	  System.out.println("Creating vcf");
	  this.createDataTrackDriver("vcf",parentId,vcfFiles,existingFolders);
	  
//        sess.flush();
          
        this.xmlResult = "<SUCCESS idAnalysis=\"" + analysis.getIdAnalysis() + "\"" +  " idAnalysisGroup=\"" + "\"" + "/>";
      
        setResponsePage(this.SUCCESS_JSP);
        
	  } // end of if we had some files of interest        
   } catch (Exception e){
      log.error("An exception has occurred in CreateAllDataTracks ", e);
      e.printStackTrace();
      throw new RollBackCommandException(e.getMessage());        
    } finally {
      try {
        HibernateSession.closeSession();        
      } catch(Exception e) {
        
      }      
    }
  
    return this;
  }
  
	/******************************
	 * Create Datatracks
	 ****************************/
	private void createDataTrackDriver(String folderName, Integer parentId, ArrayList<AnalysisFile> filesToLink, ArrayList<DataTrackFolder> existing) {

//		System.out.println ("[createDataTrackDriver] folderName: " + folderName + " parentId: " + parentId + " fileToLink.size(): " + filesToLink.size() + " existing.size(): " + existing.size());
		
		//Create directory and datatracks for each type
	  if (filesToLink.size() != 0) {
		  Integer subId = null;
		  boolean exists = false;
		  for (DataTrackFolder dtf: existing) {
			if (dtf.getName().equals(folderName)) {
				exists = true;
				subId = dtf.getIdDataTrackFolder();
//				System.out.println ("[createDataTrackDriver] exists subId: " + subId + " dtf.getName(): " + dtf.getName());
				break;
			}
		  } // end of for
		  
		  if (!exists) {
			 subId = this.createDataTrackFolders(folderName,parentId);
//			 System.out.println ("[createDataTrackDriver] did not exist, new subId: " + subId);
		  }
		 
		  for (AnalysisFile af: filesToLink) {
//			  System.out.println ("[createDataTrackDriver] calling createDataTracks analysisfile: " + af.getFileName() + " idAnalysisFile: " + af.getIdAnalysisFile() + " subId: " + subId);
			  this.createDataTracks(af.getIdAnalysisFile(),subId);
		  }
	  }
	}
	
	/*******************************
	 * Stolen from linkDataTrackFiles. 
	 *********************************/
	private void createDataTracks(Integer idAnalysisFile, Integer idDataTrackFolder) {
//		System.out.println ("[createDataTracks] ** starting ** idAnalysisFile: " + idAnalysisFile + " idDataTrackFolder: " + idDataTrackFolder);
		DataTrack dataTrack = null;
		AnalysisFile analysisFile = null;
		
		// check and see if this data track already exists
		
      
		try {
			PropertyDictionaryHelper propertyHelper = PropertyDictionaryHelper.getInstance(sess);
			baseDirDataTrack = propertyHelper.getDataTrackDirectory(serverName);
			baseDirAnalysis = propertyHelper.getAnalysisDirectory(serverName);

			analysisFile = (AnalysisFile)sess.load(AnalysisFile.class, idAnalysisFile);
			Analysis analysis = (Analysis)sess.load(Analysis.class, analysisFile.getIdAnalysis());

			dataTrack = new DataTrack();
			
//			System.out.println ("[createDataTracks] data track name: " + analysisFile.getAnalysis().getNumber() + "_" + analysisFile.getFileName() );
		
			dataTrack.setName(analysisFile.getAnalysis().getNumber() + "_" + analysisFile.getFileName());
			dataTrack.setIdLab(idLab);
			dataTrack.setIdGenomeBuild(idGenomeBuild);
			

			String defaultVisibility = propertyHelper.getProperty(PropertyDictionary.DEFAULT_VISIBILITY_DATATRACK);
			if (defaultVisibility != null && defaultVisibility.length() > 0) {
				dataTrack.setCodeVisibility(defaultVisibility);
				if(defaultVisibility.compareTo(hci.gnomex.model.Visibility.VISIBLE_TO_INSTITUTION_MEMBERS) == 0) {
					if (dataTrack.getIdLab() != null) {
						Lab lab = (Lab)sess.load(Lab.class, dataTrack.getIdLab());
						Hibernate.initialize(lab.getInstitutions());
						Iterator it = lab.getInstitutions().iterator();
						while(it.hasNext()) {
							Institution thisInst = (Institution) it.next();
							if(thisInst.getIsDefault().compareTo("Y") == 0) {
								dataTrack.setIdInstitution(thisInst.getIdInstitution());            
							}
						}
					}
				}
			} else {
				dataTrack.setCodeVisibility(hci.gnomex.model.Visibility.VISIBLE_TO_GROUP_MEMBERS);
			}                   

			dataTrack.setIdAppUser(analysis.getIdAppUser());
			
			dataTrack.setDataPath(baseDirDataTrack);
			dataTrack.setCreatedBy(this.getUsername());					/////////////////////////////////////////////////////////////////////////
			dataTrack.setCreateDate(new java.sql.Date(System.currentTimeMillis()));
			dataTrack.setIsLoaded("N");

			sess.save(dataTrack);
			
//			System.out.println ("[createDataTracks] data track filename: " + "DT" + dataTrack.getIdDataTrack());
			dataTrack.setFileName("DT" + dataTrack.getIdDataTrack());
			sess.flush();

			// If we are linking a .bw/.bb, .bai/.bam, or .vcf.gz/.vcf.gz.tbi see if we have linked to its pair.
			// If not, fill in idAnalysisFileOther, so that the pair is linked as well.
			Integer idAnalysisFileOther = null;
			
			boolean lookForBam = false;
			boolean lookForBai = false;
			boolean lookForBigWig = false;
			boolean lookForUSeq = false;
			boolean lookForVCF = false;
			boolean lookForVCFTBI = false;

			String baseFileName = fetchBaseName(analysisFile.getFileName(), Constants.DATATRACK_FILE_EXTENSIONS);			

			String fileName = analysisFile.getFileName().toUpperCase();
			if (fileName.endsWith(".BAI")) lookForBam = true;
			else if (fileName.endsWith(".BAM")) lookForBai = true;
			else if (fileName.endsWith(".USEQ")) lookForBigWig = true;
			else if (fileName.endsWith(".BW") || fileName.endsWith(".BB")  ) lookForUSeq = true;
			else if (fileName.endsWith(".VCF.GZ")) lookForVCFTBI = true;
			else if (fileName.endsWith(".VCF.GZ.TBI")) lookForVCF = true;	

			for (Iterator i = analysisFile.getAnalysis().getFiles().iterator(); i.hasNext();) {
				AnalysisFile af = (AnalysisFile)i.next();
				String afBaseFileName = fetchBaseName(af.getFileName(), Constants.DATATRACK_FILE_EXTENSIONS);

				//do the baseNames match?
				String afFileNameUpperCase = af.getFileName().toUpperCase();
				if (baseFileName.toUpperCase().equals(afBaseFileName.toUpperCase())) {						
					if (lookForBai && afFileNameUpperCase.endsWith(".BAI")) {
						idAnalysisFileOther = af.getIdAnalysisFile();
					} else if (lookForBam && afFileNameUpperCase.endsWith(".BAM")) {
						idAnalysisFileOther = af.getIdAnalysisFile();
					} else if (lookForBigWig && (afFileNameUpperCase.endsWith(".BW") || afFileNameUpperCase.endsWith(".BB"))) {
						idAnalysisFileOther = af.getIdAnalysisFile();
					} else if (lookForUSeq && (afFileNameUpperCase.endsWith(".USEQ") || afFileNameUpperCase.endsWith(".USEQ"))) {
						idAnalysisFileOther = af.getIdAnalysisFile();
					} else if (lookForVCFTBI && afFileNameUpperCase.endsWith(".VCF.GZ.TBI")) {
						idAnalysisFileOther = af.getIdAnalysisFile();
					} else if (lookForVCF && afFileNameUpperCase.endsWith(".VCF.GZ")) {
						idAnalysisFileOther = af.getIdAnalysisFile();
					}
				}
			}

			//is it a paired file set? then must have other
			String afFileNameUpper = analysisFile.getFileName().toUpperCase(); 
			boolean saveDataTrack = true;
			if (afFileNameUpper.endsWith(".BAM") || afFileNameUpper.endsWith(".BAI") || afFileNameUpper.endsWith(".VCF.GZ") || afFileNameUpper.endsWith(".VCF.GZ.TBI")){
				if (idAnalysisFileOther == null){
					//not sure if this makes this invalid so using boolean
//					System.out.println("Missing indexed file or file index?!  Please add either a matching xxx.bam or xxx.bai; or add a xxx.vcf.gz or xxx.vcf.gz.tbi.");
					saveDataTrack = false;
				}
			}

			if (saveDataTrack){
				//Create datatrack
				DataTrackFile dtFile = new DataTrackFile();
				dtFile.setIdAnalysisFile(idAnalysisFile);
				dtFile.setIdDataTrack(dataTrack.getIdDataTrack());
//				System.out.println ("[createDataTracks] create datatrackfile, id: " + dataTrack.getIdDataTrack());
				sess.save(dtFile);

				// If this is a file pair, add the other analysis file
				if (idAnalysisFileOther != null) {			
					DataTrackFile dtFileOther = new DataTrackFile();
					dtFileOther.setIdAnalysisFile(idAnalysisFileOther);
					dtFileOther.setIdDataTrack(dataTrack.getIdDataTrack());
//					System.out.println ("[createDataTracks] create datatrackfile other, id: " +idAnalysisFileOther);
					sess.save(dtFileOther);
				}
				sess.flush();
			}

			// *************************************************
			// If this is a new data track, add it to the folder
			// *************************************************
			GenomeBuild genomeBuild = GenomeBuild.class.cast(sess.load(GenomeBuild.class, idGenomeBuild));

			DataTrackFolder folderNew = DataTrackFolder.class.cast(sess.load(DataTrackFolder.class, idDataTrackFolder));
			if (folderNew == null) {
//				System.out.println ("[createDataTracks] WARNING folderNew is null!!!!");
			}
//			System.out.println ("[createDataTracks] folderNew: " + folderNew.getName());
			
            //
            // Add the dataTrack to the dataTrack folder
            //
            Set<DataTrack> newDataTracks = new TreeSet<DataTrack>(new DataTrackComparator());
            if (folderNew.getDataTracks() != null) {
            	for(Iterator<?> i = folderNew.getDataTracks().iterator(); i.hasNext();) {
            		DataTrack a = DataTrack.class.cast(i.next());
            		newDataTracks.add(a);
            	}
            }
            
//            System.out.println ("[createDataTracks] before newDataTracks.size(): " + newDataTracks.size());
            newDataTracks.add(dataTrack);
//			System.out.println ("[createDataTracks] after newDataTracks.size(): " + newDataTracks.size());
            folderNew.setDataTracks(newDataTracks);
            sess.flush();			

		} catch (Exception e){
			System.out.println("An exception has occurred in CreateAllDataTracks: " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	
	/**********************************************
	 * Stolen from SaveDataTrackFolder 
	 *********************************************/
	
	private Integer createDataTrackFolders(String folderName, Integer idParentDataTrackFolder) {
//		System.out.println ("[createDataTrackFolders] ** starting ** folderName: " + folderName);
		
	    DataTrackFolder dataTrackFolder = new DataTrackFolder();
		
	    dataTrackFolder.setCreatedBy(this.getUsername());							////////////////////////////////////////////////////////////////////////
	    dataTrackFolder.setCreateDate(new java.sql.Date(System.currentTimeMillis()));
	    dataTrackFolder.setName(RequestParser.unEscape(folderName));
	    dataTrackFolder.setIdLab(idLab);
	    dataTrackFolder.setIdGenomeBuild(idGenomeBuild);
	    dataTrackFolder.setIdParentDataTrackFolder(idParentDataTrackFolder);
	    
	    DataTrackFolder parentDataTrackFolder = DataTrackFolder.class.cast(sess.load(DataTrackFolder.class,idParentDataTrackFolder));


	    // If parent data track folder is owned by a user group, this
	    // child data track  folder must be as well.
	    if (parentDataTrackFolder.getIdLab() != null) {
	    	if ( idLab == null || !parentDataTrackFolder.getIdLab().equals(idLab)) {
//	    		System.out.println("Folder '" + folderName + "' must belong to lab '" + 
//	    				DictionaryHelper.getInstance(sess).getLabObject(parentDataTrackFolder.getIdLab()).getName() + "'");

	    	}
	    } 
	    
	  try {
		  sess.save(dataTrackFolder);
		  sess.flush();
	  }	  catch (Exception ex) {
		  System.out.println("Could not save datatrackfolder: " + ex.getMessage());
		  ex.getStackTrace();

	  }
	  
//	System.out.println ("[createDataTrackFolders] ** leaving ** idDataTrackFolder: " + dataTrackFolder.getIdDataTrackFolder());  
    return dataTrackFolder.getIdDataTrackFolder();
	}  
	
	
	
	
	/**Removes the extension and its period.  Thus alta.is.great.bam.bai -> alta.is.great.bam .*/
	private String fetchBaseName(String fileName, String[] lowerCaseExtensions){
		int extLength = 0;
		String lower = fileName.toLowerCase();
		for (String ext: lowerCaseExtensions){
			if (lower.endsWith(ext)){
				extLength = ext.length();
				break;
			}
		}
		return fileName.substring(0, fileName.length() - extLength);
	}

}
