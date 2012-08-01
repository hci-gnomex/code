package hci.gnomex.utility;

import hci.gnomex.model.BillingAccount;
import hci.framework.model.DetailObject;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.hibernate.Session;
import org.jdom.Document;
import org.jdom.Element;


public class BillingAccountParser extends DetailObject implements Serializable {
  
  protected Document   doc;
  protected Map        billingAccountMap = new HashMap();
  
  public BillingAccountParser(Document doc) {
    this.doc = doc;
 
  }
  
  public void parse(Session sess) throws Exception{
    
    Element root = this.doc.getRootElement();
    
    
    for(Iterator i = root.getChildren("BillingAccount").iterator(); i.hasNext();) {
      Element node = (Element)i.next();
      
      String idBillingAccountString = node.getAttributeValue("idBillingAccount");
      BillingAccount billingAccount = null;
      if (idBillingAccountString.startsWith("BillingAccount")) {
        billingAccount = new BillingAccount();
      } else {
        billingAccount = (BillingAccount)sess.load(BillingAccount.class, new Integer(idBillingAccountString));
      }
      
      
      
      this.initializeBillingAccount(sess, node, billingAccount);
      
      billingAccountMap.put(idBillingAccountString, billingAccount);
    }
    
   
  }
  
  protected void initializeBillingAccount(Session sess, Element n, BillingAccount billingAccount) throws Exception {

    if (n.getAttributeValue("accountName") != null && !n.getAttributeValue("accountName").equals("")) {
      billingAccount.setAccountName(n.getAttributeValue("accountName"));
    } 
    if (n.getAttributeValue("accountNumberBus") != null) {
      billingAccount.setAccountNumberBus(n.getAttributeValue("accountNumberBus"));
    } 
    if (n.getAttributeValue("accountNumberOrg") != null) {
      billingAccount.setAccountNumberOrg(n.getAttributeValue("accountNumberOrg"));
    } 
    if (n.getAttributeValue("accountNumberFund") != null) {
      billingAccount.setAccountNumberFund(n.getAttributeValue("accountNumberFund"));
    } 
    if (n.getAttributeValue("accountNumberActivity") != null) {
      billingAccount.setAccountNumberActivity(n.getAttributeValue("accountNumberActivity"));
    } 
    if (n.getAttributeValue("accountNumberProject") != null) {
      billingAccount.setAccountNumberProject(n.getAttributeValue("accountNumberProject"));
    } 
    if (n.getAttributeValue("accountNumberAccount") != null) {
      billingAccount.setAccountNumberAccount(n.getAttributeValue("accountNumberAccount"));
    } 
    if (n.getAttributeValue("accountNumberAu") != null) {
      billingAccount.setAccountNumberAu(n.getAttributeValue("accountNumberAu"));
    } 
    if (n.getAttributeValue("accountNumberYear") != null) {
      billingAccount.setAccountNumberYear(n.getAttributeValue("accountNumberYear"));
    } 
    
    if (n.getAttributeValue("startDate") != null && !n.getAttributeValue("startDate").equals("")) {
      billingAccount.setStartDate(this.parseDate(n.getAttributeValue("startDate")));
    } else {
      billingAccount.setStartDate(null);
    }
    
    if (n.getAttributeValue("expirationDate") != null && !n.getAttributeValue("expirationDate").equals("")) {
      billingAccount.setExpirationDate(this.parseDate(n.getAttributeValue("expirationDate")));
    } else {
      billingAccount.setExpirationDate(null);
    }
    
    if (n.getAttributeValue("idFundingAgency") != null && !n.getAttributeValue("idFundingAgency").equals("")) {
      billingAccount.setIdFundingAgency(new Integer(n.getAttributeValue("idFundingAgency")));
    } else {
      billingAccount.setIdFundingAgency(null);
    }
    if (n.getAttributeValue("isPO") != null && !n.getAttributeValue("isPO").equals("")) {
        billingAccount.setIsPO(n.getAttributeValue("isPO"));
    } else {
      billingAccount.setIsPO("N");
    }
    
    if (n.getAttributeValue("totalDollarAmountDisplay") != null && !n.getAttributeValue("totalDollarAmountDisplay").equals("")) {
      String totalDollarAmount = n.getAttributeValue("totalDollarAmountDisplay");
      totalDollarAmount = totalDollarAmount.replaceAll("\\$", "");
      totalDollarAmount = totalDollarAmount.replaceAll(",", "");
      billingAccount.setTotalDollarAmount(new BigDecimal(totalDollarAmount));
    }

    if (n.getAttributeValue("shortAcct") != null && !n.getAttributeValue("shortAcct").equals("")) {
      billingAccount.setShortAcct(n.getAttributeValue("shortAcct"));
    } else {
      billingAccount.setShortAcct(null);
    }
        
    if (n.getAttributeValue("isApproved") != null && !n.getAttributeValue("isApproved").equals("")) {
      String isApproved = n.getAttributeValue("isApproved");
      
      // If we have toggled from not approved to approved, set the approved date
      if (isApproved.equals("Y") && billingAccount.getIdBillingAccount() != null) {
        if (billingAccount.getIsApproved() == null || 
            billingAccount.getIsApproved().equals("") ||
            billingAccount.getIsApproved().equalsIgnoreCase("N")) {
          billingAccount.setApprovedDate(new java.sql.Date(System.currentTimeMillis()));
          billingAccount.isJustApproved(true);
        }
      }
      billingAccount.setIsApproved(isApproved);
      
      if (n.getAttributeValue("submitterEmail") != null) {
        billingAccount.setSubmitterEmail(n.getAttributeValue("submitterEmail"));
      }
  } else {
    billingAccount.setIsApproved("N");
  }
  }

  
  public Map getBillingAccountMap() {
    return billingAccountMap;
  }

  
  public void setBillingAccountMap(Map billingAccountMap) {
    this.billingAccountMap = billingAccountMap;
  }
  


}
