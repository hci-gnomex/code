package hci.gnomex.model;

import hci.dictionary.model.DictionaryEntry;
import java.io.Serializable;



public class UserPermissionKind extends DictionaryEntry implements Serializable {

  public static final String        ADMIN_PERMISSION_KIND   = "ADMIN";
  public static final String        GROUP_PERMISSION_KIND   = "LAB";
  
  private String  codeUserPermissionKind;
  private String  userPermissionKind;
  private String  isActive;

  
  public String getIsActive() {
    return isActive;
  }

  
  public void setIsActive(String isActive) {
    this.isActive = isActive;
  }

  public String getDisplay() {
    String display = this.getNonNullString(getUserPermissionKind());
    return display;
  }

  public String getValue() {
    return getCodeUserPermissionKind();
  }

  
  
  
  public String getUserPermissionKind() {
    return userPermissionKind;
  }

  
 

  
  public void setUserPermissionKind(String userPermissionKind) {
    this.userPermissionKind = userPermissionKind;
  }

  
  public String getCodeUserPermissionKind() {
    return codeUserPermissionKind;
  }

  
  public void setCodeUserPermissionKind(String codeUserPermissionKind) {
    this.codeUserPermissionKind = codeUserPermissionKind;
  }

 
  
  
 

  
}
