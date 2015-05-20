/*
 * $Id: DiskReaderManager.java,v 1.1 2012-10-29 22:29:56 HCI\rcundick Exp $
 */
package lia.util.net.copy.disk;

import java.util.concurrent.TimeUnit;

import lia.util.net.common.Utils;
import lia.util.net.copy.monitoring.DiskReaderManagerMonitoringTask;

/**
 * 
 * Disk reader Yoda :)
 * 
 * @author ramiro
 * 
 */
public class DiskReaderManager extends GenericDiskManager {

    private static final DiskReaderManager _theInstance = new DiskReaderManager();
    private DiskReaderManagerMonitoringTask monTask; 

    private DiskReaderManager() {
        monTask = new DiskReaderManagerMonitoringTask(this); 
        Utils.getMonitoringExecService().scheduleWithFixedDelay(monTask, 5, 5, TimeUnit.SECONDS);
    }
    
    public static final DiskReaderManager getInstance() {
        return _theInstance;
    }
    
    protected void internalClose() {
        
    }
    
    public long getSize() {
        return -1;
    }

}
