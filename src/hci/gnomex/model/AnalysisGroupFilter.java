package hci.gnomex.model;


import hci.gnomex.security.SecurityAdvisor;
import hci.framework.model.DetailObject;

import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class AnalysisGroupFilter extends DetailObject {
  
  
  // Criteria
  private Integer               idLab;
  private String                searchPublicProjects;


  private StringBuffer          queryBuf;
  private boolean              addWhere = true;
  private SecurityAdvisor       secAdvisor;
  
  
  public StringBuffer getQuery(SecurityAdvisor secAdvisor) {
    this.secAdvisor = secAdvisor;
    queryBuf = new StringBuffer();
    addWhere = true;
    
    queryBuf.append(" SELECT ag.idAnalysisGroup, ");
    queryBuf.append("        ag.name, ");
    queryBuf.append("        ag.description, ");
    queryBuf.append("        ag.idLab, ");
    queryBuf.append("        aglab.name, ");
    queryBuf.append("        a.idAnalysis, ");
    queryBuf.append("        a.number, ");
    queryBuf.append("        a.name, ");
    queryBuf.append("        a.description, ");
    queryBuf.append("        a.createDate,  ");
    queryBuf.append("        a.idLab, ");
    queryBuf.append("        alab.name, ");
    queryBuf.append("        a.idAnalysisType, ");
    queryBuf.append("        a.idAnalysisProtocol, ");
    queryBuf.append("        a.idOrganism, ");
    queryBuf.append("        a.idGenomeBuild, ");
    queryBuf.append("        a.codeVisibility ");
    
    getQueryBody(queryBuf);
    
    return queryBuf;
    
  }
  
  public void getQueryBody(StringBuffer queryBuf) {
    
    queryBuf.append(" FROM                AnalysisGroup as ag ");
    queryBuf.append(" JOIN                ag.analysisItems as a ");
    queryBuf.append(" LEFT JOIN           ag.lab as aglab ");
    queryBuf.append(" LEFT JOIN           a.lab as alab ");

    addAnalysisCriteria();
    
    addSecurityCriteria();
    
    
    queryBuf.append(" order by ag.name, a.number ");
  
  }
  

  
  private void addAnalysisCriteria() {
    // Search by lab 
    if (idLab != null){
      this.addWhereOrAnd();
      queryBuf.append(" a.idLab =");
      queryBuf.append(idLab);
    } 
    
  }
  
  private void addSecurityCriteria() {
    
    boolean scopeToGroup = true;
    if (this.searchPublicProjects != null && this.searchPublicProjects.equalsIgnoreCase("Y")) {
      scopeToGroup = false;
    }
    
    if (secAdvisor.hasPermission(secAdvisor.CAN_ACCESS_ANY_OBJECT)) {
   
      
    }  else {
      addWhere = secAdvisor.addSecurityCriteria(queryBuf, "a",       addWhere, scopeToGroup);
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
  
  protected boolean addWhereOrOr() {
    if (addWhere) {
      queryBuf.append(" WHERE ");
      addWhere = false;
    } else {
      queryBuf.append(" OR ");
    }
    return addWhere;
  }

  
  public Integer getIdLab() {
    return idLab;
  }

  
  
  
  public void setIdLab(Integer idLab) {
    this.idLab = idLab;
  }

  
  
  public String getSearchPublicProjects() {
    return searchPublicProjects;
  }

  
  public void setSearchPublicProjects(String searchPublicProjects) {
    this.searchPublicProjects = searchPublicProjects;
  }

    
}
