package hci.gnomex.controller;

import hci.framework.control.Command;
import hci.framework.control.RollBackCommandException;
import hci.gnomex.model.GenomeIndex;
import hci.gnomex.utility.DictionaryHelper;
import hci.gnomex.utility.HibernateSession;

import java.io.Serializable;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.hibernate.Session;
import org.hibernate.exception.ConstraintViolationException;
import org.apache.log4j.Logger;



public class DeleteGenomeIndex extends GNomExCommand implements Serializable {
  
 
  
  // the static field for logging in Log4J
  private static Logger LOG = Logger.getLogger(DeleteGenomeIndex.class);
  
  
  private Integer      idGenomeIndex = null;
    
  public void validate() {
  }
  
  public void loadCommand(HttpServletRequest request, HttpSession session) {
    
   if (request.getParameter("idGenomeIndex") != null && !request.getParameter("idGenomeIndex").equals("")) {
     idGenomeIndex = new Integer(request.getParameter("idGenomeIndex"));
   } else {
     this.addInvalidField("idGenomeIndex", "idGenomeIndex is required.");
   }

  }

  public Command execute() throws RollBackCommandException {
    Session sess = null;
    GenomeIndex gnIdx = null;
    
    try {
      sess = HibernateSession.currentSession(this.getUsername());
      gnIdx = (GenomeIndex)sess.load(GenomeIndex.class, idGenomeIndex);
      
      // Check permissions
      if (this.getSecAdvisor().canDelete(gnIdx)) {
        
        //
        // Delete property
        //
        sess.delete(gnIdx);
      
        
        sess.flush();
        
       
        DictionaryHelper.reload(sess);
        
        this.xmlResult = "<SUCCESS/>";
      
        setResponsePage(this.SUCCESS_JSP);
   
      } else {
        this.addInvalidField("insufficient permission", "Insufficient permissions to delete property.");
        setResponsePage(this.ERROR_JSP);
      }
    } catch (ConstraintViolationException ce) {
      this.addInvalidField("constraint", "GenomeIndex set to inactive.  Unable to delete because of existing records.");
      
      try {
        sess.clear();
        gnIdx = (GenomeIndex)sess.load(GenomeIndex.class, idGenomeIndex);
        gnIdx.setIsActive("N");
        sess.flush();
      } catch(Exception e) {
        LOG.error("An exception has occurred in DeleteGenomeIndex when trying to inactivate property ", e);

        throw new RollBackCommandException(e.getMessage());
        
      }
      
    } catch (Exception e){
      LOG.error("An exception has occurred in DeleteGenomeIndex ", e);

      throw new RollBackCommandException(e.getMessage());
        
    }finally {
      try {
        //closeHibernateSession;        
      } catch(Exception e) {
        LOG.error("An exception has occurred in DeleteGenomeIndex ", e);
      }
    }
    
    return this;
  }
  
 
  
  
  

}