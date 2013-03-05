package hci.gnomex.controller;

import hci.dictionary.utility.DictionaryManager;
import hci.framework.control.Command;
import hci.framework.control.RollBackCommandException;
import hci.framework.security.UnknownPermissionException;
import hci.gnomex.model.AppUser;
import hci.gnomex.model.Lab;
import hci.gnomex.model.Organism;
import hci.gnomex.model.ProjectExperimentReportFilter;
import hci.gnomex.security.SecurityAdvisor;
import hci.gnomex.utility.DictionaryHelper;
import hci.report.constants.ReportFormats;
import hci.report.model.Column;
import hci.report.model.ReportRow;
import hci.report.model.ReportTray;
import hci.report.utility.ReportCommand;

import java.io.Serializable;
import java.util.Date;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.naming.NamingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.hibernate.Session;


public class ShowProjectExperimentReport extends ReportCommand implements Serializable {
  
  private static org.apache.log4j.Logger log = org.apache.log4j.Logger.getLogger(ShowProjectExperimentReport.class);
  
  
  private SecurityAdvisor               secAdvisor;
  private Integer                       idLab;
  private ProjectExperimentReportFilter filter;
  
  private String                        today;
 
  public void validate() {
  }
  
  public void loadCommand(HttpServletRequest request, HttpSession session) {
    if (request.getParameter("idLab") != null && !request.getParameter("idLab").equals("")) {
      idLab = Integer.valueOf(request.getParameter("idLab"));
    } 
    
    secAdvisor = (SecurityAdvisor)session.getAttribute(SecurityAdvisor.SECURITY_ADVISOR_SESSION_KEY);
    if (secAdvisor == null) {
      this.addInvalidField("secAdvisor", "A security advisor must be created before this command can be executed.");
    }
    
    today = new SimpleDateFormat("yyyy-MM-dd").format(System.currentTimeMillis());

    filter = new ProjectExperimentReportFilter();
    this.loadDetailObject(request, filter);
  }

  @SuppressWarnings("unchecked")
  public Command execute() throws RollBackCommandException {
    
    this.SUCCESS_JSP_HTML = "/report.jsp";
    this.SUCCESS_JSP_CSV = "/report_csv.jsp";
    this.SUCCESS_JSP_PDF = "/report_pdf.jsp";
    this.SUCCESS_JSP_XLS = "/report_xls.jsp";
    this.ERROR_JSP = "/message.jsp";
    
    
    try {
         
      Session sess = secAdvisor.getReadOnlyHibernateSession(this.getUsername());
      DictionaryHelper dh = DictionaryHelper.getInstance(sess);
      
      // Create the report and define the columns
      createReportTray(sess, dh);
    
      // Get the results
      StringBuffer queryBuf = filter.getQuery(secAdvisor);

      if (this.isValid()) {
        
        SimpleDateFormat dateFormat = new SimpleDateFormat();
        List results = (List)sess.createQuery(queryBuf.toString()).list();
        
        for(Iterator i = results.iterator(); i.hasNext();) {
          Object[] row = (Object[])i.next();

          ReportRow reportRow = makeReportRow(row, dateFormat, dh);
          tray.addRow(reportRow);
        }
      }
      
      if (isValid()) {
        this.setSuccessJsp(this, tray.getFormat());
      } else {
        setResponsePage(this.ERROR_JSP);
      }
    
    }catch (UnknownPermissionException e){
      log.error("An exception has occurred in ShowAnnotationReport ", e);
      e.printStackTrace();
      throw new RollBackCommandException(e.getMessage());
        
    }catch (NamingException e){
      log.error("An exception has occurred in ShowAnnotationReport ", e);
      e.printStackTrace();
      throw new RollBackCommandException(e.getMessage());
        
    }catch (SQLException e) {
      log.error("An exception has occurred in ShowAnnotationReport ", e);
      e.printStackTrace();
      throw new RollBackCommandException(e.getMessage());
      
    } catch (Exception e) {
      log.error("An exception has occurred in ShowAnnotationReport ", e);
      e.printStackTrace();
      throw new RollBackCommandException(e.getMessage());
    } finally {
      try {
        secAdvisor.closeReadOnlyHibernateSession();    
      } catch(Exception e) {
        
      }
    }
    
    return this;
  }
  
  private void createReportTray(Session sess, DictionaryHelper dh) {
    // Get the lab
    String labQualifier = "";
    if (idLab != null) {
      Lab lab = (Lab)sess.get(Lab.class, idLab);
      labQualifier += "_" + lab.getLastName();
    }
    
    String title = "GNomEx Requests";
    String fileName = "gnomex_request" + labQualifier + "_" + today;
    
    // set up the ReportTray
    tray = new ReportTray();
    tray.setReportDate(new java.util.Date(System.currentTimeMillis()));
    tray.setReportTitle(title);
    tray.setReportDescription(title);
    tray.setFileName(fileName);
    tray.setFormat(ReportFormats.CSV);
    
    Set columns = new TreeSet();
    columns.add(makeReportColumn("Lab", 1));
    columns.add(makeReportColumn("Experiment #", 2));
    columns.add(makeReportColumn("Name", 3));
    columns.add(makeReportColumn("Owner", 4));
    columns.add(makeReportColumn("Submitter", 5));
    columns.add(makeReportColumn("Category", 6));
    columns.add(makeReportColumn("Application", 7));
    columns.add(makeReportColumn("Date Created", 8));
    columns.add(makeReportColumn("Last Modification", 9));
    columns.add(makeReportColumn("Visibility", 10));
    columns.add(makeReportColumn("Description", 11));
    columns.add(makeReportColumn("Organism", 12));
    columns.add(makeReportColumn("# Samples", 13));
    
    
    tray.setColumns(columns);
  }
  
  private Column makeReportColumn(String name, int colNumber) {
    Column reportCol = new Column();
    reportCol.setName(name);
    reportCol.setCaption(name);
    reportCol.setDisplayOrder(new Integer(colNumber));
    return reportCol;
  }

  private ReportRow makeReportRow(Object[] row, SimpleDateFormat dateFormat, DictionaryHelper dh) {
    ReportRow reportRow = new ReportRow();
    List values  = new ArrayList();
    
    
    String description = (String)row[ProjectExperimentReportFilter.COL_DESCRIPTION];
    description = this.cleanRichText(description);
    
    String labLastName = (String)row[ProjectExperimentReportFilter.COL_LAB_LASTNAME];
    String labFirstName = (String)row[ProjectExperimentReportFilter.COL_LAB_FIRSTNAME];
    String ownerLastName = (String)row[ProjectExperimentReportFilter.COL_OWNER_LASTNAME];
    String ownerFirstName = (String)row[ProjectExperimentReportFilter.COL_OWNER_FIRSTNAME];
    String number = (String)row[ProjectExperimentReportFilter.COL_REQUEST_NUMBER];
    String codeRequestCategory = (String)row[ProjectExperimentReportFilter.COL_CODE_REQUEST_CATEGORY];
    String codeRequestApplication = (String)row[ProjectExperimentReportFilter.COL_CODE_REQUEST_APPLICATION];
    Date createDate = (Date)row[ProjectExperimentReportFilter.COL_CREATE_DATE];
    Date modifyDate = (Date)row[ProjectExperimentReportFilter.COL_MODIFY_DATE];
    String codeVisibility = (String)row[ProjectExperimentReportFilter.COL_CODE_VISIBILITY];
    Date completeDate = (Date)row[ProjectExperimentReportFilter.COL_COMPLETED_DATE];
    Integer idOrganism = (Integer)row[ProjectExperimentReportFilter.COL_ORGANISM];
    Integer numSamples = (Integer)row[ProjectExperimentReportFilter.COL_NUMBER_SAMPLES];
    String submitterLastName = (String)row[ProjectExperimentReportFilter.COL_SUBMITTER_LASTNAME];
    String submitterFirstName = (String)row[ProjectExperimentReportFilter.COL_SUBMITTER_FIRSTNAME];
    String requestName = (String)row[ProjectExperimentReportFilter.COL_REQUEST_NAME];

    String labName = Lab.formatLabName(labLastName, labFirstName);
    String ownerName = AppUser.formatName(ownerLastName, ownerFirstName);
    String submitterName = AppUser.formatName(submitterLastName, submitterFirstName);
    String requestCategory = dh.getRequestCategory(codeRequestCategory);
    String application = dh.getApplication(codeRequestApplication);
    String createDateString = createDate != null ? dateFormat.format(createDate) : "";
    String modifyDateString = modifyDate != null ? dateFormat.format(modifyDate) : "";
    String visibility = DictionaryManager.getDisplay("hci.gnomex.model.Visibility", codeVisibility);
    String completeDateString = completeDate != null ? dateFormat.format(completeDate) : "";
    String organism = dh.getOrganism(idOrganism);
    
    values.add(surroundWithQuotes(labName));
    values.add(surroundWithQuotes(number));
    values.add(surroundWithQuotes(requestName) );
    values.add(surroundWithQuotes(ownerName) );
    values.add(surroundWithQuotes(submitterName) );
    values.add(surroundWithQuotes(requestCategory) );
    values.add(surroundWithQuotes(application) );
    values.add(surroundWithQuotes(createDateString) );
    values.add(surroundWithQuotes(modifyDateString) );
    values.add(surroundWithQuotes(visibility) );
    values.add(surroundWithQuotes(description) );
    values.add(surroundWithQuotes(organism) );
    values.add(surroundWithQuotes(numSamples.toString()) );
   
    reportRow.setValues(values);
    
    return reportRow;
  }
  
  private Object surroundWithQuotes(Object value) {
    if (value == null) {
      value = "";
    }
    return "\"" + value.toString() + "\"";
  }
  
  private String cleanRichText(String description) {

    final char NEW_LINE = 0x0a;
   
    
    if (description == null) {
      return "";
    } else if (description.trim().equals("")) {
      return "";
    }
   
    Pattern paragraph = Pattern.compile("<P.*?>");
    description = paragraph.matcher(description).replaceAll("");
    
    Pattern pattern = Pattern.compile("<\\/P.*?>");
    description = pattern.matcher(description).replaceAll("_NEWLINE_GOES_HERE_");

    String[] tokens = description.split("_NEWLINE_GOES_HERE_");
    if (tokens.length > 0) {
      StringBuffer buf = new StringBuffer();
      for (int x = 0; x < tokens.length; x++) {
        buf.append(tokens[x]);
        buf.append("\n");
      }
      description = buf.toString();
    } 
    
    
    pattern = Pattern.compile("<B.*?>");
    description = pattern.matcher(description).replaceAll("");
    pattern = Pattern.compile("<\\/B.*?>");
    description = pattern.matcher(description).replaceAll("");
    pattern = Pattern.compile("<U.*?>");
    description = pattern.matcher(description).replaceAll("");
    pattern = Pattern.compile("<\\/U.*?>");
    description = pattern.matcher(description).replaceAll("");
    pattern = Pattern.compile("<LI.*?>");
    description = pattern.matcher(description).replaceAll("");
    pattern = Pattern.compile("<\\/LI.*?>");
    description = pattern.matcher(description).replaceAll("");
    pattern = Pattern.compile("<I.*?>");
    description = pattern.matcher(description).replaceAll("");
    pattern = Pattern.compile("<\\/I.*?>");
    description = pattern.matcher(description).replaceAll("");
    
    return description;
  }
  
  /* (non-Javadoc)
   * @see hci.framework.control.Command#setRequestState(javax.servlet.http.HttpServletRequest)
   */
  public HttpServletRequest setRequestState(HttpServletRequest request) {
    request.setAttribute("tray", this.tray);
    return request;
  }

  /* (non-Javadoc)
   * @see hci.framework.control.Command#setResponseState(javax.servlet.http.HttpServletResponse)
   */
  public HttpServletResponse setResponseState(HttpServletResponse response) {
    // TODO Auto-generated method stub
    return response;
  }

  /* (non-Javadoc)
   * @see hci.framework.control.Command#setSessionState(javax.servlet.http.HttpSession)
   */
  public HttpSession setSessionState(HttpSession session) {
    // TODO Auto-generated method stub
    return session;
  }

  /* (non-Javadoc)
   * @see hci.report.utility.ReportCommand#loadContextPermissions()
   */
  public void loadContextPermissions(){
    
  }
  public void loadContextPermissions(String userName) throws SQLException {
    
  }
  
}