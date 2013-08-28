package hci.gnomex.controller;

import hci.framework.control.Command;
import hci.framework.control.RollBackCommandException;
import hci.gnomex.constants.Constants;
import hci.gnomex.model.AppUser;
import hci.gnomex.model.CoreFacility;
import hci.gnomex.model.Lab;
import hci.gnomex.model.PropertyDictionary;
import hci.gnomex.security.EncrypterService;
import hci.gnomex.utility.HibernateSession;
import hci.gnomex.utility.MailUtil;
import hci.gnomex.utility.PropertyDictionaryHelper;

import java.io.Serializable;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.UUID;

import javax.mail.MessagingException;
import javax.naming.NamingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.hibernate.HibernateException;
import org.hibernate.Session;

public class ChangePassword extends GNomExCommand implements Serializable {

  private static org.apache.log4j.Logger log = org.apache.log4j.Logger.getLogger(ChangePassword.class);
  
  public String SUCCESS_JSP = "/getXML.jsp";
  //public String ERROR_JSP = "/message.jsp";
  public String ERROR_JSP = "/change_password.jsp";
  
  private String userName;
  private String oldPassword;
  private String newPassword;
  private String newPasswordConfirm;
  private String responsePageSuccess = null;
  private String responsePageError = null;
  
  private AppUser appUser = null;
  private String  labContactEmail = null;
  private String regErrorMsg = null;
  
  private String launchAppURL = "";
  
  public void loadCommand(HttpServletRequest request, HttpSession session) {
  	try {	
    	this.launchAppURL = this.getLaunchAppURL(request);
      
      if (request.getParameter("userName") != null && ! request.getParameter("userName").equals("")) {
      	this.userName = request.getParameter("userName");
      }
      else {
        this.addInvalidField("userName", "Username is required");
      }      	
    	
      if (request.getParameter("oldPassword") != null && ! request.getParameter("oldPassword").equals("")) {
      	this.oldPassword = request.getParameter("oldPassword");
      }
      
      if (request.getParameter("newPassword") != null && ! request.getParameter("newPassword").equals("")) {
      	this.newPassword = request.getParameter("newPassword");
      }
      
      if (request.getParameter("newPasswordConfirm") != null && ! request.getParameter("newPasswordConfirm").equals("")) {
      	this.newPasswordConfirm = request.getParameter("newPasswordConfirm");
      }    
      
      if (newPassword != null && newPasswordConfirm != null) {
      	if (! newPassword.equals(newPasswordConfirm)) {
      		this.addInvalidField("passwords", "The two passwords you have entered do not match. Please re-enter your new password.");
      	}
      }
      if (request.getParameter("responsePageSuccess") != null && !request.getParameter("responsePageSuccess").equals("")) {
        responsePageSuccess = request.getParameter("responsePageSuccess");
      }
      if (request.getParameter("responsePageError") != null && !request.getParameter("responsePageError").equals("")) {
        responsePageError = request.getParameter("responsePageError");
      }
      
      this.validate();
  	} catch (Exception e) {
  		log.error(e.getClass().toString() + ": " + e);
  		e.printStackTrace();
  	}
  }

  public Command execute() throws RollBackCommandException {
    
    try {
      Session sess = HibernateSession.currentSession(this.getUsername());
      
      // First make sure this person is registered by looking them
      // up in the user table
      String checkRegHql = "from AppUser u where u.userNameExternal='" + userName + "'";

      appUser = (AppUser) sess.createQuery(checkRegHql).uniqueResult();
      //Check that the user exists
      if (appUser == null) {
      	regErrorMsg = "Invalid user.";
        this.addInvalidField("Invalid username", regErrorMsg);
      } 
      //Check that the user is active
      else if (appUser.getIsActive() != null && appUser.getIsActive().equals("N")) {
      	regErrorMsg = "Inactive user account.";
        this.addInvalidField("Inactive user account", regErrorMsg);      	
      } else {
        //Change password
        if(oldPassword != null && newPassword != null && newPasswordConfirm != null) {
        	//Encrypt password
        	String oldPasswordEncrypted = EncrypterService.getInstance().encrypt(oldPassword);
        	
        	//Check old password match
        	if (! appUser.getPasswordExternal().equals(oldPasswordEncrypted)) {
        		this.addInvalidField("Invalid username or password", "Invalid username or password.");
        	}
        	else {
        		String thePasswordEncrypted = EncrypterService.getInstance().encrypt(newPassword);
          	appUser.setPasswordExternal(thePasswordEncrypted);
          	sess.update(appUser);
          	sess.flush();
        	}
        }
        //Reset password
        else {  
          //Check that the user has an email if we are attempting a reset password
          if (appUser.getEmail() == null || appUser.getEmail().equals("")) {
            regErrorMsg = "Unable to reset password because the email is not filled in. Please contact GNomEx support.";
            this.addInvalidField("Invalid email", regErrorMsg);       
          } else {
            UUID uuid = UUID.randomUUID();
            String myRandom = uuid.toString();
            String randPwd =  myRandom.substring(0, 8);
            String thePasswordEncrypted = EncrypterService.getInstance().encrypt(randPwd);
            
            appUser.setPasswordExternal(thePasswordEncrypted);
            sess.update(appUser);
            sess.flush(); 

            Integer idCoreFacility = null;
            for(Iterator i = appUser.getLabs().iterator(); i.hasNext();) {
              Lab l = (Lab)i.next();
              for(Iterator j = l.getCoreFacilities().iterator(); j.hasNext(); ) {
                CoreFacility facility = (CoreFacility)j.next();
                if (idCoreFacility == null) {
                  idCoreFacility = facility.getIdCoreFacility();
                } else if(!idCoreFacility.equals(facility.getIdCoreFacility())) {
                  idCoreFacility = null;
                  break;
                }
              }
            }
            
            PropertyDictionaryHelper pdh = PropertyDictionaryHelper.getInstance(sess);
            if (idCoreFacility == null) {
              labContactEmail = pdh.getProperty(PropertyDictionary.GENERIC_NO_REPLY_EMAIL);
            } else {
              labContactEmail = pdh.getCoreFacilityProperty(idCoreFacility, PropertyDictionary.CONTACT_EMAIL_CORE_FACILITY);
            }
            
            sendConfirmationEmail(appUser.getEmail(), randPwd);  
          }
          
        }      
      }
      this.validate();
    } catch (HibernateException e) {
      e.printStackTrace();
      log.error(e.getClass().toString() + ": " + e);
      throw new RollBackCommandException();
    } catch (NumberFormatException e) {
      log.error(e.getClass().toString() + ": " + e);
      e.printStackTrace();
      throw new RollBackCommandException();
    } catch (NamingException e) {
      log.error(e.getClass().toString() + ": " + e);
      e.printStackTrace();
      throw new RollBackCommandException();
    } catch (SQLException e) {
      log.error(e.getClass().toString() + ": " + e);
      e.printStackTrace();
      throw new RollBackCommandException();
    } catch (Exception e) {
      log.error(e.getClass().toString() + ": " + e);
      e.printStackTrace();
      throw new RollBackCommandException();    	
    }
    finally {
      try {
        this.validate();
        HibernateSession.closeSession();
      } catch (HibernateException e) {
        log.error(e.getClass().toString() + ": " + e);
        this.validate();
        throw new RollBackCommandException();
      } catch (SQLException e) {
        log.error(e.getClass().toString() + ": " + e);
        this.validate();
        throw new RollBackCommandException();
      }
    }
    
    return this;
  }
  
  public void validate() {
    // See if we have a valid form
    if (isValid()) {
      setResponsePage(responsePageSuccess != null ? responsePageSuccess : this.SUCCESS_JSP);
    } else {
      setResponsePage(responsePageError != null ? responsePageError : this.ERROR_JSP);
    }
  }
  
  public void sendConfirmationEmail(String email, String newPwd)  throws NamingException, MessagingException {
  	
  	StringBuffer content = new StringBuffer();
    content.append("Your GNomEx password has been reset. Your new temporary password is:<br><br>" + newPwd + "<br><br>");
    content.append("Please take a moment to change your temporary password to a new password the next time you log in.<br>");
    content.append("<a href=\"" + launchAppURL + "\">" + "Sign in to " + Constants.APP_NAME + "</a>");      

    MailUtil.send(
  		email,
  		null,
  		this.labContactEmail,
  		"Your GNomEx password has been reset",
  		content.toString(),
  		true
  	);
  }
}
