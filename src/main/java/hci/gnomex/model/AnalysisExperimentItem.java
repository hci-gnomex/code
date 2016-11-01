package hci.gnomex.model;



import hci.hibernate5utils.HibernateDetailObject;



public class AnalysisExperimentItem extends HibernateDetailObject {
  
  private Integer        idAnalysisExperimentItem;
  private Integer        idAnalysis;
  private Analysis       analysis;
  private Hybridization  hybridization;
  private SequenceLane   sequenceLane;
  private Sample		 sample;
  private Integer        idHybridization;
  private Integer        idSequenceLane;
  private Integer        idRequest;
  private Integer		 idSample;
  
  public Integer getIdAnalysisExperimentItem() {
    return idAnalysisExperimentItem;
  }
  
  public void setIdAnalysisExperimentItem(Integer idAnalysisExperimentItem) {
    this.idAnalysisExperimentItem = idAnalysisExperimentItem;
  }
  
  public Integer getIdAnalysis() {
    return idAnalysis;
  }
  
  public void setIdAnalysis(Integer idAnalysis) {
    this.idAnalysis = idAnalysis;
  }
  
  public Analysis getAnalysis() {
    return analysis;
  }
  
  public void setAnalysis(Analysis analysis) {
    this.analysis = analysis;
  }
  
  public Hybridization getHybridization() {
    return hybridization;
  }
  
  public void setHybridization(Hybridization hybridization) {
    this.hybridization = hybridization;
  }
  
  public SequenceLane getSequenceLane() {
    return sequenceLane;
  }
  
  public void setSequenceLane(SequenceLane sequenceLane) {
    this.sequenceLane = sequenceLane;
  }
  
  public Sample getSample() {
	return sample;
  }
  
  public void setSample(Sample sample) {
	this.sample = sample;
  }
  
  public void registerMethodsToExcludeFromXML() {
    this.excludeMethodFromXML("getAnalysis");
  }

  
  public Integer getIdHybridization() {
    return idHybridization;
  }

  
  public void setIdHybridization(Integer idHybridization) {
    this.idHybridization = idHybridization;
  }

  
  public Integer getIdSequenceLane() {
    return idSequenceLane;
  }

  
  public void setIdSequenceLane(Integer idSequenceLane) {
    this.idSequenceLane = idSequenceLane;
  }

  
  public Integer getIdRequest() {
    return idRequest;
  }

  
  public void setIdRequest(Integer idRequest) {
    this.idRequest = idRequest;
  }
  
  public Integer getIdSample() {
	return idSample;
  }
  
  public void setIdSample(Integer idSample) {
	this.idSample = idSample;
  }
  
 
}