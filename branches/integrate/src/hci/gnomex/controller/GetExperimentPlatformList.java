package hci.gnomex.controller;

import hci.framework.control.Command;
import hci.framework.control.RollBackCommandException;
import hci.framework.model.DetailObject;
import hci.framework.utilities.XMLReflectException;
import hci.gnomex.model.Application;
import hci.gnomex.model.NumberSequencingCyclesAllowed;
import hci.gnomex.model.RequestCategory;
import hci.gnomex.model.RequestCategoryApplication;
import hci.gnomex.model.SampleType;
import hci.gnomex.model.SampleTypeApplication;
import hci.gnomex.model.SampleTypeRequestCategory;
import hci.gnomex.model.SeqLibProtocolApplication;
import hci.gnomex.utility.DictionaryHelper;

import java.io.Serializable;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.naming.NamingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.hibernate.Session;
import org.jdom.Document;
import org.jdom.Element;


public class GetExperimentPlatformList extends GNomExCommand implements Serializable {
  
  // the static field for logging in Log4J
  private static org.apache.log4j.Logger log = org.apache.log4j.Logger.getLogger(GetExperimentPlatformList.class);

  private List<SampleType> sampleTypes = new ArrayList<SampleType>();
  private List <Application> applications = new ArrayList<Application>();
  private HashMap<String, Map<Integer, ?>> sampleTypeMap = new HashMap<String, Map<Integer, ?>>();
  private HashMap<String, Map<String, RequestCategoryApplication>> applicationMap = new HashMap<String, Map<String, RequestCategoryApplication>>();
  private HashMap<Integer, Map<Integer, ?>> sampleTypeXMethodMap = new HashMap<Integer, Map<Integer, ?>>();
  private HashMap<Integer, Map<String, ?>> sampleTypeXApplicationMap = new HashMap<Integer, Map<String, ?>>();
  private HashMap<String, Map<Integer, ?>> applicationXSeqLibProtocolMap = new HashMap<String, Map<Integer, ?>>();
  private HashMap<String, List<NumberSequencingCyclesAllowed>> numberSeqCyclesAllowedMap = new HashMap<String, List<NumberSequencingCyclesAllowed>>();
  
  
  public void validate() {
  }
  
  public void loadCommand(HttpServletRequest request, HttpSession session) {

    
    if (isValid()) {
      setResponsePage(this.SUCCESS_JSP);
    } else {
      setResponsePage(this.ERROR_JSP);
    }

  }

  public Command execute() throws RollBackCommandException {

    try {

      Session sess = this.getSecAdvisor().getReadOnlyHibernateSession(this.getUsername());

      hashSupportingDictionaries(sess);
      DictionaryHelper dh = DictionaryHelper.getInstance(sess);
      
      Document doc = new Document(new Element("ExperimentPlatformList"));

      List platforms = sess.createQuery("SELECT rc from RequestCategory rc order by rc.requestCategory").list();

      for(Iterator i = platforms.iterator(); i.hasNext();) {
        RequestCategory rc = (RequestCategory)i.next();
        this.getSecAdvisor().flagPermissions(rc);
        Element node = rc.toXMLDocument(null, DetailObject.DATE_OUTPUT_SQL).getRootElement();
        doc.getRootElement().addContent(node);
        
        Element listNode = new Element("sampleTypes");
        node.addContent(listNode);
        for(Iterator i1 = sampleTypes.iterator(); i1.hasNext();) {
          SampleType st = (SampleType)i1.next();
          this.getSecAdvisor().flagPermissions(st);
          Element sampleTypeNode = st.toXMLDocument(null, DetailObject.DATE_OUTPUT_SQL).getRootElement();
          listNode.addContent(sampleTypeNode);
          sampleTypeNode.setAttribute("isSelected", isAssociated(rc, st) ? "Y" : "N");
          sampleTypeNode.setAttribute("codeApplications", getCodeApplications(st));
        }
        
        
        listNode = new Element("applications");
        node.addContent(listNode);
        for(Iterator i1 = applications.iterator(); i1.hasNext();) {
          Application a = (Application)i1.next();
          this.getSecAdvisor().flagPermissions(a);
          Element applicationNode = a.toXMLDocument(null, DetailObject.DATE_OUTPUT_SQL).getRootElement();
          listNode.addContent(applicationNode);
          applicationNode.setAttribute("isSelected", isAssociated(rc, a) ? "Y" : "N");
          applicationNode.setAttribute("idSeqLibProtocols", getIdSeqLibProtocols(a));
          RequestCategoryApplication x = (RequestCategoryApplication)getRequestCategoryApplication(rc, a);
          applicationNode.setAttribute("idLabelingProtocolDefault", x != null && x.getIdLabelingProtocolDefault() != null ? x.getIdLabelingProtocolDefault().toString() : "");
          applicationNode.setAttribute("idHybProtocolDefault", x != null && x.getIdHybProtocolDefault() != null ? x.getIdHybProtocolDefault().toString() : "");
          applicationNode.setAttribute("idScanProtocolDefault", x != null && x.getIdScanProtocolDefault() != null ? x.getIdScanProtocolDefault().toString() : "");
          applicationNode.setAttribute("idFeatureExtractionProtocolDefault", x != null && x.getIdFeatureExtractionProtocolDefault() != null ? x.getIdFeatureExtractionProtocolDefault().toString() : "");
        }

        listNode = new Element("sequencingOptions");
        node.addContent(listNode);
        List<NumberSequencingCyclesAllowed> allowedList = this.numberSeqCyclesAllowedMap.get(rc.getCodeRequestCategory());
        if (allowedList != null) {
          for(NumberSequencingCyclesAllowed c : allowedList) {
            this.getSecAdvisor().flagPermissions(c);
            Element cycleNode = c.toXMLDocument(null, DetailObject.DATE_OUTPUT_SQL).getRootElement();
            listNode.addContent(cycleNode);
          }
        }               
      }

      org.jdom.output.XMLOutputter out = new org.jdom.output.XMLOutputter();
      this.xmlResult = out.outputString(doc);

      setResponsePage(this.SUCCESS_JSP);
    }catch (NamingException e){
      log.error("An exception has occurred in GetExperimentPlatformList ", e);
      e.printStackTrace();
      throw new RollBackCommandException(e.getMessage());
        
    }catch (SQLException e) {
      log.error("An exception has occurred in GetExperimentPlatformList ", e);
      e.printStackTrace();
      throw new RollBackCommandException(e.getMessage());
    } catch (XMLReflectException e){
      log.error("An exception has occurred in GetExperimentPlatformList ", e);
      e.printStackTrace();
      throw new RollBackCommandException(e.getMessage());
    } catch (Exception e) {
      log.error("An exception has occurred in GetExperimentPlatformList ", e);
      e.printStackTrace();
      throw new RollBackCommandException(e.getMessage());
    } finally {
      try {
        this.getSecAdvisor().closeReadOnlyHibernateSession();        
      } catch(Exception e) {
        
      }
    }

    if (isValid()) {
      setResponsePage(this.SUCCESS_JSP);
    } else {
      setResponsePage(this.ERROR_JSP);
    }
    
    return this;
  }
  
  private boolean isAssociated(RequestCategory rc, SampleType st) {
    Map idMap = sampleTypeMap.get(rc.getCodeRequestCategory());
    return idMap != null && idMap.containsKey(st.getIdSampleType());
  }
  
  private boolean isAssociated(RequestCategory rc, Application a) {
    Map idMap = applicationMap.get(rc.getCodeRequestCategory());
    return idMap != null && idMap.containsKey(a.getCodeApplication());
  }
  private RequestCategoryApplication getRequestCategoryApplication(RequestCategory rc, Application a) {
    Map<String, RequestCategoryApplication> idMap = applicationMap.get(rc.getCodeRequestCategory());
    if (idMap != null && idMap.containsKey(a.getCodeApplication())) {
      return idMap.get(a.getCodeApplication());
    } else {
      return null;
    }
  }
  
  private String getCodeApplications(SampleType st) {
    String buf = "";
    Map idMap = sampleTypeXApplicationMap.get(st.getIdSampleType());
    if (idMap != null) {
      for(Iterator i = idMap.keySet().iterator(); i.hasNext();) {
        String codeApplication = (String)i.next();
        if (buf.length() > 0) {
          buf += ",";
        }
        buf += codeApplication;
      }
    }
    return buf;
  }
  
  private String getIdSeqLibProtocols(Application app) {
    String buf = "";
    Map idMap = applicationXSeqLibProtocolMap.get(app.getCodeApplication());
    if (idMap != null) {
      for(Iterator i = idMap.keySet().iterator(); i.hasNext();) {
        Integer id = (Integer)i.next();
        if (buf.length() > 0) {
          buf += ",";
        }
        buf += id.toString();
      }
    }
    return buf;
  }
  
  private void hashSupportingDictionaries(Session sess) throws Exception {
    sampleTypes = sess.createQuery("SELECT st from SampleType st order by st.sampleType").list();
    List sampleTypeXrefs = sess.createQuery("SELECT x from SampleTypeRequestCategory x").list();
    for(Iterator i = sampleTypeXrefs.iterator(); i.hasNext();) {
      SampleTypeRequestCategory x = (SampleTypeRequestCategory)i.next();
      Map idMap = (Map)sampleTypeMap.get(x.getCodeRequestCategory());
      if (idMap == null) {
        idMap = new HashMap();
      }
      idMap.put(x.getIdSampleType(), null);
      sampleTypeMap.put(x.getCodeRequestCategory(), idMap);
    }
    
    applications = sess.createQuery("SELECT a from Application a order by a.application").list();
    List applicationXrefs = sess.createQuery("SELECT x from RequestCategoryApplication x").list();
    for(Iterator i = applicationXrefs.iterator(); i.hasNext();) {
      RequestCategoryApplication x = (RequestCategoryApplication)i.next();
      Map idMap = (Map)applicationMap.get(x.getCodeRequestCategory());
      if (idMap == null) {
        idMap = new HashMap();
      }
      idMap.put(x.getCodeApplication(), x);
      applicationMap.put(x.getCodeRequestCategory(), idMap);
    }
    
    List sampleTypeXApplications = sess.createQuery("SELECT x from SampleTypeApplication x").list();
    for(Iterator i = sampleTypeXApplications.iterator(); i.hasNext();) {
      SampleTypeApplication x = (SampleTypeApplication)i.next();
      Map idMap = (Map)sampleTypeXApplicationMap.get(x.getIdSampleType());
      if (idMap == null) {
        idMap = new HashMap();
      }
      idMap.put(x.getCodeApplication(), null);
      sampleTypeXApplicationMap.put(x.getIdSampleType(), idMap);
    }
    
    List applicationXSeqLibProtocols = sess.createQuery("SELECT x from SeqLibProtocolApplication x").list();
    for(Iterator i = applicationXSeqLibProtocols.iterator(); i.hasNext();) {
      SeqLibProtocolApplication x = (SeqLibProtocolApplication)i.next();
      Map idMap = (Map)applicationXSeqLibProtocolMap.get(x.getCodeApplication());
      if (idMap == null) {
        idMap = new HashMap();
      }
      idMap.put(x.getIdSeqLibProtocol(), null);
      applicationXSeqLibProtocolMap.put(x.getCodeApplication(), idMap);
    }
    
    
    List numberSeqCyclesAllowed = sess.createQuery("SELECT x from NumberSequencingCyclesAllowed x join x.numberSequencingCycles c order by c.numberSequencingCycles").list();
    for(Iterator i = numberSeqCyclesAllowed.iterator(); i.hasNext();) {
      NumberSequencingCyclesAllowed x = (NumberSequencingCyclesAllowed)i.next();
      List<NumberSequencingCyclesAllowed> allowedList = numberSeqCyclesAllowedMap.get(x.getCodeRequestCategory());
      if (allowedList == null) {
        allowedList = new ArrayList<NumberSequencingCyclesAllowed>();
      }
      allowedList.add(x);
      numberSeqCyclesAllowedMap.put(x.getCodeRequestCategory(), allowedList);
    }
  }
}