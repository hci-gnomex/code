package hci.gnomex.controller;

import hci.framework.control.Command;
import hci.framework.control.RollBackCommandException;
import hci.gnomex.constants.Constants;
import hci.gnomex.model.AnalysisFile;
import hci.gnomex.model.DataTrack;
import hci.gnomex.model.DataTrackFile;
import hci.gnomex.model.GenomeBuild;
import hci.gnomex.model.PropertyDictionary;
import hci.gnomex.model.UCSCLinkFiles;
import hci.gnomex.utility.DataTrackUtil;
import hci.gnomex.utility.PropertyDictionaryHelper;

import java.io.File;
import java.io.Serializable;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import javax.naming.NamingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.hibernate.Session;




public class MakeDataTrackUCSCLinks extends GNomExCommand implements Serializable {

	private static org.apache.log4j.Logger log = org.apache.log4j.Logger.getLogger(MakeDataTrackUCSCLinks.class);

	private Integer idDataTrack;
	private String baseURL;
	private String baseDir;
	private String analysisBaseDir;
	private String serverName;
	private String dataTrackFileServerURL;
	private String dataTrackFileServerWebContext;
	private Integer idAnalysisFile;

	public static final Pattern TO_STRIP = Pattern.compile("\\n");


	private static boolean autoConvertUSeqArchives = true;


	public void validate() {
	}

	public void loadCommand(HttpServletRequest request, HttpSession session) {
		if (request.getParameter("idDataTrack") != null && !request.getParameter("idDataTrack").equals("")) {
			idDataTrack = new Integer(request.getParameter("idDataTrack"));   
		} else {
			this.addInvalidField("idDataTrack", "idDataTrack is required");
		}

	    // if idAnalysisFile is a parameter we will need to figure out idDataTrack
	    idAnalysisFile = null;
	    if (request.getParameter("idAnalysisFile") != null && !request.getParameter("idAnalysisFile").equals("")) {
	    	idAnalysisFile = new Integer(request.getParameter("idAnalysisFile"));   
	      }    
		
		serverName = request.getServerName();
	}

	public Command execute() throws RollBackCommandException {

		try {


			Session sess = this.getSecAdvisor().getHibernateSession(this.getUsername());
			baseDir = PropertyDictionaryHelper.getInstance(sess).getDataTrackDirectory(serverName);
			analysisBaseDir = PropertyDictionaryHelper.getInstance(sess).getAnalysisDirectory(serverName);
			dataTrackFileServerURL = PropertyDictionaryHelper.getInstance(sess).getProperty(PropertyDictionary.DATATRACK_FILESERVER_URL);
			dataTrackFileServerWebContext = PropertyDictionaryHelper.getInstance(sess).getProperty(PropertyDictionary.DATATRACK_FILESERVER_WEB_CONTEXT);

			// We have to serve files from Tomcat, so use das2 base url
			baseURL =  dataTrackFileServerURL;

		    // do we need to figure out idDataTrack?
		    if (idAnalysisFile != null) {
		      idDataTrack = getidDataTrack (idAnalysisFile,sess);
		    }
			
			DataTrack dataTrack = DataTrack.class.cast(sess.load(DataTrack.class, idDataTrack));

			if (this.getSecAdvisor().canRead(dataTrack)) {


				//make links fetching url(s)
				ArrayList<String>  urlsToLoad = makeUCSCLink(sess);
				String url1 = urlsToLoad.get(0);
				String url2 = "";
				if (urlsToLoad.size() == 2) url2 = urlsToLoad.get(1);

				//post results with link urls
				this.xmlResult = "<SUCCESS ucscURL1=\"" +  url1 + "\" ucscURL2=\"" +  url2 + "\"" + "/>";
				setResponsePage(this.SUCCESS_JSP);

			} else {
				this.addInvalidField("insufficient permission", "Insufficient permission to access data track");
			}

		}catch (NamingException e){
			log.error("An exception has occurred in MakeDataTrackUCSCLinks ", e);
			e.printStackTrace(System.out);
			throw new RollBackCommandException(e.getMessage());
		}catch (SQLException e) {
			log.error("An exception has occurred in MakeDataTrackUCSCLinks ", e);
			e.printStackTrace(System.out);
			throw new RollBackCommandException(e.getMessage());
		} catch (Exception e) {
			log.error("An exception has occurred in MakeDataTrackUCSCLinks ", e);
			e.printStackTrace(System.out);
			throw new RollBackCommandException(e.getMessage());
		} finally {
			try {
				this.getSecAdvisor().closeHibernateSession();        
			} catch(Exception e) {

			}
		}

		return this;
	}

	private ArrayList<String>  makeUCSCLink(Session sess) throws Exception {

		ArrayList<String> urlsToLoad = new ArrayList<String>();
		// What is the users preferred ucsc url?
		String ucscUrl = this.getSecAdvisor().getUserUcscUrl();

		//load dataTrack
		DataTrack dataTrack = DataTrack.class.cast(sess.load(DataTrack.class, idDataTrack));      

		//check genome has UCSC name
		GenomeBuild gv = GenomeBuild.class.cast(sess.load(GenomeBuild.class, dataTrack.getIdGenomeBuild()));
		String ucscGenomeBuildName = gv.getUcscName();
		if (ucscGenomeBuildName == null || ucscGenomeBuildName.length() ==0) throw new Exception ("Missing UCSC Genome Version name, update, and resubmit.");

		List<File> dataTrackFiles = dataTrack.getFiles(baseDir, analysisBaseDir);

		//check if dataTrack has exportable file type (xxx.bam, xxx.bai, xxx.bw, xxx.bb, xxx.vcf.gz, xxx.vcf.gz.tbi, xxx.useq (will be converted if autoConvert is true))
		UCSCLinkFiles link = DataTrackUtil.fetchUCSCLinkFiles(dataTrackFiles, GNomExFrontController.getWebContextPath());
		if (link == null) {
		  throw new Exception ("No files to link?!");
		}
		File[] filesToLink = link.getFilesToLink();
		if (filesToLink == null) {
		  throw new Exception ("No files to link?!");
		}

		// When new .bw/.bb files are created, add analysis files and then link via data
		// track file to the data track.
		registerDataTrackFiles(sess, analysisBaseDir, dataTrack, filesToLink);

		//look and or make directory to hold softlinks to data, also removes old softlinks
		File urlLinkDir = DataTrackUtil.checkUCSCLinkDirectory(baseURL, dataTrackFileServerWebContext);
		
		String linkPath = this.checkForUserFolderExistence(urlLinkDir, username);
	  	
		if (linkPath == null) {
			linkPath = UUID.randomUUID().toString() + username;
		}

		//if (randomWord.length() > 6) randomWord = randomWord.substring(0, 6) +"_"+gv.getDas2Name();
		//if (ucscGenomeBuildName != null && ucscGenomeBuildName.length() !=0) randomWord = randomWord+"_"+ ucscGenomeBuildName;

		//Create the users' data directory
		File dir = new File(urlLinkDir.getAbsoluteFile(),linkPath);
		if (!dir.exists())
			dir.mkdir();

		//what data type (bam, bigBed, bigWig, vcfTabix)
		String type = "type=" + DataTrackUtil.fetchUCSCDataType (filesToLink);

		//is there a summary?
		String summary = dataTrack.getSummary();
		if (summary !=null && summary.trim().length() !=0) {
			summary = Constants.HTML_BRACKETS.matcher(summary).replaceAll("");
			summary = "description=\""+summary+"\"";
		}
		else summary = "";

		//TODO: color indicated? look for property named color, convert to RGB, comma delimited and set 'color='

		
		//for each file, there might be two for xxx.bam and xxx.bai files, xxx.vcf.gz and xxx.vcf.gz.tbi, possibly two for converted useq files, plus/minus strands, otherwise just one.
		String customHttpLink = null;
		String toEncode = null;
		for (File f: filesToLink){
			File annoFile = new File(dir, DataTrackUtil.stripBadURLChars(f.getName(), "_"));
			String annoString = annoFile.toString();

			//make soft link
			DataTrackUtil.makeSoftLinkViaUNIXCommandLine(f, annoFile);

			//is it a bam index xxx.bai or vcf index? If so then skip after making soft link.
			if (annoString.endsWith(".bai") || annoString.endsWith(".vcf.gz.tbi")) continue;

			//stranded?
			String strand = "";
			if (link.isStranded()){
				if (annoString.endsWith("_Plus.bw")) strand = " + ";
				else if (annoString.endsWith("_Minus.bw")) strand = " - ";
				else throw new Exception ("\nCan't determine strand of bw file? "+annoString);
			}

			String datasetName = "name=\""+dataTrack.getName()+ strand +" "+dataTrack.getFileName()+"\"";

			//make bigData URL e.g. bigDataUrl=http://genome.ucsc.edu/goldenPath/help/examples/bigBedExample.bb
			int index = annoString.indexOf(Constants.URL_LINK_DIR_NAME);
			String annoPartialPath = annoString.substring(index);
			String bigDataUrl = "bigDataUrl="+ baseURL + annoPartialPath;

			//make final html link
			customHttpLink = ucscUrl + "/cgi-bin/hgTracks?db=" + ucscGenomeBuildName + "&hgct_customText=track+visibility=full+";
			toEncode = type +" "+ datasetName +" "+ summary +" "+ bigDataUrl;

			//System.out.println("LinkForLoading "+customHttpLink + toEncode);
			//System.out.println(customHttpLink+ GeneralUtils.URLEncode(toEncode)+"\n");

			//remove any returns
			toEncode = TO_STRIP.matcher(toEncode).replaceAll(" ");

			urlsToLoad.add(customHttpLink + URLEncoder.encode(toEncode, "UTF-8"));
		}
		return urlsToLoad;

	}
	
	private String checkForUserFolderExistence(File igvLinkDir, String username) throws Exception{
		File[] directoryList = igvLinkDir.listFiles();
		
		String desiredDirectory = null;
		
		for (File directory: directoryList) {
			if (directory.getName().length() > 36) {
				String parsedUsername = directory.getName().substring(36);
				if (parsedUsername.equals(username)) {
					desiredDirectory = directory.getName();
				}
			} 
		}
		
		return desiredDirectory;
	}


	public static void registerDataTrackFiles(Session sess, String analysisBaseDir, DataTrack dataTrack, File[] filesToLink) throws Exception {
		// We need to create an AnalysisFile and DataTrackFile in the db for newly converted files
		// Only do this if we have linked to an existing analysis file
		if (dataTrack.getDataTrackFiles() != null && dataTrack.getDataTrackFiles().size() > 0) {
			// First get the analysis file to clone; this should be the .useq file already
			// linked to the data track.
			AnalysisFile analysisFileToClone = null;
			for (DataTrackFile dtFile1 : (Set<DataTrackFile>)dataTrack.getDataTrackFiles()) {
				analysisFileToClone = dtFile1.getAnalysisFile();
				break;
			}
			// Now we will look at each of the converted files.  If there isn't an existing
			// data track file that points back to it (an analysis file), then create it
			// and link it to the data track.
			if (analysisFileToClone != null) {
				for (int x = 0; x < filesToLink.length; x++) {
					File f = filesToLink[x];
					boolean found = false;
					for (DataTrackFile dtFile1 : (Set<DataTrackFile>)dataTrack.getDataTrackFiles()) {
						String fileName = dtFile1.getAssociatedFilePath(analysisBaseDir);
						File dtf = new File(fileName);
						if (dtf.equals(f)) {
							found = true;
							analysisFileToClone = dtFile1.getAnalysisFile();
							break;
						}
					}
					if (!found) {

						AnalysisFile af = new AnalysisFile();
						af.setIdAnalysis(analysisFileToClone.getIdAnalysis());
						af.setBaseFilePath(analysisFileToClone.getBaseFilePath());
						af.setQualifiedFilePath(analysisFileToClone.getQualifiedFilePath());
						af.setFileName(f.getName());
						af.setFileSize(new BigDecimal(f.length()));

						sess.save(af);

						DataTrackFile dtf = new DataTrackFile();
						dtf.setIdAnalysisFile(af.getIdAnalysisFile());
						dtf.setIdDataTrack(dataTrack.getIdDataTrack());
						sess.save(dtf);
					}        
				}
			}
		}

	}

	  public static int getidDataTrack(int idAnalysisFile, Session sess) {
//		  System.out.println ("[MakeDataTrackUCSCLinks:getidDataTrack] ** starting ** idAnalysisFile: " + idAnalysisFile);
		  

		  int idDataTrack = -1;
		  
		    StringBuffer buf = new StringBuffer("SELECT idDataTrack from DataTrackFile where idAnalysisFile = " + idAnalysisFile);
		    List results = sess.createQuery(buf.toString()).list();

		    if (results.size() > 0) {
		      idDataTrack = (Integer)results.get(0);
		    }
		    
//		    System.out.println ("[MakeDataTrackUCSCLinks:getidDataTrack] ** leaving ** idDataTrack: " + idDataTrack);
		    return idDataTrack;
		  }
	
}