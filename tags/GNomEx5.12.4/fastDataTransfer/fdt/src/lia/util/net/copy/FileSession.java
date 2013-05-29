/*
 * $Id: FileSession.java,v 1.1 2012-10-29 22:29:55 HCI\rcundick Exp $
 */
package lia.util.net.copy;

import java.io.File;
import java.nio.channels.FileChannel;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.util.net.common.FileChannelProvider;

/**
 * 
 * This class is the FDT wrapper over the FileChannel which performs the I/O operations
 * 
 * @author ramiro
 */
public abstract class FileSession extends IOSession {

    private static final Logger logger = Logger.getLogger(FileSession.class.getName());
    
    public static final String DEV_NULL_FILENAME   =   "/dev/null";
    public static final String DEV_ZERO_FILENAME   =   "/dev/zero";

    protected final boolean isLoop;

    protected volatile String fileName;
    
    protected volatile FileChannel fileChannel;
    
    protected volatile File file;
    protected final FDTSession fdtSession;
    public final AtomicLong cProcessedBytes = new AtomicLong(0);
    protected int partitionID;
    
    protected volatile long lastModified;
    
    protected final boolean isNull;
    protected final boolean isZero;
    
    protected final FileChannelProvider fileChannelProvider;
    
    public FileSession(UUID uid, FDTSession fdtSession, String fileName, boolean isLoop, FileChannelProvider fileChannelProvider) {
        super(uid, -1);
        this.fdtSession = fdtSession;
        
        boolean bNull = false;
        boolean bZero = false;
        File iFile = null;
        
        this.fileChannelProvider = fileChannelProvider;
        
        try {
            this.isLoop = isLoop;
            
            //some checks
            if(fileName == null) throw new NullPointerException("The fileName cannot be null");

            iFile = new File(fileName);
            this.fileName = fileName;
            
            this.lastModified = iFile.lastModified();
            
            if(fileName.startsWith(DEV_NULL_FILENAME)) {
                iFile = new File(DEV_NULL_FILENAME);
                this.fileName = DEV_NULL_FILENAME;
                bNull = true;
                return;
            }
            
            if(fileName.startsWith(DEV_ZERO_FILENAME)) {
                iFile = new File(DEV_ZERO_FILENAME);
                bZero = true;
                this.fileName = DEV_ZERO_FILENAME;
                return;
            }
        } finally {
            file = iFile;
            isNull = bNull;
            isZero = bZero;
        }
    }
    
    public abstract FileChannel getChannel() throws Exception;

    public int partitionID() {
        return partitionID;
    }
    
    public long lastModified() {
        return lastModified;
    }
    
    public final boolean isNull() {
        return isNull;
    }

    public final boolean isZero() {
        return isZero;
    }
    
    public File getFile() {
        return file;
    }
    
    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }
    
    @Override
    protected void internalClose() {
        if(fileChannel != null) {
            try {
                fileChannel.close();
            }catch(Throwable t) {
                logger.log(Level.WARNING, " Got exception closing file " + file, t);
            }
        }
    }
    
    public String fileName() {
        return fileName;
    }
}
