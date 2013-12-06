package hci.gnomex.utility;

import hci.framework.model.DetailObject;
import hci.gnomex.model.AnalysisGroup;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.hibernate.Session;
import org.jdom.Document;
import org.jdom.Element;


public class AnalysisGroupParser extends DetailObject implements Serializable {
  
  protected Document    doc;
  protected Map         analysisGroupMap = new HashMap();
  
  public AnalysisGroupParser(Document doc) {
    this.doc = doc;
 
  }
  
  public void parse(Session sess) throws Exception{
    
    Element root = this.doc.getRootElement();
    
    if(root.getChildren("AnalysisGroup").size() == 1) {
    	Element node = root.getChild("AnalysisGroup");
    	if(node.getAttributeValue("idAnalysisGroup").equals("") && node.getAttributeValue("name").equals("")) {
    		return;	//we have a Lab with no AnalysisGroups. Leave the analysisGroupMap empty.
    	}
    }else{    
	    for(Iterator i = root.getChildren("AnalysisGroup").iterator(); i.hasNext();) {
	      Element node = (Element)i.next();
	      
	      String idAnalysisGroupString = node.getAttributeValue("idAnalysisGroup");      
	
	      AnalysisGroup ag = (AnalysisGroup)sess.load(AnalysisGroup.class, new Integer(idAnalysisGroupString));	      
	      
	      analysisGroupMap.put(idAnalysisGroupString, ag);
	    }
    }
  }

  
  public Map getAnalysisGroupMap() {
    return analysisGroupMap;
  }
}
