package hci.gnomex.model;


import java.io.Serializable;
import java.sql.Date;
import java.util.Calendar;
import java.util.Comparator;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.Set;

import hci.gnomex.security.SecurityAdvisor;
import hci.framework.model.DetailObject;

public class RequestProgressFilter extends DetailObject {
  
  
  // Criteria
  private String                requestNumber;
  private Integer               idAppUser;
  private Integer               idLab;
  private Date                  createDateFrom;
  private Date                  createDateTo;
  private String                isComplete;
  private String                isNotComplete;
  private String                requestDateLastWeek;
  private String                requestDateLastMonth;
  private String                requestDateLastYear;
  private Integer               idProject;
  
  
  private StringBuffer          queryBuf;
  private boolean              addWhere = true;
  private SecurityAdvisor       secAdvisor;
  
  
  public boolean hasCriteria() {
    if ((requestNumber != null && !requestNumber.equals("")) ||
        idAppUser != null ||
        idLab != null ||
        idProject != null ||
        createDateFrom != null ||
        createDateTo != null ||
        (isComplete != null && isComplete.equalsIgnoreCase("Y")) ||
        (isNotComplete != null && isNotComplete.equalsIgnoreCase("Y")) ||
        (requestDateLastWeek != null && requestDateLastWeek.equalsIgnoreCase("Y")) ||
        (requestDateLastMonth != null && requestDateLastMonth.equalsIgnoreCase("Y")) ||
        (requestDateLastYear != null && requestDateLastYear.equalsIgnoreCase("Y"))) {
      return true;
    } else {
      return false;
    }
  }
  
  
  public StringBuffer getQualityControlQuery(SecurityAdvisor secAdvisor) {
    this.secAdvisor = secAdvisor;
    queryBuf = new StringBuffer();
    addWhere = true;
    
    queryBuf.append(" SELECT req.createDate, req.number, req.codeRequestCategory, "); 
    queryBuf.append("        req.codeMicroarrayCategory, req.idAppUser, ");
    queryBuf.append("        '', '', ");
    queryBuf.append("        '', '', ");
    queryBuf.append("        '', s.qualDate, s.number, s.name, ");
    queryBuf.append("        '', '', '', '', ");
    queryBuf.append("        req.idLab, ");
    queryBuf.append("        '', req.idRequest, ");
    queryBuf.append("        reqOwner.firstName, reqOwner.lastName");
    getQualityControlQueryBody(queryBuf);
    
    return queryBuf;
    
  }
  
  
  public StringBuffer getMicroarrayQuery(SecurityAdvisor secAdvisor) {
    this.secAdvisor = secAdvisor;
    queryBuf = new StringBuffer();
    addWhere = true;
    
    queryBuf.append(" SELECT req.createDate, req.number, req.codeRequestCategory, "); 
    queryBuf.append("        req.codeMicroarrayCategory, req.idAppUser, ");
    queryBuf.append("        hyb.number, hyb.hybDate, ");
    queryBuf.append("        hyb.extractionDate, hyb.hybFailed, ");
    queryBuf.append("        ls1.labelingDate, s1.qualDate, s1.number, s1.name, ");
    queryBuf.append("        ls2.labelingDate, s2.qualDate, s2.number, s2.name, ");
    queryBuf.append("        req.idLab, ");
    queryBuf.append("        hyb.hasResults, req.idRequest, ");
    queryBuf.append("        reqOwner.firstName, reqOwner.lastName");
    getMicroarrayQueryBody(queryBuf);
    
    return queryBuf;
    
  }
  
  public void getMicroarrayQueryBody(StringBuffer queryBuf) {
    
    queryBuf.append(" FROM           Request as req ");
    queryBuf.append(" JOIN           req.hybridizations as hyb ");
    queryBuf.append(" JOIN           hyb.labeledSampleChannel1 as ls1 ");
    queryBuf.append(" JOIN           ls1.sample as s1 ");
    queryBuf.append(" LEFT JOIN      req.appUser as reqOwner ");
    queryBuf.append(" LEFT JOIN      hyb.labeledSampleChannel2 as ls2 ");
    queryBuf.append(" LEFT JOIN      ls2.sample as s2 ");
    
    addRequestCriteria();
    addHybCriteria();
    addSecurityCriteria();
    
    
    this.addWhereOrAnd();
    queryBuf.append(" req.codeRequestCategory != '");
    queryBuf.append(RequestCategory.QUALITY_CONTROL_REQUEST_CATEGORY);
    queryBuf.append("'");
    
    queryBuf.append(" order by req.createDate desc, req.number desc , hyb.number asc");
  
  }
  
  public void getQualityControlQueryBody(StringBuffer queryBuf) {
     
    queryBuf.append(" FROM           Request as req ");
    queryBuf.append(" LEFT JOIN      req.samples as s ");
    queryBuf.append(" LEFT JOIN      req.appUser as reqOwner ");

    
    addRequestCriteria();
    addQualityControlCriteria();
    addSecurityCriteria();

    
    this.addWhereOrAnd();
    queryBuf.append(" req.codeRequestCategory = '");
    queryBuf.append(RequestCategory.QUALITY_CONTROL_REQUEST_CATEGORY);
    queryBuf.append("'");

    queryBuf.append(" order by req.createDate desc, req.number desc , s.number asc");
  
  }
  
  
  

  private void addRequestCriteria() {
    // Search by request number 
    if (requestNumber != null && !requestNumber.equals("")){
      this.addWhereOrAnd();
      queryBuf.append(" req.number like '");
      queryBuf.append(requestNumber);
      queryBuf.append("%'");
    } 
    // Search by lab 
    if (idLab != null){
      this.addWhereOrAnd();
      queryBuf.append(" req.idLab =");
      queryBuf.append(idLab);
    } 
    // Search by project 
    if (idProject != null){
      this.addWhereOrAnd();
      queryBuf.append(" req.idProject =");
      queryBuf.append(idProject);
    } 
    // Search by user 
    if (idAppUser != null){
      this.addWhereOrAnd();
      queryBuf.append(" req.idAppUser = ");
      queryBuf.append(idAppUser);
    } 
    //  Search by create date from 
    if (createDateFrom != null){
      this.addWhereOrAnd();
      queryBuf.append(" req.createDate >= '");
      queryBuf.append(this.formatDate(createDateFrom));
      queryBuf.append("'");
    } 
    //  Search by create date from 
    if (createDateTo != null){
      this.addWhereOrAnd();
      queryBuf.append(" req.createDate <= '");
      queryBuf.append(this.formatDate(createDateTo) + " 12:00pm");
      queryBuf.append("'");
    } 
    // Search by request date last year
    if (requestDateLastYear != null && requestDateLastYear.equalsIgnoreCase("Y")) {
      Calendar end = Calendar.getInstance();
      end.add(Calendar.YEAR, -1);
      
      this.addWhereOrAnd();
      queryBuf.append(" req.createDate >= '");
      queryBuf.append(this.formatDate(end.getTime()));
      queryBuf.append("'");      
    } 
    // Search by request date last month
    else if (requestDateLastMonth != null && requestDateLastMonth.equalsIgnoreCase("Y")) {
        Calendar end = Calendar.getInstance();
        end.add(Calendar.MONTH, -1);
        
        this.addWhereOrAnd();
        queryBuf.append(" req.createDate >= '");
        queryBuf.append(this.formatDate(end.getTime()));
        queryBuf.append("'");      
    }
    // Search by request date last month
    else if (requestDateLastWeek != null && requestDateLastWeek.equalsIgnoreCase("Y")) {
        Calendar end = Calendar.getInstance();
        end.add(Calendar.DAY_OF_YEAR, -7);
        
        this.addWhereOrAnd();
        queryBuf.append(" req.createDate >= '");
        queryBuf.append(this.formatDate(end.getTime()));
        queryBuf.append("'");      
    }
    
  }
 
  private void addHybCriteria() {
    
    //  Search by isComplete
    if (isComplete != null && isComplete.equalsIgnoreCase("Y")){
      this.addWhereOrAnd();
      queryBuf.append(" hyb.extractionDate != null");
    }   
    
    //  Search by isNotComplete
    if (isNotComplete != null && isNotComplete.equalsIgnoreCase("Y")){
      this.addWhereOrAnd();
      queryBuf.append(" hyb.extractionDate is null");
    }   
  }

  private void addQualityControlCriteria() {
    
    //  Search by isComplete
    if (isComplete != null && isComplete.equalsIgnoreCase("Y")){
      this.addWhereOrAnd();
      queryBuf.append(" s.qualDate != null");
    }  
    
    // Search by isNotComplete
    if (isNotComplete != null && isNotComplete.equalsIgnoreCase("Y")){
      this.addWhereOrAnd();
      queryBuf.append(" s.qualDate is null");
    }   
    
  }

  private void addSecurityCriteria() {
    secAdvisor.addSecurityCriteria(queryBuf, "req", addWhere, true);
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

  
  public Integer getIdLab() {
    return idLab;
  }

  
  public Integer getIdUser() {
    return idAppUser;
  }

  
  public String getRequestNumber() {
    return requestNumber;
  }

  
  public void setIdLab(Integer idLab) {
    this.idLab = idLab;
  }

  
  public void setIdUser(Integer idAppUser) {
    this.idAppUser = idAppUser;
  }

  
  public void setRequestNumber(String requestNumber) {
    this.requestNumber = requestNumber;
  }

  
  public Date getCreateDateFrom() {
    return createDateFrom;
  }


  
  public void setCreateDateFrom(Date createDateFrom) {
    this.createDateFrom = createDateFrom;
  }

  
  public Date getCreateDateTo() {
    return createDateTo;
  }

  
  public void setCreateDateTo(Date createDateTo) {
    this.createDateTo = createDateTo;
  }

  
  public Integer getIdAppUser() {
    return idAppUser;
  }

  
  public void setIdAppUser(Integer idAppUser) {
    this.idAppUser = idAppUser;
  }


  
  public String getIsComplete() {
    return isComplete;
  }


  
  public void setIsComplete(String isComplete) {
    this.isComplete = isComplete;
  }


  
  public String getIsNotComplete() {
    return isNotComplete;
  }


  
  public void setIsNotComplete(String isNotComplete) {
    this.isNotComplete = isNotComplete;
  }


  
  public String getRequestDateLastMonth() {
    return requestDateLastMonth;
  }


  
  public void setRequestDateLastMonth(String requestDateLastMonth) {
    this.requestDateLastMonth = requestDateLastMonth;
  }


  
  public String getRequestDateLastWeek() {
    return requestDateLastWeek;
  }


  
  public void setRequestDateLastWeek(String requestDateLastWeek) {
    this.requestDateLastWeek = requestDateLastWeek;
  }


  
  public String getRequestDateLastYear() {
    return requestDateLastYear;
  }


  
  public void setRequestDateLastYear(String requestDateLastYear) {
    this.requestDateLastYear = requestDateLastYear;
  }


  
  public Integer getIdProject() {
    return idProject;
  }


  
  public void setIdProject(Integer idProject) {
    this.idProject = idProject;
  }


  
  
}
