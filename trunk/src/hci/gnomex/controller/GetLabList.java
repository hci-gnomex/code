package hci.gnomex.controller;

import hci.gnomex.security.SecurityAdvisor;
import hci.gnomex.utility.HibernateSession;
import hci.framework.control.Command;
import hci.framework.control.RollBackCommandException;
import hci.framework.model.DetailObject;
import hci.framework.utilities.Annotations;
import hci.framework.utilities.XMLReflectException;

import java.io.Serializable;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.naming.NamingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.jdom.Element;
import org.jdom.Document;
import org.jdom.output.XMLOutputter;

import hci.gnomex.model.Lab;
import hci.gnomex.model.LabFilter;
import hci.gnomex.model.Visibility;


public class GetLabList extends GNomExCommand implements Serializable {
  
 
  
  // the static field for logging in Log4J
  private static org.apache.log4j.Logger log = org.apache.log4j.Logger.getLogger(GetLabList.class);
  
  private LabFilter labFilter;
  private String    listKind = "LabList";
  
  public void validate() {
  }
  
  public void loadCommand(HttpServletRequest request, HttpSession session) {

    labFilter = new LabFilter();
    HashMap errors = this.loadDetailObject(request, labFilter);
    this.addInvalidFields(errors);
    
    if (request.getParameter("listKind") != null && !request.getParameter("listKind").equals("")) {
      listKind = request.getParameter("listKind");
      
      if (listKind.startsWith("Unbounded")) {
        labFilter.setUnbounded(true);
      }
    }
    
  }

  public Command execute() throws RollBackCommandException {
    
    try {
    Document doc = new Document(new Element(listKind));
    
    // If this is a guest user and the list is bounded, return an empty lab list
    if (this.getSecAdvisor().isGuest()/* && !labFilter.isUnbounded()*/) {
      
    } else {
      Session sess = this.getSecAdvisor().getReadOnlyHibernateSession(this.getUsername());
      
      // If this is an unbounded list, run a query to find all
      // labs that have public experiments.
      Map otherLabMap = new HashMap();
      if (labFilter.isUnbounded()) {
        StringBuffer buf = new StringBuffer();
        buf.append("SELECT distinct p.idLab  ");
        buf.append(" FROM      Project p ");
        buf.append(" LEFT JOIN p.requests as req ");
        buf.append(" LEFT JOIN req.collaborators as collab ");
        this.getSecAdvisor().buildSecurityCriteria(buf, "req", "collab", true, false, false);
        List otherLabs = (List)sess.createQuery(buf.toString()).list();
        for (Iterator i = otherLabs.iterator(); i.hasNext();) {
          Integer idLabOther = (Integer)i.next();
          otherLabMap.put(idLabOther, idLabOther);        
        }        
      }
      
      // If this is a non-gnomex University-only user, we want to get
      // all of the active labs
      Map activeLabMap = new HashMap();
      if (this.getSecAdvisor().isUniversityOnlyUser()) {
        StringBuffer buf = new StringBuffer();
        buf.append("SELECT l.idLab  ");
        buf.append(" FROM  Lab l ");
        buf.append(" WHERE l.isActive = 'Y' ");
        List allLabs = (List)sess.createQuery(buf.toString()).list();
        for (Iterator i = allLabs.iterator(); i.hasNext();) {
          Integer idLab = (Integer)i.next();
          activeLabMap.put(idLab, idLab);        
        }        
        
      }
      
      StringBuffer queryBuf = labFilter.getQuery(this.getSecAdvisor());
      List labs = (List)sess.createQuery(queryBuf.toString()).list();
      
      for(Iterator i = labs.iterator(); i.hasNext();) {
        Lab lab = (Lab)i.next();
       
        if (this.getSecAdvisor().isGroupIAmMemberOrManagerOf(lab.getIdLab())) {
          lab.canSubmitRequests(true);
        } else {
          lab.canSubmitRequests(false);
        }
        
        if (this.getSecAdvisor().isGroupIManage(lab.getIdLab())) {
          lab.canManage(true);
        } else {
          lab.canManage(false);
        }
        
        if (this.getSecAdvisor().hasPermission(SecurityAdvisor.CAN_ACCESS_ANY_OBJECT) ||
            this.getSecAdvisor().isGroupICollaborateWith(lab.getIdLab()) ||
            this.getSecAdvisor().isGroupIAmMemberOrManagerOf(lab.getIdLab())) {
          lab.isMyLab(true);
        } else {
          lab.isMyLab(false);
        }

        
        if (lab.getIsMyLab().equals("Y") || otherLabMap.containsKey(lab.getIdLab()) || activeLabMap.containsKey(lab.getIdLab())) {

          lab.excludeMethodFromXML("getNotes");
          lab.excludeMethodFromXML("getContactName");
          lab.excludeMethodFromXML("getContactAddress");
          lab.excludeMethodFromXML("getContactCity");
          lab.excludeMethodFromXML("getContactCodeState");
          lab.excludeMethodFromXML("getContactZip");
          lab.excludeMethodFromXML("getContactEmail");
          lab.excludeMethodFromXML("getContactPhone");

          lab.excludeMethodFromXML("getMembers");
          lab.excludeMethodFromXML("getCollaborators");
          lab.excludeMethodFromXML("getManagers");

          lab.excludeMethodFromXML("getBillingAccounts");
          lab.excludeMethodFromXML("getApprovedBillingAccounts");
          lab.excludeMethodFromXML("getPendingBillingAccounts");
          
          lab.excludeMethodFromXML("getProjects");
          
          lab.excludeMethodFromXML("getIsCcsgMember");
          
          Hibernate.initialize(lab.getInstitutions());
          doc.getRootElement().addContent(lab.toXMLDocument(null, DetailObject.DATE_OUTPUT_SQL, null, Annotations.IGNORE).getRootElement());
        }
      }
    }
   
    
    XMLOutputter out = new org.jdom.output.XMLOutputter();
    this.xmlResult = out.outputString(doc);
    
    setResponsePage(this.SUCCESS_JSP);
    }catch (NamingException e){
      log.error("An exception has occurred in GetLabList ", e);
      e.printStackTrace();
      throw new RollBackCommandException(e.getMessage());
        
    }catch (SQLException e) {
      log.error("An exception has occurred in GetLabList ", e);
      e.printStackTrace();
      throw new RollBackCommandException(e.getMessage());
    } catch (XMLReflectException e){
      log.error("An exception has occurred in GetLabList ", e);
      e.printStackTrace();
      throw new RollBackCommandException(e.getMessage());
    } catch (Exception e){
      log.error("An exception has occurred in GetLabList ", e);
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

}