package hci.gnomex.controller;

import hci.framework.control.Command;
import hci.framework.control.RollBackCommandException;
import hci.gnomex.security.SecurityAdvisor;
import hci.framework.security.UnknownPermissionException;
import hci.gnomex.constants.Constants;
import hci.gnomex.model.BillingAccount;
import hci.gnomex.model.BillingItem;
import hci.gnomex.model.BillingPeriod;
import hci.gnomex.model.BillingStatus;
import hci.gnomex.model.CoreFacility;
import hci.gnomex.model.Lab;
import hci.gnomex.model.PropertyDictionary;
import hci.gnomex.model.Request;
import hci.gnomex.utility.BillingInvoiceEmailFormatter;
import hci.gnomex.utility.BillingInvoiceHTMLFormatter;
import hci.gnomex.utility.DictionaryHelper;
import hci.gnomex.utility.MailUtil;
import hci.gnomex.utility.PropertyDictionaryHelper;

import java.io.Serializable;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.naming.NamingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.hibernate.Session;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.output.XMLOutputter;


public class ShowBillingInvoiceForm extends GNomExCommand implements Serializable {
  
  private static final String     ACTION_SHOW  = "show";
  private static final String     ACTION_EMAIL = "email";
  
  private static org.apache.log4j.Logger log = org.apache.log4j.Logger.getLogger(ShowBillingInvoiceForm.class);  
  public String SUCCESS_JSP = "/getHTML.jsp";

  private String           serverName;
  
  private Integer          idLab;
  private Integer          idBillingAccount;
  private Integer          idBillingPeriod;
  private Integer          idCoreFacility;
  private String           action = "show";
  
 
  public void validate() {
  }
  
  public void loadCommand(HttpServletRequest request, HttpSession session) {

    if (request.getParameter("idLab") != null) {
      idLab = new Integer(request.getParameter("idLab"));
    } else {
      this.addInvalidField("idLab", "idLab is required");
    }
    if (request.getParameter("idBillingAccount") != null) {
      idBillingAccount = new Integer(request.getParameter("idBillingAccount"));
    } else {
      this.addInvalidField("idBillingAccount", "idBillingAccount is required");
    }
    if (request.getParameter("idBillingPeriod") != null) {
      idBillingPeriod = new Integer(request.getParameter("idBillingPeriod"));
    } else {
      this.addInvalidField("idBillingPeriod", "idBillingPeriod is required");
    }
    if (request.getParameter("idCoreFacility") != null) {
      idCoreFacility = new Integer(request.getParameter("idCoreFacility"));
    } else {
      this.addInvalidField("idCoreFacility", "idCoreFacility is required");
    }
    if (request.getParameter("action") != null && !request.getParameter("action").equals("")) {
      action = request.getParameter("action");
    }
    serverName = request.getServerName();
  }

  public Command execute() throws RollBackCommandException {
    
    try {
      
   
      Session sess = this.getSecAdvisor().getReadOnlyHibernateSession(this.getUsername());
      
     
      DictionaryHelper dh = DictionaryHelper.getInstance(sess);
    
      

      if (this.isValid()) {
        if (this.getSecAdvisor().hasPermission(SecurityAdvisor.CAN_MANAGE_BILLING)) { 
          
          BillingPeriod billingPeriod = dh.getBillingPeriod(idBillingPeriod);
          Lab lab = (Lab)sess.get(Lab.class, idLab);
          BillingAccount billingAccount = (BillingAccount)sess.get(BillingAccount.class, idBillingAccount);
          CoreFacility coreFacility = (CoreFacility)sess.get(CoreFacility.class, idCoreFacility);
          
          TreeMap requestMap = new TreeMap();
          TreeMap billingItemMap = new TreeMap();
          TreeMap relatedBillingItemMap = new TreeMap();
          cacheBillingItemMap(sess, this.getSecAdvisor(), idBillingPeriod, idLab, idBillingAccount, idCoreFacility, billingItemMap, relatedBillingItemMap, requestMap);
          
          
          if (action.equals(ACTION_SHOW)) {
            this.makeInvoiceReport(sess, billingPeriod, lab, billingAccount, billingItemMap, relatedBillingItemMap, requestMap);
          } else if (action.equals(ACTION_EMAIL)) {
            this.sendInvoiceEmail(sess, lab.getContactEmail(), coreFacility, billingPeriod, lab, billingAccount, billingItemMap, relatedBillingItemMap, requestMap);
          }
          
        } else {
          this.addInvalidField("Insufficient permissions", "Insufficient permission to show flow cell report.");
        }
        
      }
    
      if (isValid()) {
        setResponsePage(this.SUCCESS_JSP);
      } else {
        setResponsePage(this.ERROR_JSP);
      }
    
    }catch (UnknownPermissionException e){
      log.error("An exception has occurred in ShowBillingInvoiceForm ", e);
      e.printStackTrace();
      throw new RollBackCommandException(e.getMessage());
        
    }catch (NamingException e){
      log.error("An exception has occurred in ShowBillingInvoiceForm ", e);
      e.printStackTrace();
      throw new RollBackCommandException(e.getMessage());
        
    }catch (SQLException e) {
      log.error("An exception has occurred in ShowBillingInvoiceForm ", e);
      e.printStackTrace();
      throw new RollBackCommandException(e.getMessage());
      
    } catch (Exception e) {
      log.error("An exception has occurred in ShowBillingInvoiceForm ", e);
      e.printStackTrace();
      throw new RollBackCommandException(e.getMessage());
    } finally {
      try {
        this.getSecAdvisor().closeReadOnlyHibernateSession();    
      } catch(Exception e) {
        
      }
    }
    
    return this;
  }
  
  
  public static void cacheBillingItemMap(Session sess, SecurityAdvisor secAdvisor, Integer idBillingPeriod, Integer idLab, Integer idBillingAccount, Integer idCoreFacility, Map billingItemMap, Map relatedBillingItemMap, Map requestMap)
    throws Exception {
    StringBuffer buf = new StringBuffer();
    buf.append("SELECT req, bi ");
    buf.append("FROM   Request req ");
    buf.append("JOIN   req.billingItems bi ");
    buf.append("WHERE  bi.idLab = " + idLab + " ");
    buf.append("AND    bi.idBillingAccount = " + idBillingAccount + " ");
    buf.append("AND    bi.idBillingPeriod = " + idBillingPeriod + " ");
    buf.append("AND    bi.idCoreFacility = " + idCoreFacility + " ");
    buf.append("AND    bi.codeBillingStatus in ('" + BillingStatus.COMPLETED + "', '" + BillingStatus.APPROVED + "', '" + BillingStatus.APPROVED_PO + "')");
    
    if (!secAdvisor.hasPermission(SecurityAdvisor.CAN_ADMINISTER_ALL_CORE_FACILITIES)) {
      buf.append(" AND ");
      secAdvisor.appendCoreFacilityCriteria(buf, "bi");
      buf.append(" ");
    }
    
    buf.append("ORDER BY req.number, bi.idBillingItem ");
    
    List results = sess.createQuery(buf.toString()).list();
    
    
    for(Iterator i = results.iterator(); i.hasNext();) {
      Object[] row = (Object[])i.next();
      Request req    =  (Request)row[0];
      BillingItem bi =  (BillingItem)row[1];
      
      // Exclude any requests that are have
      // pending billing items.
      boolean hasPendingItems = false;
      for(Iterator i1 = req.getBillingItems().iterator(); i1.hasNext();) {
        BillingItem item = (BillingItem)i1.next();
        
        if (item.getIdBillingPeriod().equals(idBillingPeriod) &&
            item.getIdBillingAccount().equals(idBillingAccount)) {
          if (item.getCodeBillingStatus().equals(BillingStatus.PENDING)) {
            hasPendingItems = true;
            break;
          }          
        }
      }
      if (hasPendingItems) {
        continue;
      }
      
      
      requestMap.put(req.getNumber(), req);
      
      List billingItems = (List)billingItemMap.get(req.getNumber());
      if (billingItems == null) {
        billingItems = new ArrayList();
        billingItemMap.put(req.getNumber(), billingItems);
      }
      billingItems.add(bi);
    }    
    
    buf = new StringBuffer();
    buf.append("SELECT req, bi ");
    buf.append("FROM   Request req ");
    buf.append("JOIN   req.billingItems bi ");
    buf.append("WHERE  bi.idBillingAccount != " + idBillingAccount + " ");
    buf.append("AND    bi.idBillingPeriod = " + idBillingPeriod + " ");
    buf.append("AND    bi.idRequest in (");
    Boolean first = true;
    for(Iterator i = requestMap.keySet().iterator(); i.hasNext();) {
      String requestNumber = (String)i.next();      
      Request request = (Request)requestMap.get(requestNumber);
      if (!first) {
        buf.append(", ");
      }
      first = false;
      buf.append(request.getIdRequest().toString());
    }
    buf.append(")");
    
    if (!secAdvisor.hasPermission(SecurityAdvisor.CAN_ADMINISTER_ALL_CORE_FACILITIES)) {
      buf.append(" AND ");
      secAdvisor.appendCoreFacilityCriteria(buf, "req");
      buf.append(" ");
    }
    
    buf.append("ORDER BY req.number, bi.idBillingAccount, bi.idBillingItem ");
    
    results = sess.createQuery(buf.toString()).list();
    
    for(Iterator i = results.iterator(); i.hasNext();) {
      Object[] row = (Object[])i.next();
      Request req    =  (Request)row[0];
      BillingItem bi =  (BillingItem)row[1];
      
      List billingItems = (List)relatedBillingItemMap.get(req.getNumber());
      if (billingItems == null) {
        billingItems = new ArrayList();
        relatedBillingItemMap.put(req.getNumber(), billingItems);
      }
      billingItems.add(bi);
    }    
  }
  
  private void makeInvoiceReport(Session sess, BillingPeriod billingPeriod, 
      Lab lab, BillingAccount billingAccount, 
      Map billingItemMap, Map relatedBillingItemMap, Map requestMap) throws Exception {
    
    DictionaryHelper dh = DictionaryHelper.getInstance(sess);
    BillingInvoiceHTMLFormatter formatter = new BillingInvoiceHTMLFormatter(
        PropertyDictionaryHelper.getInstance(sess).getCoreFacilityProperty(idCoreFacility, PropertyDictionary.CORE_FACILITY_NAME),
        PropertyDictionaryHelper.getInstance(sess).getCoreFacilityProperty(idCoreFacility, PropertyDictionary.CONTACT_NAME_CORE_FACILITY),
        PropertyDictionaryHelper.getInstance(sess).getCoreFacilityProperty(idCoreFacility, PropertyDictionary.CONTACT_PHONE_CORE_FACILITY),
        billingPeriod, 
        lab, billingAccount, billingItemMap, relatedBillingItemMap, requestMap);

    Element root = new Element("HTML");
    Document doc = new Document(root);

    Element head = new Element("HEAD");
    root.addContent(head);

    Element link = new Element("link");
    link.setAttribute("rel", "stylesheet");
    link.setAttribute("type", "text/css");
    link.setAttribute("href", Constants.INVOICE_FORM_CSS);
    head.addContent(link);

    Element title = new Element("TITLE");
    title.addContent("Billing Invoice - " + lab.getName() + 
                     " " + billingAccount.getAccountName());
    head.addContent(title);

    Element body = new Element("BODY");
    root.addContent(body);

    Element center = new Element("CENTER");
    body.addContent(center);
      
      
    // Show print and email link
    Element emailLink = new Element("A");
    emailLink.setAttribute("HREF",
        "ShowBillingInvoiceForm.gx?idLab=" + idLab +
        "&idBillingAccount=" + idBillingAccount + 
        "&idBillingPeriod=" + idBillingPeriod +
        "&idCoreFacility=" + idCoreFacility +
        "&action=" + ACTION_EMAIL);
    String contactEmail = lab.getContactEmail();
    if (contactEmail == null || contactEmail.equals("")) {
      contactEmail = "billing contact";
    }
    emailLink.addContent("Email " + contactEmail);

    Element printLink = new Element("A");
    printLink.setAttribute("HREF", "javascript:window.print()");
    printLink.addContent("Print page");

    Element linkTable = new Element("TABLE");   
    Element row = new Element("TR");
    linkTable.addContent(row);

    Element cell = new Element("TD");
    cell.setAttribute("ALIGN", "RIGHT");
    row.addContent(cell);
    cell.addContent(emailLink);    
    cell.addContent("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;");
    cell.addContent(printLink);            

    center.addContent(linkTable);


    Element center1 = new Element("CENTER");
    body.addContent(center1);

    center1.addContent(formatter.makeHeader());

    body.addContent(new Element("BR"));

    Element center2 = new Element("CENTER");
    body.addContent(center2);

    if (!billingItemMap.isEmpty()) {
      center2.addContent(formatter.makeDetail());          
    }



    XMLOutputter out = new org.jdom.output.XMLOutputter();
    out.setOmitEncoding(true);
    this.xmlResult = out.outputString(doc);
    this.xmlResult = this.xmlResult.replaceAll("&amp;", "&");
    this.xmlResult = this.xmlResult.replaceAll("�",     "&micro");
   
  }
  
  private void sendInvoiceEmail(Session sess, String contactEmail, CoreFacility coreFacility,
      BillingPeriod billingPeriod, Lab lab,
      BillingAccount billingAccount, Map billingItemMap, Map relatedBillingItemMap,
      Map requestMap) throws Exception {
    
    DictionaryHelper dh = DictionaryHelper.getInstance(sess);
    
    BillingInvoiceEmailFormatter emailFormatter = new BillingInvoiceEmailFormatter(sess, coreFacility,
        billingPeriod, lab, billingAccount, billingItemMap, relatedBillingItemMap, requestMap);
    String subject = emailFormatter.getSubject();
    
    String note = "&nbsp";
    boolean send = false;
    if (contactEmail != null && !contactEmail.equals("")) {
      if (dh.isProductionServer(serverName)) {
        send = true;
      } else {
        if (contactEmail.equals(dh.getPropertyDictionary(PropertyDictionary.CONTACT_EMAIL_SOFTWARE_TESTER))) {
          send = true;
          subject = "(TEST) " + subject;
        } else {
          note = "Bypassing send on test system.";
        }
      }     
    } else {
      note = "Unable to email billing invoice. Billing contact email is blank for " + lab.getName();
    }
    
    if (send) {
      try {
        MailUtil.send(contactEmail, 
          emailFormatter.getCCList(sess, serverName),
          PropertyDictionaryHelper.getInstance(sess).getCoreFacilityProperty(idCoreFacility, PropertyDictionary.CONTACT_EMAIL_CORE_FACILITY),
          subject, 
          emailFormatter.format(),
          true);
        
        note = "Billing invoice emailed to " + contactEmail + ".";
        
      } catch( Exception e) {
        log.error("Unable to send invoice email to " + contactEmail, e);
        note = "Unable to email invoice to " + contactEmail + " due to the following error: " + e.toString();        
      } 
    }
    Element root = new Element("HTML");
    Document doc = new Document(root);

    Element head = new Element("HEAD");
    root.addContent(head);

    Element link = new Element("link");
    link.setAttribute("rel", "stylesheet");
    link.setAttribute("type", "text/css");
    link.setAttribute("href", "invoiceForm.css");
    head.addContent(link);

    Element title = new Element("TITLE");
    title.addContent("Email Billing Invoice - " + lab.getName() + 
                     " " + billingAccount.getAccountName());
    head.addContent(title);

    Element body = new Element("BODY");
    root.addContent(body);

    Element h = new Element("H3");
    h.addContent(note);   
    body.addContent(h);      
    
    XMLOutputter out = new org.jdom.output.XMLOutputter();
    out.setOmitEncoding(true);
    this.xmlResult = out.outputString(doc);
  }  

  

  /**
   *  The callback method called after the loadCommand, and execute methods,
   *  this method allows you to manipulate the HttpServletResponse object prior
   *  to forwarding to the result JSP (add a cookie, etc.)
   *
   *@param  request  The HttpServletResponse for the command
   *@return          The processed response
   */
  public HttpServletResponse setResponseState(HttpServletResponse response) {
    return response;
  } 
 

  
}