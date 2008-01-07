package hci.gnomex.controller;

import hci.gnomex.model.AppUser;
import hci.gnomex.security.EncrypterService;
import hci.gnomex.security.SecurityAdvisor;
import hci.gnomex.utility.HibernateSession;
import hci.framework.control.Command;
import hci.framework.control.RollBackCommandException;

import java.io.Serializable;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.hibernate.Session;




public class SaveAppUser extends GNomExCommand implements Serializable {
  
 
  
  // the static field for logging in Log4J
  private static org.apache.log4j.Logger log = org.apache.log4j.Logger.getLogger(SaveAppUser.class);
  

  private AppUser               appUserScreen;
  private boolean              isNewAppUser = false;
  
  
  public void validate() {
  }
  
  public void loadCommand(HttpServletRequest request, HttpSession session) {
    
    appUserScreen = new AppUser();
    HashMap errors = this.loadDetailObject(request, appUserScreen);
    this.addInvalidFields(errors);
    if (appUserScreen.getIdAppUser() == null || appUserScreen.getIdAppUser().intValue() == 0) {
      isNewAppUser = true;
    }
    
   
  }

  public Command execute() throws RollBackCommandException {
    
    try {
      Session sess = HibernateSession.currentSession(this.getUsername());
      
      
      if (this.getSecurityAdvisor().hasPermission(SecurityAdvisor.CAN_ADMINISTER_USERS)) {
        AppUser appUser = null;
        
        if (isNewAppUser) {
          if (userNameAlreadyExists(sess, appUserScreen.getUserNameExternal(), null)) {
            this.addInvalidField("username exists", "The User name " + appUserScreen.getUserNameExternal() + " already exists.  Please use another name.");
          }
          
          if (this.isValid()) {
            appUser = appUserScreen;
            
            if (appUser.getPasswordExternal() != null && appUser.getPasswordExternal() != "" && !appUser.getPasswordExternal().equals(AppUser.MASKED_PASSWORD)) {
              String encryptedPassword = EncrypterService.getInstance().encrypt(appUser.getPasswordExternal());
              appUser.setPasswordExternal(encryptedPassword);      
            }
            
            sess.save(appUser);
            
          }
        } else {
          
          appUser = (AppUser)sess.load(AppUser.class, appUserScreen.getIdAppUser());
          initializeAppUser(appUser);            
          
          if (userNameAlreadyExists(sess, appUser.getUserNameExternal(), appUser.getIdAppUser())) {
            this.addInvalidField("username exists", "The User name " + appUser.getUserNameExternal() + " already exists.  Please assign another name.");
          }
          
          
        }

        
        if (this.isValid()) {
          sess.flush();
          this.xmlResult = "<SUCCESS idAppUser=\"" + appUser.getIdAppUser() + "\"/>";
          setResponsePage(this.SUCCESS_JSP);
        } else {
          setResponsePage(this.ERROR_JSP);
        }
      
      } else {
        this.addInvalidField("Insufficient permissions", "Insufficient permission to save member.");
        setResponsePage(this.ERROR_JSP);
      }
      
      
    }catch (Exception e){
      log.error("An exception has occurred in SaveAppUser ", e);
      e.printStackTrace();
      throw new RollBackCommandException(e.getMessage());
        
    }finally {
      try {
        HibernateSession.closeSession();        
      } catch(Exception e) {
        
      }
    }
    
    return this;
  }
  
  private void initializeAppUser(AppUser appUser) {
    appUser.setFirstName(appUserScreen.getFirstName());
    appUser.setLastName(appUserScreen.getLastName());
    appUser.setJobTitle(appUserScreen.getJobTitle());
    appUser.setInstitute(appUserScreen.getInstitute());
    appUser.setDepartment(appUserScreen.getDepartment());
    appUser.setEmail(appUserScreen.getEmail());
    appUser.setPhone(appUserScreen.getPhone());
    appUser.setIsActive(appUserScreen.getIsActive());
    appUser.setCodeUserPermissionKind(appUserScreen.getCodeUserPermissionKind());
    appUser.setuNID(appUserScreen.getuNID());
    appUser.setUserNameExternal(appUserScreen.getUserNameExternal());
    
    // Only encrypt and set the password if something has been entered in the text field.
    if (appUserScreen.getPasswordExternal() != null && appUserScreen.getPasswordExternal() != "" && !appUserScreen.getPasswordExternal().equals(AppUser.MASKED_PASSWORD)) {
      String encryptedPassword = EncrypterService.getInstance().encrypt(appUserScreen.getPasswordExternal());
      appUser.setPasswordExternal(encryptedPassword);      
    } else {
      appUser.setPasswordExternal(null);
    }
    
    
  }
  
  private class AppUserComparator implements Comparator, Serializable {
    public int compare(Object o1, Object o2) {
      AppUser u1 = (AppUser)o1;
      AppUser u2 = (AppUser)o2;
      
      return u1.getIdAppUser().compareTo(u2.getIdAppUser());
      
    }
  }
  
  private static boolean userNameAlreadyExists(Session sess, String userNameExternal, Integer idAppUser) {
    if (userNameExternal == null || userNameExternal.equals("")) {
      return false;
    }

    StringBuffer buf = new StringBuffer();
    buf.append("SELECT a.userNameExternal from AppUser as a where a.userNameExternal = '"); 
    buf.append(userNameExternal + "'");
    if (idAppUser != null) {
      buf.append(" AND a.idAppUser != " + idAppUser);
    }
    List users = sess.createQuery(buf.toString()).list();
    return users.size() > 0;    
  }

}