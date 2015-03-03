/*
 * $Id: ProcessorInfo.java 659 2012-03-04 20:07:24Z ramiro $
 */
package lia.util.net.copy.filters;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.Map;

import lia.util.net.copy.FileSession;

/**
 * 
 * This class encapsulates the file list which is to be, or has been transfered
 * and the destination directory.
 *  
 * @author ramiro
 */
public class ProcessorInfo {
    
    public String[] fileList;
    public String destinationDir;
    /**
     * @since 0.9.25
     */
    public InetAddress remoteAddress;
    /**
     * @since 0.9.25
     */
    public int remotePort;
    /**
     * @since 0.9.25
     */
    public boolean recursive;
    
    /**
     * Non-null on writer side <b>ONLY</b>.
     * </br>
     * Gives access to the transfer map of an FDT session. 
     * </br>
     * Key - the final file name (including the destination directory) for a {@link FileSession}</br>
     * Value - the {@link FileSession}</br>
     * 
     * @see FileSession
     * @since 0.10.0 
     */
    public Map<String, FileSession> fileSessionMap;

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("ProcessorInfo [destinationDir=")
               .append(destinationDir)
               .append(", remoteAddress=")
               .append(remoteAddress)
               .append(", remotePort=")
               .append(remotePort)
               .append(", recursive=")
               .append(recursive)
               .append(", fileList=")
               .append(Arrays.toString(fileList))
               .append(", fileSessionMap=")
               .append(fileSessionMap)
               .append("]");
        return builder.toString();
    }
    
}
