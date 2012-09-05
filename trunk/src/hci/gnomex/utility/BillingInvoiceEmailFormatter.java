package hci.gnomex.utility;

import hci.framework.model.DetailObject;
import hci.gnomex.constants.Constants;
import hci.gnomex.controller.GNomExFrontController;
import hci.gnomex.model.AppUser;
import hci.gnomex.model.BillingAccount;
import hci.gnomex.model.BillingItem;
import hci.gnomex.model.BillingPeriod;
import hci.gnomex.model.CoreFacility;
import hci.gnomex.model.Invoice;
import hci.gnomex.model.Lab;
import hci.gnomex.model.PropertyDictionary;
import hci.gnomex.model.Request;
import hci.gnomex.model.UserPermissionKind;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.hibernate.Query;
import org.hibernate.Session;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.output.XMLOutputter;


public class BillingInvoiceEmailFormatter extends DetailObject{
  
  private BillingPeriod  billingPeriod;
  private Lab            lab;
  private BillingAccount billingAccount;
  private CoreFacility   coreFacility;
  private Invoice        invoice;
  private Map            billingItemMap; 
  private Map            requestMap;
  private Map            relatedBillingItemMap;
  private String         coreFacilityName;
  private String         coreFacilityContactName;
  private String         coreFacilityContactPhone;


  private DictionaryHelper dictionaryHelper;
  
  protected boolean       includeMicroarrayCoreNotes = true;

  public BillingInvoiceEmailFormatter(Session sess, CoreFacility coreFacility, BillingPeriod billingPeriod, Lab lab, BillingAccount billingAccount, Invoice invoice, Map billingItemMap, Map relatedBillingItemMap, Map requestMap) { 
    this.coreFacility   = coreFacility;
    this.billingPeriod  = billingPeriod;
    this.lab            = lab;
    this.billingAccount = billingAccount;
    this.invoice        = invoice;
    this.billingItemMap = billingItemMap;
    this.requestMap     = requestMap;
    this.relatedBillingItemMap = relatedBillingItemMap;
    this.coreFacilityName = PropertyDictionaryHelper.getInstance(sess).getCoreFacilityProperty(coreFacility.getIdCoreFacility(), PropertyDictionary.CORE_FACILITY_NAME);
    this.coreFacilityContactName = PropertyDictionaryHelper.getInstance(sess).getCoreFacilityProperty(coreFacility.getIdCoreFacility(), PropertyDictionary.CONTACT_NAME_CORE_FACILITY);
    this.coreFacilityContactPhone = PropertyDictionaryHelper.getInstance(sess).getCoreFacilityProperty(coreFacility.getIdCoreFacility(), PropertyDictionary.CONTACT_PHONE_CORE_FACILITY);
 
    this.dictionaryHelper = DictionaryHelper.getInstance(sess);
  }
  
  public String getSubject() {
    String acctNum = billingAccount.getAccountNumber();
    if (acctNum == null || !acctNum.equals("")) {
      acctNum = billingAccount.getAccountName();
    }
    String subject = billingPeriod.getBillingPeriod() + " Microarray Chargeback" +
                     " - " + acctNum;
    return subject;
  }
  
  public String getCCList(Session sess, String serverName) {
    String ccList = "";
    if (billingAccount.getIsPO() != null && billingAccount.getIsPO().equals("Y")) {
      String queryString = "select distinct user from AppUser user join user.managingCoreFacilities cores where cores.idCoreFacility = :id";
      Query query = sess.createQuery(queryString);
      query.setParameter("id", coreFacility.getIdCoreFacility());
      List managers = query.list();
      for (Iterator j = managers.iterator(); j.hasNext();) {
        AppUser manager = (AppUser)j.next();
        Boolean send = false;
        if (manager.getCodeUserPermissionKind().equals(UserPermissionKind.BILLING_PERMISSION_KIND)) {
          if (manager.getEmail() != null && !manager.getEmail().equals("")) {
            if (dictionaryHelper.isProductionServer(serverName)) {
              send = true;
            } else {
              if (manager.getEmail().equals(dictionaryHelper.getPropertyDictionary(PropertyDictionary.CONTACT_EMAIL_SOFTWARE_TESTER))) {
                send = true;
              }
            }     
          }
        }
        if (send) {
          if (ccList.length() != 0) {
            ccList += ", ";
          }
          ccList += manager.getEmail();
        }
      }
    }
    if (ccList.length() == 0) {
      ccList = null;
    }
    return ccList;
  }

  public String format() throws Exception {

    BillingInvoiceHTMLFormatter formatter = new BillingInvoiceHTMLFormatter(coreFacilityName, coreFacilityContactName, coreFacilityContactPhone,
        billingPeriod, lab, billingAccount, invoice, billingItemMap, relatedBillingItemMap, requestMap);
    
    Element root = new Element("HTML");
    Document doc = new Document(root);
    
    Element center1 = formatHeader(formatter, root);
    
    center1.addContent(formatter.makeDetail());

    XMLOutputter out = new org.jdom.output.XMLOutputter();
    String buf = out.outputString(doc);
    buf = buf.replaceAll("&amp;",    "&");
    buf = buf.replaceAll("�",        "&micro");
    buf = buf.replaceAll("&gt;",     ">");
    buf = buf.replaceAll("&lt;",     "<");
    
    return buf;
  }
  
 
  

  protected Element formatHeader(BillingInvoiceHTMLFormatter formatter, Element root) {

    
    Element head = new Element("HEAD");
    root.addContent(head);

    Element title = new Element("TITLE");
    title.addContent(getSubject());
    head.addContent(title);

    Element style = new Element("style");
    style.setAttribute("type", "text/css");
    style.addContent(this.getCascadingStyleSheet());
    head.addContent(style);
    
    Element body = new Element("BODY");
    root.addContent(body);


    body.addContent(formatter.makeIntroNote());
    body.addContent(new Element("HR"));    
    body.addContent(formatter.makeHeader());
    
    
    return body;
  }
  
  private String getCascadingStyleSheet() {
    StringBuffer buf = new StringBuffer();
    BufferedReader input =  null;
    try {
      input = new BufferedReader(new FileReader(GNomExFrontController.getWebContextPath() + Constants.INVOICE_FORM_CSS));
    } catch (FileNotFoundException ex) {
      System.out.println(ex.toString());
    }
    if (input != null) {
      try {
        String line = null; 
        while (( line = input.readLine()) != null){
          buf.append(line);
          buf.append(System.getProperty("line.separator"));
        }
      }
      catch (IOException ex){
        ex.printStackTrace();
      }
      finally {
        try {
          input.close();          
        } catch (IOException e) {
        }
      }
      
    }
    return buf.toString();
  }

}
