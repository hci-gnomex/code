package hci.gnomex.utility;

import hci.framework.model.DetailObject;
import hci.gnomex.model.BillingAccount;
import hci.gnomex.model.Request;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.hibernate.Session;
import org.jdom.Document;
import org.jdom.Element;


public class BillingAccountSplitParser extends DetailObject implements Serializable {
  
  protected Document   doc;
  protected List       billingAccounts = new ArrayList();
  protected Map        percentageMap = new HashMap();
  protected Request    request;
  protected Map        invoicePriceMap = new HashMap();
  
  public BillingAccountSplitParser(Document doc) {
    this.doc = doc;
 
  }
  
  public void parse(Session sess) throws Exception{
    
    Element root = this.doc.getRootElement();
    
    for(Iterator i = root.getChildren("Request").iterator(); i.hasNext();) {
      Element node = (Element)i.next();
      String idRequestString = node.getAttributeValue("idRequest");
      request = (Request)sess.load(Request.class, new Integer(idRequestString));
      
      for(Iterator i1 = node.getChildren("BillingAccount").iterator(); i1.hasNext();) {
        Element baNode = (Element)i1.next();
        
        String idBillingAccountString = baNode.getAttributeValue("idBillingAccount");
        String percentageString = baNode.getAttributeValue("percentage");
        percentageString = percentageString.replaceAll("\\%", "");
        String invoicePriceString = baNode.getAttributeValue("invoicePrice");
        invoicePriceString = invoicePriceString.replaceAll("\\$", "").replaceAll(",", "");
        
        Integer percentage = new Integer(percentageString);
        BillingAccount billingAccount = null;
        billingAccount = (BillingAccount)sess.load(BillingAccount.class, new Integer(idBillingAccountString));
        
        billingAccounts.add(billingAccount);
        BigDecimal percentPrice = new BigDecimal(percentage.intValue()).movePointLeft(2);
        percentageMap.put(new Integer(idBillingAccountString), percentPrice);
        
        BigDecimal invoicePrice = new BigDecimal(invoicePriceString);
        invoicePriceMap.put(new Integer(idBillingAccountString), invoicePrice);
      }
    
    }
    
   
  }
  
  public Request getRequest() {
    return request;
  }
  

  
  public BigDecimal getPercentage(Integer idBillingAccount) {
    return (BigDecimal)percentageMap.get(idBillingAccount);
  }
  
  public BigDecimal getInvoicePrice(Integer idBillingAccount) {
    return (BigDecimal)invoicePriceMap.get(idBillingAccount);
  }

  
  public List getBillingAccounts() {
    return this.billingAccounts;
  }
  


}
