package hci.gnomex.billing;

import hci.gnomex.model.BillingItem;
import hci.gnomex.model.BillingPeriod;
import hci.gnomex.model.BillingStatus;
import hci.gnomex.model.Hybridization;
import hci.gnomex.model.LabeledSample;
import hci.gnomex.model.Price;
import hci.gnomex.model.PriceCategory;
import hci.gnomex.model.PriceCriteria;
import hci.gnomex.model.Request;
import hci.gnomex.model.Sample;
import hci.gnomex.model.SequenceLane;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hibernate.Session;


public class FragmentAnalysisPlugin implements BillingPlugin {

  public List constructBillingItems(Session sess, String amendState, BillingPeriod billingPeriod, PriceCategory priceCategory, Request request, 
      Set<Sample> samples, Set<LabeledSample> labeledSamples, Set<Hybridization> hybs, Set<SequenceLane> lanes, Map<String, ArrayList<String>> sampleToAssaysMap, String billingStatus) {
    

    List billingItems = new ArrayList<BillingItem>();
    
    if (samples == null || samples.size() == 0) {
      return billingItems;
    }
    


    // Count number of genotypes while
    // hashing the assay of wells to figure out how many markers we have.
    int qty = 0;
    //HashMap<Integer, Integer> assayMap = new HashMap<Integer, Integer>();
    for (Sample s : samples) {
      ArrayList<String> assays = sampleToAssaysMap.get(s.getIdSampleString());
      if (assays != null) {
        qty += assays.size();
      }
    }
  
    
  
    //int numberOfAssays = assayMap.size();
    int numberOfAssays = 4;
    
    // Find the price for capillary sequencing
    Price price = null;
    for(Iterator i1 = priceCategory.getPrices().iterator(); i1.hasNext();) {
      Price p = (Price)i1.next();
      if (p.getIsActive() != null && p.getIsActive().equals("Y")) {
        // Pricing for capillary sequencing is tiered.  Look at filter 1
        // on the prices to find the one where the assayCount range applies.
        for(Iterator i2 = p.getPriceCriterias().iterator(); i2.hasNext();) {
          PriceCriteria criteria = (PriceCriteria)i2.next();
          
          if (criteria.getFilter1() == null || criteria.getFilter1().equals("")) {
            continue;
          }
          
          Integer assayCount1 = null;
          Integer assayCount2 = null;
          
          // Range check
          if (criteria.getFilter1().contains("-")) {
            String[] tokens = criteria.getFilter1().split("-");
            if (tokens.length < 2) {
              continue;
            }
            
            assayCount1 = Integer.valueOf(tokens[0]);
            assayCount2 = Integer.valueOf(tokens[1]);

            // If the assayCount falls within the range, this is the price that applies
            if (numberOfAssays >= assayCount1.intValue() && numberOfAssays <= assayCount2.intValue()) {
              price = p;
              break;
            }
          } else if (criteria.getFilter1().contains("+")) {
            // Lower limit check
            String tokens[] =  criteria.getFilter1().split("\\+");

            assayCount1 = Integer.valueOf(tokens[0]);
            
            if (numberOfAssays == assayCount1.intValue()) {
              price = p;
              break;
            } 
          } else  {
              // Just one number was provided so filter has to equal the value
              Integer assayCount = Integer.valueOf(criteria.getFilter1());
              
              if (numberOfAssays == assayCount.intValue()) {
                price = p;
                break;
              }
          }
        }
      }
    }


    // Instantiate a BillingItem for the matched billing price
    if (price != null) {
      BigDecimal theUnitPrice = price.getEffectiveUnitPrice(request.getLab());
      
      BillingItem billingItem = new BillingItem();
      billingItem.setCodeBillingChargeKind(priceCategory.getCodeBillingChargeKind());
      billingItem.setIdBillingPeriod(billingPeriod.getIdBillingPeriod());
      billingItem.setDescription(price.getName());
      billingItem.setQty(qty); 
      billingItem.setUnitPrice(theUnitPrice);
      billingItem.setPercentagePrice(new BigDecimal(1));        
      if (qty > 0 && theUnitPrice != null) {      
        billingItem.setInvoicePrice(theUnitPrice.multiply(new BigDecimal(qty)));
      }
      billingItem.setCodeBillingStatus(billingStatus);
      if (!billingStatus.equals(BillingStatus.NEW) && !billingStatus.equals(BillingStatus.PENDING)) {
        billingItem.setCompleteDate(new java.sql.Date(System.currentTimeMillis()));
      }
      billingItem.setIdRequest(request.getIdRequest());
      billingItem.setIdLab(request.getIdLab());
      billingItem.setIdBillingAccount(request.getIdBillingAccount());        
      billingItem.setIdPrice(price.getIdPrice());
      billingItem.setIdPriceCategory(priceCategory.getIdPriceCategory());
      billingItem.setCategory(priceCategory.getName());
      billingItem.setIdCoreFacility(request.getIdCoreFacility());
   
      
      billingItems.add(billingItem);
      
    }
    
    
    return billingItems;
  }
  
}