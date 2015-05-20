package hci.gnomex.billing;

import hci.dictionary.model.DictionaryEntry;
import hci.dictionary.model.NullDictionaryEntry;
import hci.dictionary.utility.DictionaryManager;
import hci.gnomex.constants.Constants;
import hci.gnomex.model.BillingItem;
import hci.gnomex.model.BillingPeriod;
import hci.gnomex.model.BillingStatus;
import hci.gnomex.model.CoreFacility;
import hci.gnomex.model.Hybridization;
import hci.gnomex.model.LabeledSample;
import hci.gnomex.model.NumberSequencingCyclesAllowed;
import hci.gnomex.model.Price;
import hci.gnomex.model.PriceCategory;
import hci.gnomex.model.PriceCriteria;
import hci.gnomex.model.Request;
import hci.gnomex.model.RequestCategory;
import hci.gnomex.model.Sample;
import hci.gnomex.model.SequenceLane;
import hci.gnomex.utility.DictionaryHelper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hibernate.Session;


public class IlluminaSeqPlugin implements BillingPlugin {

  public List constructBillingItems(Session sess, String amendState, BillingPeriod billingPeriod, PriceCategory priceCategory, Request request, 
      Set<Sample> samples, Set<LabeledSample> labeledSamples, Set<Hybridization> hybs, Set<SequenceLane> lanes, Map<String, ArrayList<String>> sampleToAssaysMap) {

    List billingItems = new ArrayList<BillingItem>();
    Map seqLaneMap = new HashMap();
    Map seqLaneNoteMap = new HashMap();
    Map<String, NumberSequencingCyclesAllowed> customNumberSequencingCyclesAllowed = new HashMap<String, NumberSequencingCyclesAllowed>();
    DictionaryHelper dh = DictionaryHelper.getInstance(sess);
    
    if (lanes == null || lanes.size() == 0) {
      return billingItems;
    }
    
    // Get custom NumberSequencingCyclesAllowed
    for (Iterator i = DictionaryManager.getDictionaryEntries("hci.gnomex.model.NumberSequencingCyclesAllowed").iterator(); i.hasNext();) {
      DictionaryEntry de = (DictionaryEntry)i.next();
      if (de instanceof NullDictionaryEntry) {
        continue;
      }
      NumberSequencingCyclesAllowed nsca = (NumberSequencingCyclesAllowed)de;
      
      if (nsca.getCodeRequestCategory().equals(request.getCodeRequestCategory()) && nsca.getIsCustom() != null && nsca.getIsCustom().equals("Y")) {
        String key = nsca.getIdNumberSequencingCycles().toString() + "-" + nsca.getIdSeqRunType().toString();
        customNumberSequencingCyclesAllowed.put(key, nsca);
      }
    }
    
    // Count up number of sequence lanes for number seq cycles / seq run type
    for(Iterator i = lanes.iterator(); i.hasNext();) {
      SequenceLane seqLane = (SequenceLane)i.next();
      
      
      String key = seqLane.getIdNumberSequencingCycles() + "-" + seqLane.getIdSeqRunType();
      
      // Keep track of the lanes for # cycles/seq run type
      List theLanes = (List)seqLaneMap.get(key);
      if (theLanes == null) {
        theLanes = new ArrayList();
        seqLaneMap.put(key, theLanes);
      }
      theLanes.add(seqLane);
      
      // Show the seq lane numbers in the notes for the billing item
      String notes = (String)seqLaneNoteMap.get(key);
      if (notes == null) {
        notes = "";
      }
      if (notes.length() > 0) {
        notes += ",";
      } 
      notes += seqLane.getNumber();
      seqLaneNoteMap.put(key, notes);
    }
    
    // Now generate a billing item for each seq lane number sequencing cycles  / seq run type
    for(Iterator i = seqLaneMap.keySet().iterator(); i.hasNext();) {
      String  key = (String)i.next();
      String[] tokens = key.split("-");
      String idNumberSequencingCycles = tokens[0];
      String idSeqRunType             = tokens[1];
      
      List theLanes = (List)seqLaneMap.get(key);
      
      for(Iterator j = theLanes.iterator(); i.hasNext();) {
        SequenceLane theLane = (SequenceLane)j.next();
        Sample sample = theLane.getSample();
        if (sample.getIdOligoBarcode() != null) {
          sample.setBarcodeSequence(dh.getBarcodeSequence(sample.getIdOligoBarcode()));      
        }
        
        // Set the barcodeSequenceB if  idOligoBarcodeB is filled in
        if(sample.getIdOligoBarcodeB() != null){
          sample.setBarcodeSequenceB(dh.getBarcodeSequence(sample.getIdOligoBarcodeB()));
        }
      }
      
      int qty = SequenceLane.getMultiplexLaneCount(theLanes, request.getCreateDate());
      String notes = (String)seqLaneNoteMap.get(key);
      
      // Find the billing price 
      Price price = null;
      for(Iterator i1 = priceCategory.getPrices().iterator(); i1.hasNext();) {
        Price p = (Price)i1.next();
        if (p.getIsActive() != null && p.getIsActive().equals("Y")) {
          for(Iterator i2 = p.getPriceCriterias().iterator(); i2.hasNext();) {
            PriceCriteria criteria = (PriceCriteria)i2.next();
            if (criteria.getFilter1().equals(idSeqRunType)) {
              if (criteria.getFilter2().equals(idNumberSequencingCycles)) {
                price = p;
                break;            
              }
            }
          }
        }
      }
      
      if (customNumberSequencingCyclesAllowed.containsKey(key)) {
        // Custom NumberSequencingCyclesAllowed bill for the whole cell in a single charge.
        RequestCategory category = dh.getRequestCategoryObject(request.getCodeRequestCategory());
        qty = 1;
      }
      
      // Instantiate a BillingItem for the matched price
      if (price != null) {
        BigDecimal theUnitPrice = price.getEffectiveUnitPrice(request.getLab());
        
        BillingItem billingItem = new BillingItem();
        billingItem.setCategory(priceCategory.getName());
        billingItem.setCodeBillingChargeKind(priceCategory.getCodeBillingChargeKind());
        billingItem.setIdBillingPeriod(billingPeriod.getIdBillingPeriod());
        billingItem.setDescription(price.getName());
        billingItem.setQty(Integer.valueOf(qty));
        billingItem.setUnitPrice(theUnitPrice);
        billingItem.setPercentagePrice(new BigDecimal(1));        
        if (qty > 0 && theUnitPrice != null) {
          billingItem.setInvoicePrice(theUnitPrice.multiply(new BigDecimal(qty)));          
        }
        billingItem.setCodeBillingStatus(BillingStatus.PENDING);
        billingItem.setIdRequest(request.getIdRequest());
        billingItem.setIdBillingAccount(request.getIdBillingAccount());
        billingItem.setIdLab(request.getIdLab());
        billingItem.setIdPrice(price.getIdPrice());
        billingItem.setIdPriceCategory(price.getIdPriceCategory());
        billingItem.setSplitType(Constants.BILLING_SPLIT_TYPE_PERCENT_CODE);
        billingItem.setIdCoreFacility(request.getIdCoreFacility());

        // Hold off on saving the notes.  Need to reserve note field
        // for complete date, etc at this time.
        //billingItem.setNotes(notes);

        
        
        billingItems.add(billingItem);
        
      }
    }
    
    
    return billingItems;
  }

  



}
