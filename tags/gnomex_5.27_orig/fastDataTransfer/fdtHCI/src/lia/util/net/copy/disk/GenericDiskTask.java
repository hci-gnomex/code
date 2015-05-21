/*
 * $Id: GenericDiskTask.java,v 1.1 2012-10-29 22:30:18 HCI\rcundick Exp $
 */
package lia.util.net.copy.disk;

import lia.util.net.common.DirectByteBufferPool;
import lia.util.net.copy.AccountableEntity;

/**
 * 
 * Base class for both Read/Write disk threads
 * 
 * @author ramiro
 * 
 */
public abstract class GenericDiskTask extends AccountableEntity implements Runnable {

    protected static final DirectByteBufferPool bufferPool = DirectByteBufferPool.getInstance();
    protected String myName;
    protected final int partitionID;
    protected final int taskID;

    public GenericDiskTask(final int partitionID, final int taskID) {
        this.partitionID = partitionID;
        this.taskID = taskID;
    }
    
    public long getSize() {
        return -1;
    }

}
