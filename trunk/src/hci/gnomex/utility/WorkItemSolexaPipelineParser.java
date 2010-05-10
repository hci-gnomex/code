package hci.gnomex.utility;

import hci.gnomex.constants.Constants;
import hci.gnomex.model.FlowCell;
import hci.gnomex.model.FlowCellChannel;
import hci.gnomex.model.WorkItem;

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


public class WorkItemSolexaPipelineParser implements Serializable {
  
  private Document   doc;
  private Map        flowCellChannelMap = new HashMap();
  private List       workItems = new ArrayList();
  
  
  public WorkItemSolexaPipelineParser(Document doc) {
    this.doc = doc;
 
  }
  
  public void parse(Session sess) throws Exception{
    
    Element workItemListNode = this.doc.getRootElement();
    
    
    for(Iterator i = workItemListNode.getChildren("WorkItem").iterator(); i.hasNext();) {
      Element workItemNode = (Element)i.next();
      
      String idFlowCellChannelString   = workItemNode.getAttributeValue("idFlowCellChannel");
      String idWorkItemString = workItemNode.getAttributeValue("idWorkItem");
      
      FlowCellChannel channel = (FlowCellChannel)sess.load(FlowCellChannel.class, new Integer(idFlowCellChannelString));
      WorkItem workItem = (WorkItem)sess.load(WorkItem.class, new Integer(idWorkItemString));
      
      if (workItemNode.getAttributeValue("pipelineStatus") != null && !workItemNode.getAttributeValue("pipelineStatus").equals("")) {
        workItem.setStatus(workItemNode.getAttributeValue("pipelineStatus"));
      } if (workItemNode.getAttributeValue("assembleStatus") != null && !workItemNode.getAttributeValue("assembleStatus").equals("")) {
        workItem.setStatus(workItemNode.getAttributeValue("assembleStatus"));
      } else {
        workItem.setStatus(null);
      }
      
      this.initializeFlowCellChannel(sess, workItemNode, channel);
      
      flowCellChannelMap.put(workItem.getIdWorkItem(), channel);
      workItems.add(workItem);
    }
    
   
  }
  


  
  public FlowCellChannel getFlowCellChannel(Integer idWorkItem) {
    return (FlowCellChannel)flowCellChannelMap.get(idWorkItem);
  }
  
  public List getWorkItems() {
    return workItems;
  }
  
  
  public void resetIsDirty() {
    Element workItemListNode = this.doc.getRootElement();
    
    for(Iterator i = workItemListNode.getChildren("WorkItem").iterator(); i.hasNext();) {
      Element workItemNode = (Element)i.next();
      workItemNode.setAttribute("isDirty", "N");
    }
  }

  
  protected void initializeFlowCellChannel(Session sess, Element n, FlowCellChannel channel) throws Exception {
    if (n.getAttributeValue("pipelineStatus") != null && !n.getAttributeValue("pipelineStatus").equals("")) {
      String status = n.getAttributeValue("pipelineStatus");
      if (status.equals(Constants.STATUS_COMPLETED)) {
        channel.setPipelineDate(new java.sql.Date(System.currentTimeMillis()));      
        channel.setPipelineFailed("N");        
      } else if (status.equals(Constants.STATUS_TERMINATED)) {
        channel.setPipelineDate(null);      
        channel.setPipelineFailed("Y");
      } else if (status.equals(Constants.STATUS_BYPASSED)) {
        channel.setPipelineDate(null);      
        channel.setPipelineFailed("N");
      }
    } else {
      channel.setPipelineDate(null);      
      channel.setPipelineFailed("N");
    }
    
  }


}
