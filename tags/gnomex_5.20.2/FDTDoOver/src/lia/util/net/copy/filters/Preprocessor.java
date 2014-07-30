/*
 * $Id: Preprocessor.java 348 2007-08-16 14:02:52Z ramiro $
 */
package lia.util.net.copy.filters;

import javax.security.auth.Subject;

/**
 * 
 * Base class used to implement pre filters plugins in FDT. The <code>Preprocessor</code>
 * is called before a FDT session starts.
 *  
 * @author ramiro
 */
public interface Preprocessor {
    public void preProcessFileList(ProcessorInfo processorInfo, Subject peerSubject) throws Exception; 
}
