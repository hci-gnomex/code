package hci.gnomex.daemon;

import hci.gnomex.controller.SaveAnalysis;
import hci.gnomex.daemon.RegisterFiles.AnalysisFileInfo;
import hci.gnomex.model.Analysis;
import hci.gnomex.model.AnalysisFile;
import hci.gnomex.model.AppUser;
import hci.gnomex.model.BillingAccount;
import hci.gnomex.model.BillingPeriod;
import hci.gnomex.model.CoreFacility;
import hci.gnomex.model.DataTrack;
import hci.gnomex.model.DataTrackFile;
import hci.gnomex.model.DiskUsageByMonth;
import hci.gnomex.model.ExperimentFile;
import hci.gnomex.model.Lab;
import hci.gnomex.model.PropertyDictionary;
import hci.gnomex.model.Request;
import hci.gnomex.utility.BatchDataSource;
import hci.gnomex.utility.BatchMailer;
import hci.gnomex.utility.DeleteOldExperimentAndAnalysisFilesEmailFormatter;
import hci.gnomex.utility.DictionaryHelper;
import hci.gnomex.utility.MailUtil;
import hci.gnomex.utility.PropertyDictionaryHelper;

import java.io.File;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import javax.mail.MessagingException;
import javax.naming.NamingException;

import org.apache.log4j.Level;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.TransactionException;

public class DeleteOldExperimentAndAnalysisFiles {

  private static DeleteOldExperimentAndAnalysisFiles  app = null;
  private static BigDecimal               gigabyte = new BigDecimal(1000000000);
  
  // Not making this static from paranoia.  SimpleDateFormat is not thread safe.
  private SimpleDateFormat                ymdFormatter = new SimpleDateFormat("yyyy-MM-dd");

  private String                          serverName = "";
  private String                          orionPath = "";
  private String                          testEmailAddress = "";
  private Boolean                         sendMail = true;
  private Boolean                         deleteFiles = false;
  private Boolean                         sendWarningReports = false;
  private Boolean                         usePreviousMonth = false;

  private Boolean                         diskUsageForExperiments = false;
  private Boolean                         diskUsageForAnalysis = false;
  private Integer                         analysisGracePeriod;
  private Integer                         experimentGracePeriod;
  private BigDecimal                      diskUsageFreeBeforeCharge;

  private String                          gnomexSupportEmail;
  private String                          fromEmailAddress;
  private String                          baseURL = null;
  private String                          baseAnalysisDirectory;
  private String                          baseExperimentDirectory;
  
  private String                          coreFacilityContactName;
  private String                          coreFacilityContactPhone;

  private Date                            runDate;
  private BillingPeriod                   billingPeriod;
  
  private Map<Integer, Lab>               labsForDeletion;
  private Map<Integer, Request>           requestMap;
  private Map<Integer, Analysis>          analysisMap;
  private Map<Integer, TreeMap<String, Object>> fileDeletionMap;
  private Map<Integer, DiskUsageByMonth>  diskUsageMap;
  private Map<Integer, TreeMap<String, Object>> filesActuallyDeletedMap;

  private String                          errorMessagePrefixString = "Error in DeleteOldExperimentAndAnalysisFiles";
  private String                          processingStatement;

  Map<String, Map<String, String>> emailAnalysisMap = new HashMap<String, Map<String, String>>();
  Map<String, List<AnalysisFileInfo>> analysisFileMap = new HashMap<String, List<AnalysisFileInfo>>();

  private BatchDataSource                 dataSource;
  private Session                         sess;
  private Transaction                     tx;
  
  private List<String>                    errorList;
  
  private Properties                      mailProps;

  public DeleteOldExperimentAndAnalysisFiles(String[] args) {
    Boolean err = false;
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals ("-server")) {
        serverName = args[++i];
      } else if (args[i].equals ("-gnomexServerURL")) {
        baseURL = args[++i];
      } else if (args[i].equals ("-orionPath")) {
        orionPath = args[++i];
      } else if (args[i].equals ("-doNotSendMail")) {
        sendMail = false;
      } else if (args[i].equals ("-testEmailAddress")) {
        testEmailAddress = args[++i];
      } else if (args[i].equals("-deleteFiles")) {
        deleteFiles = true;
      } else if (args[i].equals("-sendWarningReports")) {
        sendWarningReports = true;
      } else if (args[i].equals("-usePreviousMonth")) {
        usePreviousMonth = true;
      } else if (args[i].equals("-help")) {
        showHelp();
        System.exit(0);
      } else {
        printError("Unexpected switch encountered: " + args[i]);
        err = true;
      }
    }
    if ((!deleteFiles && !sendWarningReports) || (deleteFiles && sendWarningReports)) {
      String msg = "You must specify either -deleteFiles or -sendWarningReports, but you cannot specify both.";
      printError(msg);
      System.out.println(msg);
      err = true;
    }
    
    if (err) {
      printError("Run aborted due to previous errors");
      sendErrorReport();
      System.exit(0);
    }
  }

  private void showHelp() {
    System.out.println("This program runs in two modes.  It either deletes old experiment and analysis files or sends reports to lab managers showing files that will be deleted.");
    System.out.println("The 'disk_usage_for_analysis' and 'disk_usage_for_experiments' properties control if deletion is done for the core facility.");
    System.out.println("File age for deletion is determined by the 'disk_usage_analysis_grace_period_in_months' and 'disk_usage_experiment_grace_period_in_months' properties.");
    System.out.println("The date age is calculated from is the end date of the current month when doing reporting.  When deleting it uses the end date of the previous month if ");
    System.out.println("run in the first 27 days, otherwise it uses the end date of the current month.");
    System.out.println("Files are only deleted for external labs and labs with no active billing accounts.  QC files are not deleted.");
    System.out.println("Switches:");
    System.out.println("   -deleteFiles - run program in deletion mode.");
    System.out.println("   -sendWarningReports - run program in warning mode.");
    System.out.println("   -usePreviousMonth - Force program to use end date of previous month when calculating file ages.  Overrides default behaviour.");
    System.out.println("   -server - server for Hibernate connection.");
    System.out.println("   -orionPath - path to Orion directory to get mail properties.");
    System.out.println("   -doNotSendEmail - used for debugging if no email server available.  Emails are created but not sent.");
    System.out.println("   -testEmailAddress - Overrides all lab emails with this email address.  Used for testing.");
    System.out.println("   -help - gives this message.  Note no other processing is performed if the -help switch is specified.");
  }
  
  /**
   * @param args
   */
  public static void main(String[] args) {
    app  = new DeleteOldExperimentAndAnalysisFiles(args);
    app.run();
  }


  public void run(){
    Calendar calendar = Calendar.getInstance();
    errorMessagePrefixString += " on " + new SimpleDateFormat("MM-dd-yyyy_HH:mm:ss").format(calendar.getTime()) + "\n";

    try {
      org.apache.log4j.Logger log = org.apache.log4j.Logger.getLogger("org.hibernate");
      log.setLevel(Level.ERROR);

      dataSource = new BatchDataSource();
      app.connect();

      if (sendMail) {
        mailProps = new BatchMailer(orionPath).getMailProperties();
      }
      
      Query coreQuery = sess.createQuery("select c from CoreFacility c");
      List coreFacilities = coreQuery.list();
      
      for(Iterator i = coreFacilities.iterator(); i.hasNext(); ) {
        CoreFacility f = (CoreFacility)i.next();
        
        app.initialize(f);
        if (diskUsageForExperiments || diskUsageForAnalysis) {
          System.out.println("\n" + new Date() + " " + this.processingStatement);
          errorList = new ArrayList<String>();
          if (deleteFiles) {
            app.doFileDeletion(f);
          } else {
            app.doWarningReports(f);
          }
          sendErrorReport();
        }
      }

    } catch (Exception e) {
            
      this.sendErrorReport(e);

    } finally {
      if (sess != null) {
        try {
          app.disconnect();
        } catch(Exception e) {
          printError( "DeleteOldExperimentAndAnalysisFiles unable to disconnect from hibernate session.   " + e.toString() );
        }
      }
    }
    System.out.println("   " + new Date() + " processing complete");
  }
  
  private void doFileDeletion(CoreFacility facility) {
    buildFileMaps(facility);
    
    filesActuallyDeletedMap = new HashMap<Integer, TreeMap<String, Object>>();
    Integer numLabs = 0;
    Integer numFiles = 0;
    Integer numDeleteErrors = 0;
    for(Integer idLab : labsForDeletion.keySet()) {
      Lab lab = labsForDeletion.get(idLab);
      TreeMap<String, Object> filesToDelete = this.fileDeletionMap.get(idLab);
      if (filesToDelete != null && !filesToDelete.isEmpty()) {
        numLabs++;
        for(String key : filesToDelete.keySet()) {
          Object file = filesToDelete.get(key);
          Boolean success = false;
          if (file instanceof AnalysisFile) {
            success = deleteFile(lab, (AnalysisFile)file);
          } else {
            success = deleteFile(lab, (ExperimentFile)file);
          }
          if (success) {
            addDeletionFile(filesActuallyDeletedMap, idLab, key, file);
            numFiles++;
          } else {
            numDeleteErrors++;
          }
          if (numDeleteErrors >= 100) {
            printError("100 errors reached.  Run aborted.");
            break;
          }
        }
      }
    }
    
    sendWarningReportEmails(facility, filesActuallyDeletedMap, false);
    this.sendNotifyEmails();
    
    System.out.println("   Deleted " + numFiles.toString() + " files for " + numLabs.toString() + " labs.  " + numDeleteErrors.toString() + " files skipped due to errors.");
  }
  
  private boolean deleteFile(Lab lab, AnalysisFile file) {
    boolean success = true;
    tx = sess.beginTransaction();
    try {
      Analysis analysis = (Analysis)sess.load(Analysis.class, file.getIdAnalysis());

      // delete data track files if any.
      deleteDataTracksForAnalysisFile(analysis, file);
      
      // If the analysis has any comments, warn the app user that it was deleted.
      if (file.getComments() != null && !file.getComments().trim().equals("")) {
        hashForNotification(analysis, file, null);
      }

      // delete the analysis
      for(Iterator i = analysis.getFiles().iterator(); i.hasNext(); ) {
        AnalysisFile f = (AnalysisFile)i.next();
        if (f.getIdAnalysisFile().equals(file.getIdAnalysisFile())) {
          analysis.getFiles().remove(f);
          break;
        }
      }
      
      sess.flush();
      
      // Delete the file on disk
      String filePath = file.getFullPathName();
      File f = new File(filePath);
      if (!f.delete()) {
        printError("Unable to delete file " + filePath + " for Analysis " + analysis.getNumber() + " and lab " + lab.getFormattedLabName() + ".");
        success = false;
      }
    } catch(Exception ex) {
      Analysis analysis = analysisMap.get(file.getIdAnalysis());
      printError("Unable to delete file " + file.getFullPathName() + " for Analysis " + analysis.getNumber() + " and lab " + lab.getFormattedLabName() + ". Exception: " + ex.getMessage());
      success = false;
    }

    if (success) {
      tx.commit();
    } else {
      tx.rollback();
    }
    
    return success;
  }
  
  private void deleteDataTracksForAnalysisFile(Analysis analysis, AnalysisFile file) {
    StringBuffer queryBuf = new StringBuffer();
    queryBuf.append("SELECT dtf, dt FROM DataTrack dt ");
    queryBuf.append("JOIN dt.dataTrackFiles dtf ");
    queryBuf.append("WHERE dtf.idAnalysisFile = ");
    queryBuf.append( file.getIdAnalysisFile() );
    
    // Run the Query
    List dataTrackFiles = sess.createQuery(queryBuf.toString()).list();
    
    // Data tracks were found, so delete them and warn the owners
    if ( dataTrackFiles.size() > 0 ) {
      
      // Delete the DataTrackFiles and DataTracks 
      for (Iterator i4 = dataTrackFiles.iterator(); i4.hasNext();) {
        Object[] row = (Object[]) i4.next();
        DataTrackFile dtf = (DataTrackFile) row[0];
        DataTrack dt = (DataTrack) row[1];
        
        hashForNotification(analysis, file, dt);
        
        sess.delete( dtf );
        sess.flush();
      }
    }
  }

  private void hashForNotification(Analysis a, AnalysisFile af, DataTrack dt) {
    String emailAddress = getEmailAddress(a, af, dt);
    
    Map<String, String> analysisNumbers = emailAnalysisMap.get(emailAddress);
    if (analysisNumbers == null) {
      analysisNumbers = new TreeMap<String, String>();
      emailAnalysisMap.put(emailAddress, analysisNumbers);
    }
    analysisNumbers.put(af.getAnalysis().getNumber(), null);
    emailAnalysisMap.put(emailAddress, analysisNumbers);
    
    List fileInfos = analysisFileMap.get(af.getAnalysis().getNumber());
    if (fileInfos == null) {
      fileInfos = new ArrayList<AnalysisFileInfo>();
      analysisFileMap.put(af.getAnalysis().getNumber(), fileInfos);
    }
    fileInfos.add(new AnalysisFileInfo(af.getAnalysis().getNumber(), 
        af.getQualifiedFileName() + "/" + af.getFileName(), 
        dt, af.getComments()));
    
    
  }
  
  private String getEmailAddress(Analysis a, AnalysisFile af, DataTrack dt) {
    AppUser user = null;
    Lab lab = null;
    if (dt != null) {
      user = dt.getAppUser();
      lab = dt.getLab();
    } else {
      user = a.getAppUser();
      lab = a.getLab();
    }
    String emailAddress = "";
    if ( user != null && user.getEmail() != null ) {
      emailAddress = user.getEmail();
    } 
    if ( emailAddress == null || emailAddress.equals( "" ) ) {
      if ( lab != null && lab.getContactEmail() != null ) {
        emailAddress = lab.getContactEmail();
      }
    }
    if (emailAddress == null ||  emailAddress.equals( "" ) ) {
      emailAddress = this.gnomexSupportEmail;
    }
    
    return emailAddress;
  }

  private boolean deleteFile(Lab lab, ExperimentFile file) {
    boolean success = true;
    try {
      tx = sess.beginTransaction();
      Request request = (Request)sess.load(Request.class, file.getIdRequest());
      for(Iterator i = request.getFiles().iterator(); i.hasNext(); ) {
        ExperimentFile f = (ExperimentFile)i.next();
        if (f.getIdExperimentFile().equals(file.getIdExperimentFile())) {
          request.getFiles().remove(f);
          break;
        }
      }
      sess.flush();
      
      String filePath = this.baseExperimentDirectory + request.getCreateYear() + "/" + file.getFileName();
      File f = new File(filePath);
      if (!f.delete()) {
        printError("Unable to delete file " + filePath + " for Request " + request.getNumber() + " and lab " + lab.getFormattedLabName() + ".");
        success = false;
      }
    } catch(Exception ex) {
      Request request = requestMap.get(file.getIdRequest());
      printError("Unable to delete file " + file.getFileName() + " for Request " + request.getNumber() + " and lab " + lab.getFormattedLabName() + ".  Exception: " + ex.getMessage());
      success = false;
    }

    if (success) {
      tx.commit();
    } else {
      tx.rollback();
    }
    
    return success;
  }
  
  private void doWarningReports(CoreFacility facility) {
    buildFileMaps(facility);
    
    sendWarningReportEmails(facility, fileDeletionMap, true);
  }
  
  private void sendWarningReportEmails(CoreFacility facility, Map<Integer, TreeMap<String, Object>> deletionMap, boolean forWarning) {
    Integer numLabs = 0;
    for(Integer idLab : labsForDeletion.keySet()) {
      Lab lab = labsForDeletion.get(idLab);
      TreeMap<String, Object> files = null;
      files = deletionMap.get(idLab);
      if (files != null && !files.isEmpty()) {
        sendEmail(lab, files, forWarning);
        numLabs++;
      }
    }
    
    System.out.println("   Sent emails to " + numLabs.toString() + " labs.");
  }
  
  private void sendEmail(Lab lab, TreeMap<String, Object> filesToDelete, boolean forWarning) {
    String emailAddress = lab.getContactEmail();
    if (testEmailAddress.length() > 0) {
      emailAddress = testEmailAddress;
    }
    if (emailAddress == null || emailAddress.length() == 0) {
      emailAddress = this.gnomexSupportEmail;
    }

    List<AnalysisFile> analysisFiles = new ArrayList<AnalysisFile>();
    List<ExperimentFile> experimentFiles = new ArrayList<ExperimentFile>();
    for(String key : filesToDelete.keySet()) {
      Object file = filesToDelete.get(key);
      if (file instanceof AnalysisFile) {
        analysisFiles.add((AnalysisFile)file);
      } else {
        experimentFiles.add((ExperimentFile)file);
      }
    }
    
    DeleteOldExperimentAndAnalysisFilesEmailFormatter formatter = new DeleteOldExperimentAndAnalysisFilesEmailFormatter(lab, analysisMap, analysisFiles, 
      requestMap, experimentFiles, billingPeriod, this.coreFacilityContactName, this.coreFacilityContactPhone, baseURL, forWarning);
    String subject = formatter.getSubject();
    String body = formatter.format();
    
    if (sendMail) {
      try {
        MailUtil.send( mailProps,
            emailAddress,
            gnomexSupportEmail,
            fromEmailAddress,
            subject,
            body,
            true
          );
      } catch(Exception ex) {
        printError("Unable to send email to lab " + lab.getFormattedLabName() + " with email " + emailAddress + " because of exception: " + ex.getMessage());
      }
    }

  }
  
  private void buildFileMaps(CoreFacility facility) {
    this.fileDeletionMap = new HashMap<Integer, TreeMap<String, Object>>();
    mapAnalysisFilesToDelete(facility);
    mapExperimentFilesToDelete(facility);
    trimFileMapToMaxDisk(facility);
  }
  
  private void mapAnalysisFilesToDelete(CoreFacility facility) {
    // Determine latest date for files to delete
    Calendar deleteDateCal = Calendar.getInstance();
    deleteDateCal.setTime(billingPeriod.getEndDate());
    deleteDateCal.add(Calendar.MONTH, analysisGracePeriod * -1);

    String queryString = "select a, af "
        + " from Analysis a "
        + " join a.files af "
        + " where case when af.uploadDate is null then case when af.createDate is null then '3999-12-31' else af.createDate end else af.uploadDate end <= :deleteDate"
        + "     and a.idLab in (:labIds)"
        + " order by a.idLab, a.number, af.idAnalysisFile";
    Query query = sess.createQuery(queryString);
    query.setDate("deleteDate", deleteDateCal.getTime());
    query.setParameterList("labIds", labsForDeletion.keySet());
    List files = query.list();
    
    analysisMap = new HashMap<Integer, Analysis>();
    for(Iterator i = files.iterator(); i.hasNext(); ) {
      Object[] row = (Object[])i.next();
      Analysis analysis = (Analysis)row[0];
      AnalysisFile file = (AnalysisFile)row[1];
      Integer idLab = analysis.getIdLab();
      
      analysisMap.put(analysis.getIdAnalysis(), analysis);
      
      addDeletionFile(fileDeletionMap, idLab, buildFileSortKey(file.getEffectiveCreateDate(), "A", file.getIdAnalysisFile()), file);
    }
  }
  
  private void mapExperimentFilesToDelete(CoreFacility facility) {
    Calendar deleteDateCal = Calendar.getInstance();
    deleteDateCal.setTime(billingPeriod.getEndDate());
    deleteDateCal.add(Calendar.MONTH, experimentGracePeriod * -1);
    
    String queryString = "select distinct r, ef "
        + " from Request r "
        + " join r.requestCategory rc"
        + " join r.files ef "
        + " where rc.idCoreFacility = :idCoreFacility and case when ef.createDate is null then '3999-12-31' else ef.createDate end <= :deleteDate"
        + "     and rc.refrainFromAutoDelete is not null and rc.refrainFromAutoDelete != 'Y'"
        + "     and r.idLab in (:labIds)"
        + " order by r.idLab, r.number, ef.idExperimentFile ";
    Query query = sess.createQuery(queryString);
    query.setDate("deleteDate", deleteDateCal.getTime());
    query.setInteger("idCoreFacility", facility.getIdCoreFacility());
    query.setParameterList("labIds", labsForDeletion.keySet());
    List files = query.list();
    
    requestMap = new HashMap<Integer, Request>();
    for(Iterator i = files.iterator(); i.hasNext(); ) {
      Object[] row = (Object[])i.next();
      Request req = (Request)row[0];
      Integer idLab = req.getIdLab();
      ExperimentFile file = (ExperimentFile)row[1];
      
      requestMap.put(req.getIdRequest(), req);

      addDeletionFile(fileDeletionMap, idLab, buildFileSortKey(file.getCreateDate(), "F", file.getIdExperimentFile()), file);
    }
  }
  
  private void trimFileMapToMaxDisk(CoreFacility facility) {
    for(Integer idLab : labsForDeletion.keySet()) {
      Lab lab = labsForDeletion.get(idLab);
      if (!lab.isExternalLab()) {
        TreeMap<String, Object> files = this.fileDeletionMap.get(idLab);
        TreeMap<String, Object> newFiles = new TreeMap<String, Object>();
        DiskUsageByMonth usage = this.diskUsageMap.get(idLab);
        if (files != null && !files.isEmpty()) {
          BigDecimal spaceRemaining = usage.getAssessedDiskSpace();
          for(String key : files.keySet()) {
            Object file = files.get(key);
            BigDecimal fileSize = null;
            if (file instanceof AnalysisFile) {
              fileSize = ((AnalysisFile)file).getFileSize();
            }
            if (file instanceof ExperimentFile) {
              fileSize = ((ExperimentFile)file).getFileSize();
            }
            if (spaceRemaining.compareTo(this.diskUsageFreeBeforeCharge) > 0) {
              newFiles.put(key, file);
              spaceRemaining = spaceRemaining.subtract(fileSize);
            }
          }
        }
        fileDeletionMap.put(idLab, newFiles);
      }
    }
  }
  
  private void addDeletionFile(Map<Integer, TreeMap<String, Object>> map, Integer idLab, String sortKey, Object file) {
    TreeMap<String, Object> filesForDeletion = map.get(idLab);
    if (filesForDeletion == null) {
      filesForDeletion = new TreeMap<String, Object>();
      map.put(idLab, filesForDeletion);
    }
    filesForDeletion.put(sortKey, file);
  }

  private String buildFileSortKey(Date sortDate, String objectType, Integer id) {
    StringBuffer buf = new StringBuffer();
   
    if (sortDate == null) {
      buf.append("3999-12-31"); // should never happen -- weaned out in the query.
    } else {
      buf.append(ymdFormatter.format(sortDate));
    }
    buf.append("-");
    buf.append(objectType);
    buf.append("-");
    Integer leadingId = 1000000000 + id;
    buf.append(leadingId.toString());
    
    return buf.toString();
  }
  
  private void mapLabsForDeletion(CoreFacility facility) {
    // find active billing accounts
    Map<Integer, BillingAccount> billingAccountMap = new HashMap<Integer, BillingAccount>();
    String queryString = "select ba from BillingAccount ba where (startDate is null OR :date >= startDate) and (expirationDate is null OR :date <= expirationDate) and isApproved='Y'";
    Query query = sess.createQuery(queryString);
    query.setDate("date", billingPeriod.getEndDate());
    List accounts = query.list();
    for(Iterator i = accounts.iterator(); i.hasNext(); ) {
      BillingAccount account = (BillingAccount)i.next();
      billingAccountMap.put(account.getIdLab(), account);
    }
    
    // get disk usage
    diskUsageMap = ComputeMonthlyDiskUsage.getDiskUsage(diskUsageForExperiments, diskUsageForAnalysis, sess, 
        billingPeriod, experimentGracePeriod, analysisGracePeriod, facility);
    
    // find candidate labs
    labsForDeletion = new HashMap<Integer, Lab>();
    queryString = "select distinct lab from Lab lab join lab.coreFacilities core where core.idCoreFacility = :idCoreFacility";
    query = sess.createQuery(queryString);
    query.setParameter("idCoreFacility", facility.getIdCoreFacility());
    List candidateLabs = query.list();
    for(Iterator i = candidateLabs.iterator(); i.hasNext(); ) {
      Lab lab = (Lab)i.next();
      Boolean labToDelete = false;
      if (lab.isExternalLab()) {
        labToDelete = true;
      } else if (!billingAccountMap.containsKey(lab.getIdLab())) {
        DiskUsageByMonth dsk = diskUsageMap.get(lab.getIdLab());
        if (dsk != null && dsk.getAssessedDiskSpace().compareTo(diskUsageFreeBeforeCharge) > 0) { 
          labToDelete = true;
        }
      }
      if (labToDelete) {
        labsForDeletion.put(lab.getIdLab(), lab);
      }
    }
  }

  private void sendNotifyEmails() {
    for ( Iterator i = this.emailAnalysisMap.keySet().iterator(); i.hasNext();) {
      String emailAddress = (String) i.next();
      Map<String, String> analysisNumbers = emailAnalysisMap.get(emailAddress);

      StringBuffer body = new StringBuffer();
      body.append("The following analysis files have been removed from the file system and the database.  Please remove the associated data tracks.");
      for (String analysisNumber : analysisNumbers.keySet()) {
        body.append("\n\nAnalysis " + analysisNumber);
        List<AnalysisFileInfo> fileInfos = this.analysisFileMap.get(analysisNumber);
        for (AnalysisFileInfo info : fileInfos) {
          body.append("\n\t" + info.analysisFileName + "\t" + info.comment + "\t" + info.dataTrackNumber);
        }
      }
      
      if (sendMail) {
        try {
          MailUtil.send( mailProps,
              emailAddress,
              null,
              gnomexSupportEmail,
              "GNomEx Analysis file(s) removed from file system",
              body.toString(),
              false
            );
        } catch(Exception ex) {
          printError("Unable to send analysis notification email to email address " + emailAddress + " because of exception: " + ex.getMessage());
        }
      }
    }
  }

  private void initialize(CoreFacility facility) throws Exception {
    PropertyDictionaryHelper ph = PropertyDictionaryHelper.getInstance(sess);
    String diskUsageForExperimentsString = ph.getQualifiedCoreFacilityProperty(PropertyDictionary.DISK_USAGE_FOR_EXPERIMENTS, serverName, facility.getIdCoreFacility());
    String diskUsageForAnalysisString = ph.getQualifiedCoreFacilityProperty(PropertyDictionary.DISK_USAGE_FOR_ANALYSIS, serverName, facility.getIdCoreFacility());
    String analysisGracePeriodString = ph.getQualifiedCoreFacilityProperty(PropertyDictionary.DISK_USAGE_ANALYSIS_GRACE_PERIOD_IN_MONTHS, serverName, facility.getIdCoreFacility());
    String experimentGracePeriodString = ph.getQualifiedCoreFacilityProperty(PropertyDictionary.DISK_USAGE_EXPERIMENT_GRACE_PERIOD_IN_MONTHS, serverName, facility.getIdCoreFacility());
    String diskUsageFreeBeforeChargeString = ph.getQualifiedCoreFacilityProperty(PropertyDictionary.DISK_USAGE_FREE_GB, serverName, facility.getIdCoreFacility());
    if (diskUsageForExperimentsString != null && diskUsageForExperimentsString.equals("Y")) {
      diskUsageForExperiments = true;
    } else {
      diskUsageForExperiments = false;
    }
    if (diskUsageForAnalysisString != null && diskUsageForAnalysisString.equals("Y")) {
      diskUsageForAnalysis = true;
    } else {
      diskUsageForAnalysis = false;
    }
    if (analysisGracePeriodString != null) {
      analysisGracePeriod = Integer.parseInt(analysisGracePeriodString);
    } else {
      analysisGracePeriod = 1;
    }
    if (experimentGracePeriodString != null) {
      experimentGracePeriod = Integer.parseInt(experimentGracePeriodString);
    } else {
      experimentGracePeriod = 3;
    }
    if (diskUsageFreeBeforeChargeString != null) {
      diskUsageFreeBeforeCharge = new BigDecimal(diskUsageFreeBeforeChargeString.replaceAll(",", ""));
      diskUsageFreeBeforeCharge = diskUsageFreeBeforeCharge.multiply(gigabyte); // property in gigabytes.
    } else {
      diskUsageFreeBeforeCharge = new BigDecimal("100000000000");
    }

    // Set todays date
    Calendar calendar = Calendar.getInstance();
    runDate = calendar.getTime();
 
    gnomexSupportEmail = ph.getProperty(PropertyDictionary.GNOMEX_SUPPORT_EMAIL);
    if (gnomexSupportEmail == null) {
      gnomexSupportEmail = ph.getProperty(PropertyDictionary.CONTACT_EMAIL_SOFTWARE_TESTER);
    }
    this.fromEmailAddress = ph.getProperty(PropertyDictionary.GENERIC_NO_REPLY_EMAIL);
    
    emailAnalysisMap = new HashMap<String, Map<String, String>>();
    analysisFileMap = new HashMap<String, List<AnalysisFileInfo>>();

    this.coreFacilityContactName = ph.getCoreFacilityProperty(facility.getIdCoreFacility(), PropertyDictionary.CONTACT_NAME_CORE_FACILITY);
    this.coreFacilityContactPhone = ph.getCoreFacilityProperty(facility.getIdCoreFacility(), PropertyDictionary.CONTACT_PHONE_CORE_FACILITY);
    
    this.baseAnalysisDirectory = ph.getAnalysisDirectory(serverName);
    if (!baseAnalysisDirectory.endsWith("/") && !baseAnalysisDirectory.endsWith("\\")) {
      baseAnalysisDirectory += "/";
    }
    this.baseExperimentDirectory = ph.getExperimentDirectory(serverName, facility.getIdCoreFacility());
    if (!baseExperimentDirectory.endsWith("/") && !baseExperimentDirectory.endsWith("\\")) {
      baseExperimentDirectory += "/";
    }

    if (baseURL == null) {
      baseURL = (serverName.equals("localhost")  || serverName.equals("h005973") ? "http://" : "https://") + serverName + "/gnomex/";
    }
    if (!baseURL.endsWith("/")) {
      baseURL += "/";
    }

    if (usePreviousMonth) {
      // Explicitly asked to compute for the previous month.
      calendar.add(Calendar.MONTH, -1);
    } else if (deleteFiles) {
      if (calendar.get(Calendar.DAY_OF_MONTH) <= 27) {
        calendar.add(Calendar.MONTH, -1);
      }
    }
    String computeDateString = new SimpleDateFormat("yyyy-MM-dd").format(calendar.getTime());
    Query billingPeriodQuery = sess.createQuery("select b from BillingPeriod b where '" + computeDateString + "' between b.startDate and b.endDate");
    billingPeriod = (BillingPeriod)billingPeriodQuery.uniqueResult();
    
    this.mapLabsForDeletion(facility);
    
    processingStatement = "";
    if (!diskUsageForExperiments && !diskUsageForAnalysis) {
       processingStatement += "Core Facility " + facility.getFacilityName() + " skipped.";
    } else  {
      if (this.deleteFiles) {
        processingStatement += "Deleting files for ";
      } else {
        processingStatement += "Creating deletion reports for ";
      }
      processingStatement += facility.getFacilityName();
      if (diskUsageForExperiments && diskUsageForAnalysis) {
        processingStatement += " for experiment and analysis files.";
      } else if (diskUsageForExperiments) {
        processingStatement += " for experiment files only.";
      } else if (diskUsageForAnalysis) {
        processingStatement += " for analysis files only.";
      }
    }
  }

  private String getDisplayServerName() {
    String localServerName = "unknown";
    if (serverName != null && serverName.length() > 0) {
      localServerName = serverName;
    }
    return localServerName;
  }
  
  private void sendErrorReport(Exception e)  {
    String msg = "DeleteOldExperimentAndAnalysisFiles could not process on server " + getDisplayServerName() + ". Transaction rolled back:   " + e.toString() + "\n\t";
    
    StackTraceElement[] stack = e.getStackTrace();
    for (StackTraceElement s : stack) {
      msg = msg + s.toString() + "\n\t\t";
    }
    
    try {
      if (tx != null) {
        tx.rollback();
      }
    }
    catch(TransactionException te) {
      msg += "\nTransactionException: " + te.getMessage() + "\n\t";
      stack = te.getStackTrace();
      for (StackTraceElement s : stack) {
        msg = msg + s.toString() + "\n\t\t";
      }
    } finally {}
    
    String errorMessageString = errorMessagePrefixString;
    if ( !errorMessageString.equals( "" )) {
      errorMessageString += "\n";
    }
    errorMessageString += msg;
    
    sendErrorReport(errorMessageString);
  }
  
  private void sendErrorReport() {
    if (errorList.size() > 0) {
      String msg = "DeleteOldExperimentAndAnalysisFiles process finished on server " + getDisplayServerName() + " but had following errors:\n\t";
      for(String line : errorList) {
        msg += line + "\n\t";
      }
      sendErrorReport(msg);
    }
  }
  
  private void sendErrorReport(String errorMessageString) {
    printError(errorMessageString);
    
    String toAddress = gnomexSupportEmail;
    if (testEmailAddress.length() > 0) {
      toAddress = testEmailAddress;
    }
    try {
      if (sendMail) {
        MailUtil.send( mailProps,
          toAddress,
          null,
          fromEmailAddress,
          "ComputeMonthlyDiskUsage Error",
          errorMessageString,
          false
        );
      }
    } catch (Exception e1) {
      printError( "DeleteOldExperimentAndAnalysisFiles unable to send error report.   " + e1.toString() );
    }
  }

  private void connect()
  throws Exception
  {
    sess = dataSource.connect();
  }
  
  private void disconnect() 
  throws Exception {
    sess.close();
  }
  
  private void printError(String message) {
    String msg = "\n" + new Date() + " " + message;
    System.err.println(msg);
    errorList.add(msg);
  }
}
