/*
 * $Id: SpeedLimitManager.java,v 1.1 2012-10-29 22:29:43 HCI\rcundick Exp $
 *
 * Created on December 13, 2006, 11:16 AM
 *
 */
package lia.util.net.copy.transport;

import gui.Log;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import lia.util.net.common.Utils;

/**
 * This class manages all the speed limits for FDTSession-s
 * 
 * @author ramiro
 */
public class SpeedLimitManager {
    
    private static final SpeedLimitManager _thisInstance = new SpeedLimitManager();

    private static final Log logger = Log.getLoggerInstance();
    
    private final ScheduledThreadPoolExecutor executor;
    
    private static final class SpeedLimiterTask implements Runnable {
        final SpeedLimiter speedLimiter;
        long lastUpdate;
        private SpeedLimiterTask(SpeedLimiter speedLimiter) {
            this.speedLimiter = speedLimiter;
        }
        
        public void run() {
            try {
                final long now = System.currentTimeMillis();
                double newAvailable;
                
                final long rateLimit = speedLimiter.getRateLimit();
                if(logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINEST, "[ SpeedLimitManagerTask ] " + speedLimiter + " rateLimit: " + rateLimit);
                }
                if(lastUpdate == 0) {
                    newAvailable = rateLimit;
                } else {
                    newAvailable = rateLimit * ( (now - lastUpdate)/1000D );
                }
                lastUpdate = now;
                if(logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, "[ SpeedLimitManagerTask ] " + speedLimiter + " av: " + newAvailable);
                }
                speedLimiter.notifyAvailableBytes(Math.round(newAvailable));
            } catch(Throwable t) {
                logger.log(Level.WARNING, " SpeedLimiterTask got exception notifying " + this.speedLimiter, t) ;
            }
        }
    }
    
    /** Creates a new instance of LimitManager */
    private SpeedLimitManager() {
        executor = Utils.getSchedExecService("SpeedLimitManager", 2, Thread.MIN_PRIORITY + 1);
    }
    
    public static final SpeedLimitManager getInstance() {
        return _thisInstance;
    }
    
    public ScheduledFuture<?> addLimiter(SpeedLimiter speedLimiter) throws Exception {
        final long delay = speedLimiter.getNotifyDelay();
        if(logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, " Adding SpeedLimiterTask for " + speedLimiter + " delay: " + delay);
        }
        return executor.scheduleWithFixedDelay(new SpeedLimiterTask(speedLimiter), 0, delay, TimeUnit.MILLISECONDS);
    }
}
