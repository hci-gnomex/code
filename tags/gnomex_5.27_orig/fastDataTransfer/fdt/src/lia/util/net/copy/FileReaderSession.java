/*
 * $Id: FileReaderSession.java,v 1.1 2012-10-29 22:29:53 HCI\rcundick Exp $
 */
package lia.util.net.copy;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.UUID;
import java.util.logging.Logger;

import lia.util.net.common.FileChannelProvider;

/**
 * Wrapper class over a current file which is being read
 * 
 * @author ramiro
 */
public class FileReaderSession extends FileSession {

    @Override
    public String toString() {
        return "FileReaderSession [file=" + file + ", partitionID=" + partitionID + ", sessionID=" + sessionID + ", sessionSize=" + sessionSize + "]";
    }

    private static final Logger logger = Logger.getLogger(FileSession.class.getName());

    public FileReaderSession(String fileName, FDTSession fdtSession, boolean isLoop, FileChannelProvider fileChannelProvider) throws IOException {
        this(UUID.randomUUID(), fdtSession, fileName, isLoop, fileChannelProvider);
    }

    public FileReaderSession(UUID uid, FDTSession fdtSession, String fileName, boolean isLoop, FileChannelProvider fileChannelProvider) throws IOException {
        super(uid, fdtSession, fileName, isLoop, fileChannelProvider);

        this.fileName = file.getAbsolutePath();
        this.file = this.fileChannelProvider.getFile(fileName);
		
		if (!file.exists()) {
            throw new IOException("No such file: " + fileName);
        }

        sessionSize = file.length();

        if (fileName.startsWith(FileSession.DEV_ZERO_FILENAME)) {
            this.sessionSize = -1;
            return;
        }

        if (!file.isFile()) {
            throw new IOException("The specified name [ " + fileName + " ] is not a file!");
        }
        
        this.partitionID = this.fileChannelProvider.getPartitionID(this.file);
    }

    public FileChannel getChannel() throws Exception {

        synchronized (closeLock) {
            if (isClosed()) {
                if (!isLoop) {
                    throw new IOException("FileReaderSession closed!");
                }
            } else {
                if(this.fileChannel != null) {
                    return this.fileChannel;
                }
            }

            try {
                fileChannel = this.fileChannelProvider.getFileChannel(file, null);
            } catch (Exception ex) {
                close("Cannot instantiate fileChannel", ex);
                throw ex;
            }
        }

        return fileChannel;
    }

    // this is always called with closeLock taken !
    protected void internalClose() {
        super.internalClose();
        if (isLoop) {
            closed = false;
        }
    }

}
