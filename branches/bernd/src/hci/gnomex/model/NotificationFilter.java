package hci.gnomex.model;

import hci.gnomex.security.SecurityAdvisor;
import hci.framework.model.DetailObject;

public class NotificationFilter extends DetailObject {
  
  // Criteria / Argumented mx:request variables
	  
  private static final long serialVersionUID = 42L;
    
  private Integer			idNotification;
  private String			sourceType;
  private String			message;
  private java.util.Date	date;
  private Integer			idUserTarget;
  private Integer			idLabTarget;
  private Integer			expID;
  private String			type;
  	
  private StringBuffer          queryBuf;
  private boolean               addWhere = true;
  private SecurityAdvisor       secAdvisor;
  
  public StringBuffer getQuery(SecurityAdvisor secAdvisor) {
    this.secAdvisor = secAdvisor;
    
    // Get Base query. 
    return getQuery();

  }

  private StringBuffer getQuery() {
    queryBuf = new StringBuffer();
    addWhere = true;
      
    addBaseColumns(queryBuf);
    addBaseQueryBody(queryBuf);
    addRequestCriteria();
    
//    System.out.println("Query: " + idUserTarget+ " --- " + queryBuf);
    
    return queryBuf;
  }

  public void addBaseColumns(StringBuffer queryBuf) {
	  queryBuf.append("SELECT distinct ");
	  queryBuf.append(" n.idNotification, n.sourceType, ");
	  queryBuf.append(" n.message, n.date, ");
	  queryBuf.append(" n.idUserTarget, n.idLabTarget, ");
	  queryBuf.append(" n.expID, n.type, n.fullNameUser ");
   } 

  private void addBaseQueryBody(StringBuffer queryBuf) {
	  queryBuf.append(" FROM         Notification n ");
  }
  
  
  private void addRequestCriteria() {
	// Search by idNotification 
	    if (idNotification != null && idNotification != 0){
	      this.addWhereOrAnd();
	      queryBuf.append(" n.idNotification = '");
	      queryBuf.append(idNotification);
	      queryBuf.append("'");
	    }
	    
	// Search by userID
	    if(idUserTarget != null && idUserTarget != 0){
	    	this.addWhereOrAnd();
	    	queryBuf.append("n.idUserTarget = '");
	    	queryBuf.append(idUserTarget);
	    	queryBuf.append("'");
	    }
	    
	// Search by labID
	    if(idLabTarget != null && idLabTarget != 0){
	    	this.addWhereOrAnd();
	    	queryBuf.append("n.idLabTarget = '");
	    	queryBuf.append(idLabTarget);
	    	queryBuf.append("'");
	    }
	    
	// Search by date
	    if(date != null){
	    	this.addWhereOrAnd();
	    	queryBuf.append("n.date = '");
	    	queryBuf.append(date);
	    	queryBuf.append("'");
	    }
	    
	// Search by experimentID
	    if(expID != null){
	    	this.addWhereOrAnd();
	    	queryBuf.append("n.expID = '");
	    	queryBuf.append(expID);
	    	queryBuf.append("'");
	    }
  }

  protected boolean addWhereOrAnd() {
    if (addWhere) {
      queryBuf.append(" WHERE ");
      addWhere = false;
    } else {
      queryBuf.append(" AND ");
    }
    return addWhere;
  }

  public String getMessage() {
	  return message;
  }

	public void setMessage(String message) {
		this.message = message;
	}

	public Integer getIdNotification() {
		return idNotification;
	}

	public void setIdNotification(Integer idNotification) {
		this.idNotification = idNotification;
	}

	public java.util.Date getDate() {
		return date;
	}

	public void setDate(java.util.Date date) {
		this.date = date;
	}

	public Integer getIdUserTarget() {
		return idUserTarget;
	}

	public void setIdUserTarget(Integer idUserTarget) {
		this.idUserTarget = idUserTarget;
	}

	public String getSourceType() {
		return sourceType;
	}

	public void setSourceType(String sourceType) {
		this.sourceType = sourceType;
	}

	public Integer getIdLabTarget() {
		return idLabTarget;
	}

	public void setIdLabTarget(Integer idLabTarget) {
		this.idLabTarget = idLabTarget;
	}

	public Integer getExpID() {
		return expID;
	}

	public void setExpID(Integer expID) {
		this.expID = expID;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}
} 