package hci.gnomex.controller;

import hci.gnomex.constants.Constants;
import hci.gnomex.model.ArrayCoordinate;
import hci.gnomex.model.Sample;
import hci.gnomex.model.Step;
import hci.gnomex.model.WorkItemFilter;
import hci.gnomex.security.SecurityAdvisor;
import hci.gnomex.utility.HibernateSession;
import hci.framework.control.Command;
import hci.framework.control.RollBackCommandException;

import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.naming.NamingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.hibernate.Session;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.output.XMLOutputter;


public class GetWorkItemList extends GNomExCommand implements Serializable {
  
  private static org.apache.log4j.Logger log = org.apache.log4j.Logger.getLogger(GetWorkItemList.class);
  
  private WorkItemFilter filter;
  
  public void validate() {
  }
  
  public void loadCommand(HttpServletRequest request, HttpSession session) {

    filter = new WorkItemFilter();
    HashMap errors = this.loadDetailObject(request, filter);
    this.addInvalidFields(errors);
  }

  public Command execute() throws RollBackCommandException {
    
    try {
      
      if (this.getSecurityAdvisor().hasPermission(SecurityAdvisor.CAN_MANAGE_WORKFLOW)) {
        Session sess = this.getSecAdvisor().getReadOnlyHibernateSession(this.getUsername());
        
        Comparator comparator = null;
        if (filter.getCodeStepNext().equals(Step.QUALITY_CONTROL_STEP) ||
            filter.getCodeStepNext().equals(Step.SEQ_QC) ||
            filter.getCodeStepNext().equals(Step.SEQ_PREP)) {
          comparator = new SampleComparator();
        } else if (filter.getCodeStepNext().equals(Step.LABELING_STEP)) {
          comparator  = new LabeledSampleComparator();
        } else if (filter.getCodeStepNext().equals(Step.SEQ_ASSEMBLE)) {
          comparator  = new LaneComparator();
        } else if (filter.getCodeStepNext().equals(Step.SEQ_RUN)) {
          comparator = new FlowCellComparator();
        } else {
          comparator =  new HybComparator();
        }
        
        TreeMap allRows = allRows = new TreeMap(comparator);
        StringBuffer queryBuf = filter.getQuery(this.getSecAdvisor());
        log.info("GetWorkItemList query: " + queryBuf.toString());
        List rows = (List) sess.createQuery(queryBuf.toString()).list();

        for (Iterator i1 = rows.iterator(); i1.hasNext();) {
          Object[] row = (Object[]) i1.next();
          
          String requestNumber    = (String) row[1];
          String sampleNumber     = (String) row[9];
          String itemNumber       = (String) row[11];
          

          String key = null;
          if (filter.getCodeStepNext().equals(Step.QUALITY_CONTROL_STEP) ||
              filter.getCodeStepNext().equals(Step.SEQ_QC) ||
              filter.getCodeStepNext().equals(Step.SEQ_PREP)) {
            key = requestNumber + "," + sampleNumber;
          } else if (filter.getCodeStepNext().equals(Step.LABELING_STEP)) {
            Integer idLabel         = row[16] == null || row[16].equals("") ? null : (Integer)row[16];
            Integer idLabeledSample = row[18] == null || row[18].equals("") ? null : (Integer)row[18];
            key = requestNumber + "," + sampleNumber + "," + idLabel  + "," + idLabeledSample;
          } else if (filter.getCodeStepNext().equals(Step.SEQ_RUN)) {
            Integer idFlowCellType = (Integer)row[13];
            key = idFlowCellType.toString();
          } else {
            key = requestNumber + "," + itemNumber;
          }

          allRows.put(key, row);
        }
        
        boolean alt = false;
        String prevRequestNumber = "";
      
        Document doc = new Document(new Element("WorkItemList"));
        for(Iterator i = allRows.keySet().iterator(); i.hasNext();) {
          String key = (String)i.next();
          Object[] row = (Object[])allRows.get(key);
          
          String requestNumber = (String)row[1];
          if (requestNumber != null && !requestNumber.equals(prevRequestNumber)) {
            alt = !alt;
          }
          
          Element n = new Element("WorkItem");
          n.setAttribute("key",                    key);
          n.setAttribute("isSelected",             "N");
          n.setAttribute("altColor",               new Boolean(alt).toString());
          
          n.setAttribute("idRequest",              row[0] == null ? "" : ((Integer)row[0]).toString());
          n.setAttribute("requestNumber",          row[1] == null ? "" : (String)row[1]);
          n.setAttribute("createDate",             row[2] == null ? "" :  this.formatDate((java.sql.Date)row[2]));
          n.setAttribute("codeRequestCategory",    row[3] == null ? "" :  (String)row[3]);
          n.setAttribute("codeRequestCategory1",   row[3] == null ? "" :  (String)row[3]);
          n.setAttribute("idAppUser",              row[4] == null ? "" :  ((Integer)row[4]).toString());
          n.setAttribute("idLab",                  row[5] == null ? "" :  ((Integer)row[5]).toString());
          n.setAttribute("idWorkItem",            ((Integer)row[6]).toString());
          n.setAttribute("codeStepNext",           row[7] == null ? "" :  (String)row[7]);
          n.setAttribute("idSample",               row[8] == null ? "" :  ((Integer)row[8]).toString());
          n.setAttribute("sampleNumber",           row[9] == null ? "" :  (String)row[9]);
          n.setAttribute("idHybridization",        row[10] == null ? "" :  ((Integer)row[10]).toString());
          if (filter.getCodeStepNext().equals(Step.SEQ_ASSEMBLE)) {
            n.setAttribute("laneNumber",             row[11] == null ? "" :  (String)row[11]);  
          } else {
            n.setAttribute("hybNumber",              row[11] == null ? "" :  (String)row[11]);
          }
          n.setAttribute("workItemCreateDate",     row[12] == null ? "" :  this.formatDate((java.sql.Date)row[12]));
          n.setAttribute("isDirty","N");
          
          if (filter.getCodeStepNext().equals(Step.QUALITY_CONTROL_STEP) ||
              filter.getCodeStepNext().equals(Step.SEQ_QC)) {
            n.setAttribute("qualDate",                   row[13] == null ? "" :  this.formatDate((java.sql.Date)row[13]));
            n.setAttribute("qualCompleted",              row[13] == null ? "N" : "Y");
            n.setAttribute("qualFailed",                 row[14] == null ? "" :  (String)row[14]);
            n.setAttribute("qual260nmTo280nmRatio",      row[15] == null ? "" :  ((BigDecimal)row[15]).toString());
            n.setAttribute("qual260nmTo230nmRatio",      row[16] == null ? "" :  ((BigDecimal)row[16]).toString());
            n.setAttribute("qualAverageFragmentLength",  row[17] == null ? "" :  ((BigDecimal)row[17]).toString());
            n.setAttribute("qualCalcConcentration",      row[18] == null ? "" :  ((BigDecimal)row[18]).toString());
            n.setAttribute("qual28sTo18sRibosomalRatio", row[19] == null ? "" :  ((BigDecimal)row[19]).toString());
            n.setAttribute("qualRINNumber",              row[20] == null ? "" :  ((BigDecimal)row[20]).toString());
            n.setAttribute("qualBypassed",               row[21] == null ? "" :  (String)row[21]);
            
            String qualStatus = "";
            if (n.getAttributeValue("qualCompleted").equals("Y")) {
              qualStatus = Constants.STATUS_COMPLETED;
            } else if (n.getAttributeValue("qualFailed").equals("Y")) {
              qualStatus = Constants.STATUS_TERMINATED;
            } else if (n.getAttributeValue("qualBypassed").equals("Y")) {
              qualStatus = Constants.STATUS_BYPASSED;
            }
            n.setAttribute("qualStatus", qualStatus);
            
          } else if (filter.getCodeStepNext().equals(Step.LABELING_STEP)) {
            n.setAttribute("labelingDate",               row[13] == null ? "" :  this.formatDate((java.sql.Date)row[13]));
            n.setAttribute("labelingCompleted",          row[13] == null ? "N" : "Y");
            n.setAttribute("labelingFailed",             row[14] == null ? "" :  (String)row[14]).toString();
            n.setAttribute("labelingYield",              row[15] == null ? "" :  ((BigDecimal)row[15]).toString());
            n.setAttribute("idLabel",                    row[16] == null ? "" :  ((Integer)row[16]).toString());
            n.setAttribute("idLabelingProtocol",         row[17] == null ? "" :  ((Integer)row[17]).toString());
            n.setAttribute("idLabeledSample",            row[18] == null ? "" :  ((Integer)row[18]).toString());
            n.setAttribute("codeLabelingReactionSize",   row[19] == null ? "" :  (String)row[19]);
            n.setAttribute("numberOfReactions",          row[20] == null ? "" :  ((Integer)row[20]).toString());
            n.setAttribute("labelingBypassed",           row[21] == null ? "" :  (String)row[21]);
            
            String labelingStatus = "";
            if (n.getAttributeValue("labelingCompleted").equals("Y")) {
              labelingStatus = Constants.STATUS_COMPLETED;
            } else if (n.getAttributeValue("labelingFailed").equals("Y")) {
              labelingStatus = Constants.STATUS_TERMINATED;
            } else if (n.getAttributeValue("labelingBypassed").equals("Y")) {
              labelingStatus = Constants.STATUS_BYPASSED;
            }
            n.setAttribute("labelingStatus", labelingStatus);
            
          } else if (filter.getCodeStepNext().equals(Step.HYB_STEP)) {
            n.setAttribute("hybDate",                   row[13] == null ? "" :  this.formatDate((java.sql.Date)row[13]));
            n.setAttribute("hybCompleted",              row[13] == null ? "N" : "Y");
            n.setAttribute("hybFailed",                 row[14] == null ? "" :  (String)row[14]);
            n.setAttribute("idHybProtocol",             row[15] == null ? "" :  ((Integer)row[15]).toString());
            n.setAttribute("idSlide",                   row[16] == null ? "" :  ((Integer)row[16]).toString());
            n.setAttribute("slideBarcode",              row[17] == null ? "" :  (String)row[17]);
            n.setAttribute("idSlideDesign",             row[18] == null ? "" :  ((Integer)row[18]).toString());
            n.setAttribute("idArrayCoordinate",         row[19] == null ? "" :  ((Integer)row[19]).toString());
            n.setAttribute("arrayCoordinate",           row[20] == null ? "" :  (String)row[20]);
            n.setAttribute("hybBypassed",               row[21] == null ? "" :  (String)row[21]);

            String hybStatus = "";
            if (n.getAttributeValue("hybCompleted").equals("Y")) {
              hybStatus = Constants.STATUS_COMPLETED;
            } else if (n.getAttributeValue("hybFailed").equals("Y")) {
              hybStatus = Constants.STATUS_TERMINATED;
            } else if (n.getAttributeValue("hybBypassed").equals("Y")) {
              hybStatus = Constants.STATUS_BYPASSED;
            }
            n.setAttribute("hybStatus", hybStatus);

          } else if (filter.getCodeStepNext().equals(Step.SCAN_EXTRACTION_STEP)) {
            n.setAttribute("extractionDate",               row[13] == null ? "" :  this.formatDate((java.sql.Date)row[13]));
            n.setAttribute("extractionCompleted",          row[13] == null ? "N" : "Y");
            n.setAttribute("idScanProtocol",               row[14] == null ? "" :  ((Integer)row[14]).toString());
            n.setAttribute("idFeatureExtractionProtocol",  row[15] == null ? "" :  ((Integer)row[15]).toString());
            n.setAttribute("extractionFailed",             row[16] == null ? "" :  (String)row[16]);
            n.setAttribute("extractionBypassed",           row[17] == null ? "" :  (String)row[17]);
            

            String extractionStatus = "";
            if (n.getAttributeValue("extractionCompleted").equals("Y")) {
              extractionStatus = Constants.STATUS_COMPLETED;
            } else if (n.getAttributeValue("extractionFailed").equals("Y")) {
              extractionStatus = Constants.STATUS_TERMINATED;
            } else if (n.getAttributeValue("extractionBypassed").equals("Y")) {
              extractionStatus = Constants.STATUS_BYPASSED;
            }
            n.setAttribute("extractionStatus", extractionStatus);
          } else if (filter.getCodeStepNext().equals(Step.SEQ_ASSEMBLE)) {
            n.setAttribute("idSequenceLane",               row[13] == null ? "" :  ((Integer)row[13]).toString());
            n.setAttribute("idFlowCellType",               row[14] == null ? "" :  ((Integer)row[14]).toString());
            n.setAttribute("idOrganism",                   row[15] == null ? "" :  ((Integer)row[15]).toString());
            n.setAttribute("idNumberSequencingCycles",     row[16] == null ? "" :  ((Integer)row[16]).toString());
          
          }  else if (filter.getCodeStepNext().equals(Step.SEQ_RUN)) {
            n.setAttribute("idFlowCell",                   row[13] == null ? "" :  ((Integer)row[13]).toString());
            n.setAttribute("idFlowCellType",               row[14] == null ? "" :  ((Integer)row[14]).toString());
            n.setAttribute("idNumberSequencingCycles",     row[15] == null ? "" :  ((Integer)row[15]).toString());
            n.setAttribute("number",                       row[16] == null ? "" :  ((String)row[16]));
            n.setAttribute("createDate",                   row[17] == null ? "" :  this.formatDate((java.sql.Date)row[17]));
            n.setAttribute("notes",                        row[18] == null ? "" :  ((String)row[18]));
            n.setAttribute("firstCycleDate",               row[19] == null ? "" :  this.formatDate((java.sql.Date)row[19]));
            n.setAttribute("firstCycleCompleted",          row[19] == null ? "N" : "Y");
            n.setAttribute("firstCycleFailed",             row[20] == null ? "" :  ((String)row[20]));
            n.setAttribute("lastCycleDate",                row[21] == null ? "" :  this.formatDate((java.sql.Date)row[21]));
            n.setAttribute("lastCycleCompleted",           row[21] == null ? "N" : "Y");
            n.setAttribute("lastCycleFailed",              row[22] == null ? "" :  ((String)row[22]));
            n.setAttribute("firstCycleStartDate",          row[23] == null ? "" :   this.formatDate((java.sql.Date)row[23]));

            String firstCycleStatus = "";
            if (n.getAttributeValue("firstCycleCompleted").equals("Y")) {
              firstCycleStatus = Constants.STATUS_COMPLETED;
            } else if (n.getAttributeValue("firstCycleFailed").equals("Y")) {
              firstCycleStatus = Constants.STATUS_TERMINATED;
            } else if (n.getAttributeValue("firstCycleStartDate") != "") {
              firstCycleStatus = Constants.STATUS_IN_PROGRESS;
            }
            n.setAttribute("firstCycleStatus", firstCycleStatus);
          
            String lastCycleStatus = "";
            if (n.getAttributeValue("lastCycleCompleted").equals("Y")) {
              lastCycleStatus = Constants.STATUS_COMPLETED;
            } else if (n.getAttributeValue("lastCycleFailed").equals("Y")) {
              lastCycleStatus = Constants.STATUS_TERMINATED;
            }   
            n.setAttribute("lastCycleStatus", lastCycleStatus);
          
         
          }



          
          doc.getRootElement().addContent(n);
          
          prevRequestNumber = requestNumber;
          
        }
      
        XMLOutputter out = new org.jdom.output.XMLOutputter();
        this.xmlResult = out.outputString(doc);
      
        setResponsePage(this.SUCCESS_JSP);
        
      } else {
        this.addInvalidField("Insufficient permissions", "Insufficient permission to manage workflow.");
        setResponsePage(this.ERROR_JSP);
      }
      
      
   
    }catch (NamingException e){
      log.error("An exception has occurred in GetRequestList ", e);
      e.printStackTrace(System.out);
      throw new RollBackCommandException(e.getMessage());
        
    }catch (SQLException e) {
      log.error("An exception has occurred in GetRequestList ", e);
      e.printStackTrace(System.out);
      throw new RollBackCommandException(e.getMessage());
    } catch (Exception e) {
      log.error("An exception has occurred in GetRequestList ", e);
      e.printStackTrace(System.out);
      throw new RollBackCommandException(e.getMessage());
    } finally {
      try {
        this.getSecAdvisor().closeReadOnlyHibernateSession();        
      } catch(Exception e) {
        
      }
    }
    
    return this;
  }
  
  public static class  SampleComparator implements Comparator, Serializable {
    public int compare(Object o1, Object o2) {
      String key1 = (String)o1;
      String key2 = (String)o2;

      
      
      String[] tokens1 = key1.split(",");
      String[] tokens2 = key2.split(",");
      
      String reqNumber1    = tokens1[0];
      String itemNumber1 = tokens1[1];
      
      String reqNumber2    = tokens2[0];
      String itemNumber2 = tokens2[1];

      String[] itemNumberTokens1 = itemNumber1.split("X");
      String number1 = itemNumberTokens1[itemNumberTokens1.length - 1];

      String[] itemNumberTokens2 = itemNumber2.split("X");
      String number2 = itemNumberTokens2[itemNumberTokens2.length - 1];

      if (reqNumber1.equals(reqNumber2)) {
        return new Integer(number1).compareTo(new Integer(number2));        
      } else {
        return reqNumber1.compareTo(reqNumber2);
      }
      
    }
  }
  public static class  HybComparator implements Comparator, Serializable {
    public int compare(Object o1, Object o2) {
      String key1 = (String)o1;
      String key2 = (String)o2;

      
      
      String[] tokens1 = key1.split(",");
      String[] tokens2 = key2.split(",");
      
      String reqNumber1    = tokens1[0];
      String itemNumber1 = tokens1[1];
      
      String reqNumber2    = tokens2[0];
      String itemNumber2 = tokens2[1];

      String[] itemNumberTokens1 = itemNumber1.split("E");
      String number1 = itemNumberTokens1[itemNumberTokens1.length - 1];

      String[] itemNumberTokens2 = itemNumber2.split("E");
      String number2 = itemNumberTokens2[itemNumberTokens2.length - 1];

      if (reqNumber1.equals(reqNumber2)) {
        return new Integer(number1).compareTo(new Integer(number2));        
      } else {
        return reqNumber1.compareTo(reqNumber2);
      }
      
    }
  }
  public static class  LaneComparator implements Comparator, Serializable {
    public int compare(Object o1, Object o2) {
      String key1 = (String)o1;
      String key2 = (String)o2;

      
      
      String[] tokens1 = key1.split(",");
      String[] tokens2 = key2.split(",");
      
      String reqNumber1    = tokens1[0];
      String itemNumber1 = tokens1[1];
      
      String reqNumber2    = tokens2[0];
      String itemNumber2 = tokens2[1];

      String[] itemNumberTokens1 = itemNumber1.split("L");
      String number1 = itemNumberTokens1[itemNumberTokens1.length - 1];

      String[] itemNumberTokens2 = itemNumber2.split("L");
      String number2 = itemNumberTokens2[itemNumberTokens2.length - 1];

      if (reqNumber1.equals(reqNumber2)) {
        return new Integer(number1).compareTo(new Integer(number2));        
      } else {
        return reqNumber1.compareTo(reqNumber2);
      }
      
    }
  }  
  public static class  FlowCellComparator implements Comparator, Serializable {
    public int compare(Object o1, Object o2) {
      String key1 = (String)o1;
      String key2 = (String)o2;
      return new Integer(key1).compareTo(new Integer(key2));        
      
    }
  }  
  
  public static class  LabeledSampleComparator implements Comparator, Serializable {
    public int compare(Object o1, Object o2) {
      String key1 = (String)o1;
      String key2 = (String)o2;

      
      
      String[] tokens1 = key1.split(",");
      String[] tokens2 = key2.split(",");
      
      String reqNumber1       = tokens1[0];
      String sampleNumber1    = tokens1[1];
      String idLabel1         = tokens1[2];
      String idLabeledSample1 = tokens1[3];
      
      String reqNumber2       = tokens2[0];
      String sampleNumber2    = tokens2[1];
      String idLabel2         = tokens2[2];
      String idLabeledSample2 = tokens2[3];


      String[] tokens = sampleNumber1.split("X");
      String sampleNum1 = tokens[tokens.length - 1];

      tokens = sampleNumber2.split("X");
      String sampleNum2 = tokens[tokens.length - 1];


      if (reqNumber1.equals(reqNumber2)) {
        if (sampleNum1.equals(sampleNum2)) {
          if (idLabel1.equals(idLabel2)) {
            return new Integer(idLabeledSample1).compareTo(new Integer(idLabeledSample2));
          } else {
            return new Integer(idLabel1).compareTo(new Integer(idLabel2));
          }
        } else {
          return new Integer(sampleNum1).compareTo(new Integer(sampleNum2));
        }
      } else {
        return reqNumber1.compareTo(reqNumber2);
      }
      
    }
  }

}