package hci.gnomex.controller;

import hci.framework.control.Command;
import hci.framework.control.RollBackCommandException;
import hci.gnomex.model.BillingAccount;
import hci.gnomex.model.BillingItem;
import hci.gnomex.model.BillingPeriod;
import hci.gnomex.model.BillingStatus;
import hci.gnomex.model.CoreFacility;
import hci.gnomex.model.DiskUsageByMonth;
import hci.gnomex.model.Invoice;
import hci.gnomex.model.Lab;
import hci.gnomex.model.PropertyDictionary;
import hci.gnomex.model.Request;
import hci.gnomex.security.SecurityAdvisor;
import hci.gnomex.utility.BillingInvoiceEmailFormatter;
import hci.gnomex.utility.BillingItemParser;
import hci.gnomex.utility.DictionaryHelper;
import hci.gnomex.utility.HibernateSession;
import hci.gnomex.utility.LogLongExecutionTimes;
import hci.gnomex.utility.MailUtil;
import hci.gnomex.utility.PropertyDictionaryHelper;
import hci.gnomex.utility.LogLongExecutionTimes.LogItem;

import java.io.Serializable;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.hibernate.Query;
import org.hibernate.Session;
import org.jdom.Document;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;




public class SaveBillingItemList extends GNomExCommand implements Serializable {
  
  private class LabAccountBillingPeriod implements Serializable {
    public Lab getLab() {
      return lab;
    }
    public Integer getIdBillingPeriod() {
      return idBillingPeriod;
    }
    public BillingAccount getBillingAccount() {
      return billingAccount;
    }
    public Integer getIdCoreFacility() {
      return idCoreFacility;
    }
    public LabAccountBillingPeriod(Lab lab, Integer idBillingPeriod,
        BillingAccount billingAccount, Integer idCoreFacility) {
      super();
      this.lab = lab;
      this.idBillingPeriod = idBillingPeriod;
      this.billingAccount = billingAccount;
      this.idCoreFacility = idCoreFacility;
    }
    
    private Lab lab;
    private Integer idBillingPeriod;
    private BillingAccount billingAccount;
    private Integer idCoreFacility;
    
    public int hashCode() {
      return new HashCodeBuilder()
      .append(getLab().getIdLab())
      .append(getIdBillingPeriod())
      .append(getBillingAccount().getIdBillingAccount())
      .append(getIdCoreFacility())
      .toHashCode();
    }
    
    public boolean equals(Object other) {
      if ( !(other instanceof LabAccountBillingPeriod) ) return false;
      LabAccountBillingPeriod castOther = (LabAccountBillingPeriod) other;
      return new EqualsBuilder()
          .append(this.getLab().getIdLab(), castOther.getLab().getIdLab())
          .append(this.getIdBillingPeriod(), castOther.getIdBillingPeriod())
          .append(this.getBillingAccount().getIdBillingAccount(), castOther.getBillingAccount().getIdBillingAccount())
          .append(this.getIdCoreFacility(), castOther.getIdCoreFacility())
          .isEquals();
  }    

} 
  
  // the static field for logging in Log4J
  private static org.apache.log4j.Logger log = org.apache.log4j.Logger.getLogger(SaveBillingItemList.class);
  
  private String                       billingItemXMLString;
  private Document                     billingItemDoc;
  private BillingItemParser            parser;
  
  private String                       appURL;
  
  private String                       serverName;
  
  private Map<LabAccountBillingPeriod, Object[]>  labAccountBillingPeriodMap = new HashMap<LabAccountBillingPeriod, Object[]>();
  
  private DictionaryHelper             dictionaryHelper = null;
  
  private transient LogLongExecutionTimes executionLogger = null;
  
  public void validate() {
  }
  
  public void loadCommand(HttpServletRequest request, HttpSession session) {
    
    
    if (request.getParameter("billingItemXMLString") != null && !request.getParameter("billingItemXMLString").equals("")) {
      billingItemXMLString = "<BillingItemList>" + request.getParameter("billingItemXMLString") + "</BillingItemList>";
      
      StringReader reader = new StringReader(billingItemXMLString);
      try {
        SAXBuilder sax = new SAXBuilder();
        billingItemDoc = sax.build(reader);
        parser = new BillingItemParser(billingItemDoc);
      } catch (JDOMException je ) {
        log.error( "Cannot parse billingItemXMLString", je );
        this.addInvalidField( "BillingItemXMLString", "Invalid work item xml");
      }
    }

    
    try {
      appURL = this.getLaunchAppURL(request);      
    } catch (Exception e) {
      log.warn("Cannot get launch app URL in SaveBillingItemList", e);
    }
    
    serverName = request.getServerName();
    
  }

  public Command execute() throws RollBackCommandException {
    
    if (billingItemXMLString != null) {
      try {
        Session sess = HibernateSession.currentSession(this.getUsername());
        
        executionLogger = new LogLongExecutionTimes(log, PropertyDictionaryHelper.getInstance(sess), "SaveBillingItemList");
        LogItem li = null;
        
        if (this.getSecAdvisor().hasPermission(SecurityAdvisor.CAN_MANAGE_BILLING)) {
          li = executionLogger.startLogItem("Parse");
          parser.parse(sess);
          executionLogger.endLogItem(li);

          li = executionLogger.startLogItem("Initial Save");
          ArrayList billingItems = new ArrayList();
          for(Iterator i = parser.getBillingItems().iterator(); i.hasNext();) {
            BillingItem billingItem = (BillingItem)i.next();
            if (billingItem.getIdCoreFacility() == null) {
              if (billingItem.getIdRequest() != null) {
                Request req = (Request)sess.load(Request.class, billingItem.getIdRequest());
                billingItem.setIdCoreFacility(req.getIdCoreFacility());
              }
              if (billingItem.getIdDiskUsageByMonth() != null) {
                DiskUsageByMonth dsk = (DiskUsageByMonth)sess.load(DiskUsageByMonth.class, billingItem.getIdDiskUsageByMonth());
                billingItem.setIdCoreFacility(dsk.getIdCoreFacility());
              }
            }
            billingItem.resetInvoiceForBillingItem(sess);
            sess.save(billingItem);
            billingItems.add(billingItem);

          }
          
          sess.flush();
          executionLogger.endLogItem(li);
          
          li = executionLogger.startLogItem("Set PO Status");
          for(Iterator i = billingItems.iterator(); i.hasNext();) {
            BillingItem billingItem = (BillingItem)i.next();
            
            sess.refresh(billingItem);
            
            // For PO Billing account, approved status is changed to 
            // 'Approved External' so that it shows under a different
            // folder in the billing tree.
            if (billingItem.getCodeBillingStatus().equals(BillingStatus.APPROVED)) {
              if (billingItem.getBillingAccount().getIsPO() != null && billingItem.getBillingAccount().getIsPO().equals("Y")) {
                billingItem.setCodeBillingStatus(BillingStatus.APPROVED_PO);
              }
            }
            

          }
          
          sess.flush();
          executionLogger.endLogItem(li);

          li = executionLogger.startLogItem("Check Approved");
          for(Iterator i = parser.getBillingItems().iterator(); i.hasNext();) {
            BillingItem bi = (BillingItem)i.next();
            sess.refresh(bi);
            // This item should not contribute to decision to send billing statement if previously approved
            if (!(bi.getCodeBillingStatus().equals(BillingStatus.APPROVED) && (bi.getCurrentCodeBillingStatus().equals(BillingStatus.APPROVED)))) {
              LabAccountBillingPeriod labp = new LabAccountBillingPeriod(bi.getLab(), bi.getBillingPeriod().getIdBillingPeriod(), bi.getBillingAccount(), bi.getIdCoreFacility());
              labAccountBillingPeriodMap.put(labp, null);              
            }

          }
          executionLogger.endLogItem(li);
          
          li = executionLogger.startLogItem("Delete Items");
          for(Iterator i = parser.getBillingItemsToRemove().iterator(); i.hasNext();) {
            BillingItem bi = (BillingItem)i.next();
            sess.delete(bi);
          }
          sess.flush();
          executionLogger.endLogItem(li);
          
          li = executionLogger.startLogItem("Save Invoices");
          for(Invoice invoice : parser.getInvoices()) {
            sess.save(invoice);
          }
          sess.flush();
          executionLogger.endLogItem(li);
          
          for(Iterator<LabAccountBillingPeriod> i = labAccountBillingPeriodMap.keySet().iterator(); i.hasNext();) {
            LabAccountBillingPeriod labp = (LabAccountBillingPeriod) i.next();
            this.checkToSendInvoiceEmail(sess, labp.getLab(), labp.getIdBillingPeriod(), labp.getBillingAccount(), labp.getIdCoreFacility());
          }       
          
          this.xmlResult = "<SUCCESS/>";
          
          setResponsePage(this.SUCCESS_JSP);          
        } else {
          this.addInvalidField("Insufficient permissions", "Insufficient permission to manage workflow");
          setResponsePage(this.ERROR_JSP);
        }

        this.executionLogger.LogTimes();
      }catch (Exception e){
        log.error("An exception has occurred in SaveBillingItem ", e);
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
  
  private void checkToSendInvoiceEmail(Session sess, Lab lab, Integer idBillingPeriod, BillingAccount billingAccount, Integer idCoreFacility) {
    if (lab != null && lab.getContactEmail() != null && !lab.getContactEmail().equals("")) {
      if (this.readyToInvoice(sess, idBillingPeriod, lab, billingAccount.getIdBillingAccount(), idCoreFacility)) {
        try {
          sendInvoiceEmail(sess, idBillingPeriod, lab, billingAccount, idCoreFacility);        
        } catch (Exception e) {
          log.error("Unable to send invoice email to billing contact " + lab.getContactEmail() + " for lab " + lab.getName() + ".", e);
        }
        
      }
    } else {
      log.error("Unable to send invoice email to billing contact for lab " + lab.getName()
          + ".Billing contact email is blank.");      
    }
  }
  
  private void sendInvoiceEmail(Session sess, Integer idBillingPeriod, Lab lab, BillingAccount billingAccount, Integer idCoreFacility) throws Exception {
    LogItem li = this.executionLogger.startLogItem("Format Email");
    dictionaryHelper = DictionaryHelper.getInstance(sess);
    
    BillingPeriod billingPeriod = dictionaryHelper.getBillingPeriod(idBillingPeriod);
    CoreFacility coreFacility = (CoreFacility)sess.get(CoreFacility.class, idCoreFacility);

    TreeMap requestMap = new TreeMap();
    TreeMap billingItemMap = new TreeMap();
    TreeMap relatedBillingItemMap = new TreeMap();
    String queryString="from Invoice where idCoreFacility=:idCoreFacility and idBillingPeriod=:idBillingPeriod and idBillingAccount=:idBillingAccount";
    Query query = sess.createQuery(queryString);
    query.setParameter("idCoreFacility", idCoreFacility);
    query.setParameter("idBillingPeriod", idBillingPeriod);
    query.setParameter("idBillingAccount", billingAccount.getIdBillingAccount());
    Invoice invoice = (Invoice)query.uniqueResult();
    ShowBillingInvoiceForm.cacheBillingItemMap(sess, this.getSecAdvisor(), idBillingPeriod, lab.getIdLab(), billingAccount.getIdBillingAccount(), idCoreFacility, billingItemMap, relatedBillingItemMap, requestMap);
    
    BillingInvoiceEmailFormatter emailFormatter = new BillingInvoiceEmailFormatter(sess, coreFacility, billingPeriod, lab, billingAccount, invoice, billingItemMap, relatedBillingItemMap, requestMap);
    String subject = emailFormatter.getSubject();
    
    boolean send = false;
    if (dictionaryHelper.isProductionServer(serverName)) {
      send = true;
    } else {
      if (lab.getContactEmail().equals(dictionaryHelper.getPropertyDictionary(PropertyDictionary.CONTACT_EMAIL_SOFTWARE_TESTER))) {
        send = true;
        subject = "(TEST) " + subject;
      }
    }
    this.executionLogger.endLogItem(li);
    
    li = this.executionLogger.startLogItem("Send email");
    if (send) {
      MailUtil.send(lab.getBillingNotificationEmail(), 
          emailFormatter.getCCList(sess, serverName),
          PropertyDictionaryHelper.getInstance(sess).getCoreFacilityProperty(idCoreFacility, PropertyDictionary.CONTACT_EMAIL_CORE_FACILITY),
          subject, 
          emailFormatter.format(),
          true); 
      
      // Set last email date
      if (invoice != null) {
        invoice.setLastEmailDate(new java.sql.Date(System.currentTimeMillis()));
        sess.save(invoice);
        sess.flush();
      }
    }
    this.executionLogger.endLogItem(li);
  }  

  
  private boolean readyToInvoice(Session sess, Integer idBillingPeriod, Lab lab, Integer idBillingAccount, Integer idCoreFacility) {
    LogItem li = this.executionLogger.startLogItem("readyToInvoice");
    boolean readyToInvoice = false;
    
    if (lab != null && idBillingAccount != null) {
      // Find out if this if all billing items for this lab and billing period
      // are approved.  If so, send out a billing invoice to the 
      // lab's billing contact.
      StringBuffer buf = new StringBuffer();
      buf.append("SELECT DISTINCT bi.codeBillingStatus, count(*) ");
      buf.append("FROM   Request req ");
      buf.append("JOIN   req.billingItems bi ");
      buf.append("WHERE  bi.idBillingPeriod = " + idBillingPeriod + " ");
      buf.append("AND    bi.idLab = " + lab.getIdLab());
      buf.append("AND    bi.idBillingAccount = " + idBillingAccount);
      buf.append("AND    bi.idCoreFacility = " + idCoreFacility);
      buf.append("GROUP BY bi.codeBillingStatus ");
      
      List countList = sess.createQuery(buf.toString()).list();
      int approvedCount = 0;
      int otherCount = 0;
      for(Iterator i = countList.iterator(); i.hasNext();) {
        Object[] countRow = (Object[])i.next();
        String codeBillingStatus = (String)countRow[0];
        Integer count = (Integer)countRow[1];
        
        if (codeBillingStatus.equals(BillingStatus.APPROVED) || codeBillingStatus.equals(BillingStatus.APPROVED_PO)) {
          approvedCount = count.intValue();
        } else {
          otherCount += count.intValue();
        }
        
      }
      if (approvedCount > 0 && otherCount == 0) {
        readyToInvoice = true;
      }
      
    }
    this.executionLogger.endLogItem(li);
    return readyToInvoice;
    
  }
  
  

}