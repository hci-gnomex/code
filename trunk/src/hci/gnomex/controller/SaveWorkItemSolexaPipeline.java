package hci.gnomex.controller;

import hci.framework.control.Command;
import hci.framework.control.RollBackCommandException;
import hci.gnomex.constants.Constants;
import hci.gnomex.model.FlowCellChannel;
import hci.gnomex.model.GenomeBuild;
import hci.gnomex.model.PropertyDictionary;
import hci.gnomex.model.Request;
import hci.gnomex.model.RequestStatus;
import hci.gnomex.model.Sample;
import hci.gnomex.model.SequenceLane;
import hci.gnomex.model.WorkItem;
import hci.gnomex.security.SecurityAdvisor;
import hci.gnomex.utility.BillingItemAutoComplete;
import hci.gnomex.utility.DictionaryHelper;
import hci.gnomex.utility.HibernateSession;
import hci.gnomex.utility.MailUtil;
import hci.gnomex.utility.RequestEmailBodyFormatter;
import hci.gnomex.utility.WorkItemSolexaPipelineParser;

import java.io.Serializable;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.mail.MessagingException;
import javax.naming.NamingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.hibernate.Session;
import org.jdom.Document;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jdom.output.XMLOutputter;




public class SaveWorkItemSolexaPipeline extends GNomExCommand implements Serializable {
  
 
  
  // the static field for logging in Log4J
  private static org.apache.log4j.Logger log = org.apache.log4j.Logger.getLogger(SaveWorkItemSolexaPipeline.class);
  
  private String                       workItemXMLString;
  private Document                     workItemDoc;
  private WorkItemSolexaPipelineParser parser;
  
  private DictionaryHelper             dictionaryHelper;
  
  private String                       launchAppURL;
  private String                       appURL;
  
  private String                       serverName;
  
  private HashMap                      requestNotifyMap = new HashMap();
  private HashMap                      requestNotifyLaneMap = new HashMap();
  
  
  
  public void validate() {
  }
  
  public void loadCommand(HttpServletRequest request, HttpSession session) {
    
    
    if (request.getParameter("workItemXMLString") != null && !request.getParameter("workItemXMLString").equals("")) {
      workItemXMLString = "<WorkItemList>" + request.getParameter("workItemXMLString") + "</WorkItemList>";
      
      StringReader reader = new StringReader(workItemXMLString);
      try {
        SAXBuilder sax = new SAXBuilder();
        workItemDoc = sax.build(reader);
        parser = new WorkItemSolexaPipelineParser(workItemDoc);
      } catch (JDOMException je ) {
        log.error( "Cannot parse workItemXMLString", je );
        this.addInvalidField( "WorkItemXMLString", "Invalid work item xml");
      }
    }
    
    try {
      launchAppURL = this.getLaunchAppURL(request);      
      appURL = this.getAppURL(request);      
    } catch (Exception e) {
      log.warn("Cannot get launch app URL in SaveRequest", e);
    }
    
    serverName = request.getServerName();
  }

  public Command execute() throws RollBackCommandException {
    
    if (workItemXMLString != null) {
      try {
        Session sess = HibernateSession.currentSession(this.getUsername());
        Map<Integer, BillingItemAutoComplete> autoCompleteMap = new HashMap<Integer, BillingItemAutoComplete>();
        
        if (this.getSecAdvisor().hasPermission(SecurityAdvisor.CAN_MANAGE_WORKFLOW)) {
          parser.parse(sess);
          
          for (Iterator i = parser.getWorkItems().iterator(); i.hasNext();) {
            WorkItem workItem = (WorkItem) i.next();
            FlowCellChannel channel = (FlowCellChannel) parser.getFlowCellChannel(workItem.getIdWorkItem());

            // No further processing required for On Hold or In Progress work items
            if (workItem.getStatus() != null && workItem.getStatus().equals(Constants.STATUS_ON_HOLD)) {
              continue;
            } else if (workItem.getStatus() != null && workItem.getStatus().equals(Constants.STATUS_IN_PROGRESS)) {
              continue;
            }
            
            // If pipeline is done or has failed, delete the work item.
            if (channel.getPipelineDate() != null ||
                (channel.getPipelineFailed() != null && channel.getPipelineFailed().equals("Y"))) {

              // Delete work item
              sess.delete(workItem);
              
              
             
            }
            
            // Check to see if all of the sequence lanes for each request have been completed.
            if (channel.getSequenceLanes() != null) {
              for(Iterator i1 = channel.getSequenceLanes().iterator(); i1.hasNext();) {
                SequenceLane lane = (SequenceLane)i1.next();
                
                Request request = (Request)sess.load(Request.class, lane.getIdRequest());
                if (autoCompleteMap.containsKey(request.getIdRequest())) {
                  BillingItemAutoComplete auto = autoCompleteMap.get(request.getIdRequest());
                  // If same request has workitems of different steps then they have to manually complete billing items.
                  if (!auto.getCodeStep().equals(workItem.getCodeStepNext())) {
                    auto.setSkip();
                  }
                } else {
                  BillingItemAutoComplete auto = new BillingItemAutoComplete(sess, workItem.getCodeStepNext(), request);
                  autoCompleteMap.put(request.getIdRequest(), auto);
                }
                
                // Keep track of requests to send out a confirmation email (just once per request)
                // (Send email if at least one sequence lane has been completed pipeline.)
                if (channel.getPipelineDate() != null) {  
                  requestNotifyMap.put(request.getNumber(), request);

                  Collection lanes = (Collection)requestNotifyLaneMap.get(request.getNumber());
                  if (lanes == null) {
                    lanes = new ArrayList();
                  }
                  lanes.add(lane);
                  requestNotifyLaneMap.put(request.getNumber(), lanes);
                }

                // Set the completed date on the request
                if (request.isConsideredFinished() && request.getCompletedDate() == null) {
                  request.setCompletedDate(new java.sql.Date(System.currentTimeMillis()));
                  request.setCodeRequestStatus(RequestStatus.COMPLETED);
                }
                
              }
            }
            
          }

          // auto complete the billing items.
          for(Integer key : autoCompleteMap.keySet()) {
            BillingItemAutoComplete auto = autoCompleteMap.get(key);
            if (auto.getSkip()) {
              continue;
            }

            Integer completedQty = 0;
            Map<Integer, FlowCellChannel> channels = auto.getRequest().getFlowCellChannels();
            for(Integer chKey : channels.keySet()) {
              FlowCellChannel channel = channels.get(chKey);
              if (channel != null && channel.getPipelineDate() != null) {
                completedQty++;
              }
            }
            
            auto.completeItems(channels.size(), completedQty);
          }

          sess.flush();
          
          parser.resetIsDirty();

          XMLOutputter out = new org.jdom.output.XMLOutputter();
          this.xmlResult = out.outputString(workItemDoc);

          // Now send out confirmation emails
          for(Iterator i = requestNotifyMap.keySet().iterator(); i.hasNext();) {
            String requestNumber = (String)i.next();
            Request request = (Request)requestNotifyMap.get(requestNumber);
            try {
              this.sendConfirmationEmail(sess, request, (Collection)requestNotifyLaneMap.get(request.getNumber()));
            } catch (Exception e) {
              log.error("Unable to send confirmation email notifying submitter that request "
                  + request.getNumber()
                  + " has sequence lanes that have completed the pipeline.  " + e.toString());
            }
          }
          
          setResponsePage(this.SUCCESS_JSP);  
        } else {
          this.addInvalidField("Insufficient permissions", "Insufficient permission to manage workflow");
          setResponsePage(this.ERROR_JSP);
        }
        
        
      }catch (Exception e){
        log.error("An exception has occurred in SaveWorkflowSolexaPipeline ", e);
        e.printStackTrace();
        throw new RollBackCommandException(e.getMessage());
          
      }finally {
        try {
          HibernateSession.closeSession();        
        } catch(Exception e) {
          
        }
      }
      
    } else {
      this.xmlResult = "<SUCCESS/>";
      setResponsePage(this.SUCCESS_JSP);
    }
    
    return this;
  }
  
  

  
  private void sendConfirmationEmail(Session sess, Request request, Collection lanes) throws NamingException, MessagingException {
    
    dictionaryHelper = DictionaryHelper.getInstance(sess);
    
    String downloadRequestURL = launchAppURL + "?requestNumber=" + request.getNumber() + "&launchWindow=" + Constants.WINDOW_FETCH_RESULTS;

    String analysisInstruction = null;
    String genomeAlignTo = null;
    
    String finishedLaneText = "";
    int finishedLaneCount = 0;
    
    for(Iterator i = lanes.iterator(); i.hasNext();) {
      SequenceLane lane = (SequenceLane)i.next();

      if (lane.getAnalysisInstructions() != null && !lane.getAnalysisInstructions().equals("")) {
        analysisInstruction = lane.getAnalysisInstructions();
      }
      if (lane.getIdGenomeBuildAlignTo() != null) {
        GenomeBuild genomeBuild = (GenomeBuild)sess.load(GenomeBuild.class, lane.getIdGenomeBuildAlignTo());
        genomeAlignTo = genomeBuild.getDisplay();
      }
      
      if (lane.getFlowCellChannel() != null && lane.getFlowCellChannel().getPipelineDate() != null) {
        finishedLaneText += lane.getNumber();
        finishedLaneCount++;
        if (i.hasNext()) {
          finishedLaneText += ", ";
        }
      }
    }
    String laneText = finishedLaneCount > 1 ? "Lanes" : "Lane";
    String haveText = finishedLaneCount > 1 ? "have"  : "has";
    
    if (finishedLaneCount == 0) {
      return;
    }
    
    // Send email to submitter
    StringBuffer introNote = new StringBuffer();
    
    introNote.append("Sequence " + laneText + " " + finishedLaneText + " for ");
    introNote.append("Request " + request.getNumber() + " " + haveText + " been completed by the " + dictionaryHelper.getPropertyDictionary(PropertyDictionary.CORE_FACILITY_NAME) + ".");
    introNote.append("<br><br>To fetch the results, click <a href=\"" + downloadRequestURL + "\">" + Constants.APP_NAME + " - " + Constants.WINDOW_NAME_FETCH_RESULTS + "</a>.");
    
    RequestEmailBodyFormatter emailFormatter = new RequestEmailBodyFormatter(sess, this.getSecAdvisor(), appURL, dictionaryHelper, request, null, request.getSamples(), request.getHybridizations(), request.getSequenceLanes(), introNote.toString());
    emailFormatter.setIncludeMicroarrayCoreNotes(false);
    
    String subject = dictionaryHelper.getRequestCategory(request.getCodeRequestCategory()) + " Request " + request.getNumber() + " completed";
    
    boolean send = false;
    String emailInfo = "";
    String emailRecipients = request.getAppUser().getEmail();
    if (dictionaryHelper.isProductionServer(serverName)) {
      send = true;
    } else {
      send = true;
      subject = subject + "  (TEST)";
      emailInfo = "[If this were a production environment then this email would have been sent to: " + emailRecipients + "]<br><br>";
      emailRecipients = dictionaryHelper.getPropertyDictionary(PropertyDictionary.CONTACT_EMAIL_SOFTWARE_TESTER);
    }
    
    if (send) {
      MailUtil.send(emailRecipients, 
          null,
          dictionaryHelper.getPropertyDictionary(PropertyDictionary.CONTACT_EMAIL_CORE_FACILITY), 
          subject, 
          emailInfo + emailFormatter.format(),
          true);
    }
    
    // Send email to bioinformatics core
    StringBuffer bioIntroNote = new StringBuffer();

    bioIntroNote.append("Sequence " + laneText + " " + finishedLaneText + " for ");
    bioIntroNote.append("Request " + request.getNumber() + " " + haveText + " been completed by the " + dictionaryHelper.getPropertyDictionary(PropertyDictionary.CORE_FACILITY_NAME) + ".");
    bioIntroNote.append("<br>");
    
    emailRecipients = dictionaryHelper.getPropertyDictionary(PropertyDictionary.CONTACT_EMAIL_BIOINFORMATICS);
    
    if (genomeAlignTo != null && !genomeAlignTo.equals("")) {
      bioIntroNote.append("<br><br><b>Genome Align To:   </b>" + genomeAlignTo);
    }
    if (analysisInstruction != null && !analysisInstruction.equals("")) {
      bioIntroNote.append("<br><br><b>Analysis Instructions:   </b>" + analysisInstruction);
    }
    
    bioIntroNote.append("<br><br><br>To fetch the results, click <a href=\"" + downloadRequestURL + "\">" + Constants.APP_NAME + " - " + Constants.WINDOW_NAME_FETCH_RESULTS + "</a>.");

    RequestEmailBodyFormatter bioEmailFormatter = new RequestEmailBodyFormatter(sess, this.getSecAdvisor(), appURL, dictionaryHelper, request, null, request.getSamples(), request.getHybridizations(), request.getSequenceLanes(), bioIntroNote.toString());
    bioEmailFormatter.setIncludeMicroarrayCoreNotes(false);
     
    subject = dictionaryHelper.getRequestCategory(request.getCodeRequestCategory()) + " Request " + request.getNumber() + " completed";
    boolean bioSend = false;
    if (dictionaryHelper.isProductionServer(serverName)) {
      if (analysisInstruction != null && !analysisInstruction.equals("")) {
        bioSend = true;
      }
      if (genomeAlignTo != null && !genomeAlignTo.equals("")) {
        bioSend = true;
      }
    } else {
      bioSend = true;
      subject = subject + "  (TEST)";
      emailInfo = "[If this were a production environment then this email would have been sent to: " + emailRecipients + "]<br><br>";
      emailRecipients = dictionaryHelper.getPropertyDictionary(PropertyDictionary.CONTACT_EMAIL_SOFTWARE_TESTER);
    }
    
    if (bioSend) {      
      MailUtil.send(emailRecipients, 
          null,
          dictionaryHelper.getPropertyDictionary(PropertyDictionary.CONTACT_EMAIL_CORE_FACILITY), 
          subject, 
          emailInfo + bioEmailFormatter.format(),
          true);
    }
  }
}