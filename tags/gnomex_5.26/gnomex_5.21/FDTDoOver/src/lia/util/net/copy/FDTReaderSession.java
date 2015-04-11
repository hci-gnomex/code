/*
 * $Id: FDTReaderSession.java 683 2012-07-30 18:12:36Z ramiro $
 */
package lia.util.net.copy;

import java.io.File;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.util.net.common.Config;
import lia.util.net.common.FileChannelProvider;
import lia.util.net.common.Utils;
import lia.util.net.copy.disk.DiskReaderManager;
import lia.util.net.copy.disk.DiskReaderTask;
import lia.util.net.copy.filters.Postprocessor;
import lia.util.net.copy.filters.Preprocessor;
import lia.util.net.copy.filters.ProcessorInfo;
import lia.util.net.copy.transport.ControlChannel;
import lia.util.net.copy.transport.CtrlMsg;
import lia.util.net.copy.transport.FDTProcolException;
import lia.util.net.copy.transport.FDTSessionConfigMsg;
import lia.util.net.copy.transport.TCPSessionWriter;

/**
 * The "reader" session; it will send data over the wire
 * 
 * @author ramiro
 */
public class FDTReaderSession extends FDTSession implements FileBlockProducer {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(FDTReaderSession.class.getName());

    private static final DiskReaderManager diskManager = DiskReaderManager.getInstance();

    private static final Config config = Config.getInstance();

    private final TreeMap<Integer, ArrayList<DiskReaderTask>> readersMap;

    public final BlockingQueue<FileBlock> fileBlockQueue;

    private volatile ExecutorService execService;

    private String remoteDir;

    private boolean recursive;

    private boolean isFileList;

    private int totalFileBlocks = 0;

    private ProcessorInfo processorInfo;

    private int readersCount = 1;

    private static final int MAX_TAKE_POLL_ITER = config.getMaxTakePollIter();

    private final AtomicBoolean finalCleaupExecuted = new AtomicBoolean(false);

    private final AtomicBoolean finishNotifiedExecuted = new AtomicBoolean(false);

    public static final long END_RCV_WAIT_DELAY = TimeUnit.SECONDS.toNanos(120);

    /**
     * LOCAL SESSION - look in the Config
     * 
     * @param fileList
     * @throws Exception
     */
    public FDTReaderSession() throws Exception {
        super(FDTSession.CLIENT);
        fileBlockQueue = new ArrayBlockingQueue<FileBlock>(Utils.availableProcessors() * 2);
        // I am already connected here with the remote peer
        readersMap = new TreeMap<Integer, ArrayList<DiskReaderTask>>();
        diskManager.addSession(this);

        this.remoteDir = config.getDestinationDir();
        this.recursive = config.isRecursive();
        this.isFileList = (config.getConfigMap().get("-fl") != null);
        this.monID = config.getMonID();

        readersCount = config.getReadersCount();

        if (readersCount <= 0) {
            readersCount = 1;
        }

        localInit();
    }

    /**
     * REMOTE SESSION - wait for init()
     * 
     * @param fileList
     * @throws Exception
     */
    public FDTReaderSession(ControlChannel ctrlChannel) throws Exception {
        super(ctrlChannel, FDTSession.SERVER);
        fileBlockQueue = new ArrayBlockingQueue<FileBlock>(Utils.availableProcessors() * 2);
        readersMap = new TreeMap<Integer, ArrayList<DiskReaderTask>>();

        this.remoteDir = (String) ctrlChannel.remoteConf.get("-d");
        this.recursive = (ctrlChannel.remoteConf.get("-r") != null);
        this.isFileList = (ctrlChannel.remoteConf.get("-fl") != null);

        this.monID = (String) this.controlChannel.remoteConf.get("-monID");

        readersCount = config.getReadersCount();

        if (readersCount <= 0) {
            readersCount = 1;
        }

        String srReadersCount = (String) ctrlChannel.remoteConf.get("-rCount");

        int rReadersCount = readersCount;

        if (srReadersCount != null) {
            try {
                rReadersCount = Integer.parseInt(srReadersCount);
            } catch (Throwable t) {
                rReadersCount = readersCount;
            }
        }

        readersCount = rReadersCount;

        diskManager.addSession(this);
    }

    private void localInit() throws Exception {

        final String[] fileList = config.getFileList();
        final String[] remappedFileList = config.getRemappedFileList();
        this.recursive = config.isRecursive();

        internalInit(fileList, remappedFileList);
    }

    private void internalInit(final String[] fileList, final String[] remappedFileList) throws Exception {
        final boolean isFiner = logger.isLoggable(Level.FINER);
        final boolean isFine = isFiner || logger.isLoggable(Level.FINE);
        
        if(isFiner) {
            logger.log(Level.FINER, "\n\n FDTReaderSession - internalInit ENTER \n\n FileList:\n" + Arrays.toString(fileList) + "\n\nRemappedFileList:\n" + Arrays.toString(remappedFileList));
        }
        
        int filtersCount = 0;
        final ProcessorInfo processorInfo = new ProcessorInfo();
        final long sTime = System.nanoTime();
        
        /*check to see if they are allowed to download the files?
         * @author david.nix@hci.utah.edu
         */
		if (role==SERVER && config.getRestrictedTransferDirectories() != null){
			for (String name : fileList) {
				if (config.authorizedTransfer(new File(name)) == false) throw new Exception("Unauthorized download request for '"+name+"' from server.");
			}
		}

        try {
            final String preProcessFiltersProp = config.getPreFilters();

            if (preProcessFiltersProp == null || preProcessFiltersProp.length() == 0) {
                if (isFine) {
                    logger.log(Level.FINE, "No FDT Preprocess Filters defined");
                }
            } else {
                String[] preProcessFilters = preProcessFiltersProp.split(",");
                if (preProcessFilters == null || preProcessFilters.length == 0) {
                    logger.log(Level.WARNING, "Cannot understand -preFilters option!");
                } else {
                    filtersCount = preProcessFilters.length;

                    processorInfo.fileList = new String[fileList.length];
                    processorInfo.destinationDir = (this.remoteDir == null) ? config.getDestinationDir() : this.remoteDir;
                    processorInfo.remoteAddress = this.controlChannel.remoteAddress;
                    processorInfo.remotePort = this.controlChannel.remotePort;
                    processorInfo.recursive = this.recursive;

                    System.arraycopy(fileList, 0, processorInfo.fileList, 0, fileList.length);

                    for (final String filterName : preProcessFilters) {
                        Preprocessor preprocessor = (Preprocessor) (Class.forName(filterName).newInstance());
                        preprocessor.preProcessFileList(processorInfo, this.controlChannel.subject);
                    }
                }
            }
        } finally {
            StringBuilder sb = new StringBuilder();
            if (filtersCount > 0) {
                sb.append("[ FDTReaderSession ] Preprocessing: ")
                  .append(filtersCount)
                  .append(" filters in ")
                  .append(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - sTime))
                  .append(" ms");
            } else {
                sb.append("[ FDTReaderSession ] No pre processing filters defined/processed.");
            }
            logger.log(Level.INFO, sb.toString());
        }

        Map<String, String> initialMapping = new HashMap<String, String>();
        List<String> newFileList = new ArrayList<String>();
        Map<String, String> newRemappedFileList = new HashMap<String, String>();

        if (filtersCount > 0) {
            this.processorInfo = processorInfo;
            this.remoteDir = processorInfo.destinationDir;
            newFileList = new ArrayList<String>(processorInfo.fileList.length);
            newFileList.addAll(Arrays.asList(processorInfo.fileList));
        } else {
            if (recursive) {

                final int len = fileList.length;
                for (int iter = 0; iter < len; iter++) {
                    final String fName = fileList[iter];
                    final String remappedFName = (remappedFileList == null) ? null : remappedFileList[iter];
                    // System.out.println(" ==== START fName ==== " + fName);
                    List<String> tmpFL = new ArrayList<String>();
                    List<String> tmpRFL = new ArrayList<String>();
                    Utils.getRecursiveFiles(fName, remappedFName, tmpFL, tmpRFL);
                    if (!isFileList) {
                        for (String ffName : tmpFL) {
                            String parent = initialMapping.get(ffName);
                            if (parent != null) {
                                if (fName.length() > parent.length()) {
                                    parent = fName;
                                }
                            } else {
                                parent = fName;
                            }
                            if (new File(parent).isDirectory()) {
                                initialMapping.put(new File(ffName).getAbsolutePath(), new File(parent).getAbsolutePath());
                            }
                        }
                    }
                    newFileList.addAll(tmpFL);
                    int c = 0;
                    for (String fname : tmpFL) {
                        newRemappedFileList.put(new File(fname).getAbsolutePath(), tmpRFL.get(c++));
                    }
                }
            } else {
                newFileList = Arrays.asList(fileList);
                int c = 0;
                if (remappedFileList != null) {
                    for (String f : newFileList) {
                        newRemappedFileList.put(new File(f).getAbsolutePath(), remappedFileList[c++]);
                    }
                } else {
                    newRemappedFileList = null;
                }
            }
        }

        final FileChannelProvider fcp = Config.getInstance().getFileChannelProviderFactory().newReaderFileChannelProvider(this);

        for (final String fName : newFileList) {
            FileReaderSession frs = new FileReaderSession(fName, this, isLoop, fcp);
            fileSessions.put(frs.sessionID, frs);
            setSessionSize(sessionSize() + frs.sessionSize());
        }

        buildPartitionMap();
        int size = partitionsMap.size();

        if (size == 0) {
            throw new FDTProcolException("\n\nERROR: Cannot identify partition map for the specified fileList: " + Arrays.toString(fileList)
                    + " No such file or directory ??");
        }

        if(isFiner) {
            logger.log(Level.FINER, "\n\n FDTReaderSession - internalInit FINISHED - sendingRemoteSessions \n\n initialMapping:\n" + initialMapping + "\n\n newRemappedFileList:\n" + newRemappedFileList);
        }

        sendRemoteSessions(initialMapping, newRemappedFileList);
    }

    public long getSize() {
        return sessionSize();
    }

    private void sendRemoteSessions(final Map<String, String> initialMapping, Map<String, String> newRemappedFileList) throws Exception {
        FDTSessionConfigMsg sccm = new FDTSessionConfigMsg();

        sccm.destinationDir = this.remoteDir;
        sccm.recursive = recursive;

        int count = fileSessions.size();

        sccm.fileIDs = new UUID[count];
        sccm.fileLists = new String[count];
        sccm.remappedFileLists = new String[count];
        sccm.fileSizes = new long[count];
        sccm.lastModifTimes = new long[count];

        count = 0;
        for (Map.Entry<UUID, FileSession> entry : fileSessions.entrySet()) {

            FileSession fs = entry.getValue();

            sccm.fileIDs[count] = fs.sessionID;

            if (isFileList) {// -fl specified ... I don't care about the names
                sccm.fileLists[count] = fs.fileName;
                sccm.remappedFileLists[count] = (newRemappedFileList == null) ? null : newRemappedFileList.get(fs.fileName);
            } else if (initialMapping.size() == 0) { // only files ... no
                // -r(ecursive)
                // specified
                sccm.fileLists[count] = fs.getFile().getName();
            } else {
                String parent = initialMapping.get(fs.fileName);
                String name = fs.fileName;

                if (parent != null && parent.length() < name.length()) {
                    name = name.substring(parent.length() - new File(parent).getName().length());
                }

                if (parent == null) {
                    name = fs.getFile().getName();
                }

                sccm.fileLists[count] = name;
            }
            // System.out.println(" sccm.fileLists [ " + count + " ] = " +
            // sccm.fileLists[count]);
            sccm.fileSizes[count] = fs.sessionSize;
            sccm.lastModifTimes[count] = fs.lastModified;

            count++;
        }

        controlChannel.sendCtrlMessage(new CtrlMsg(CtrlMsg.FINAL_FDTSESSION_CONF, sccm));

        setCurrentState(FINAL_CONF_SENT);
    }

    public void setControlChannel(ControlChannel controlChannel) {
        this.controlChannel = controlChannel;
    }

    public boolean finishReader(int partitionID, DiskReaderTask drt) {

        boolean bRet = true;

        synchronized (readersMap) {
            final ArrayList<DiskReaderTask> readersList = readersMap.get(Integer.valueOf(partitionID));
            if (readersList != null) {
                if (!readersList.remove(drt)) {
                    bRet = false;
                    logger.log(Level.WARNING, " The DiskReaderTask ");
                }
            }
        }

        return bRet;
    }

    /**
     * @param partitionID
     */
    public void notifyReaderDown(int partitionID) {
        // TODO - not yet used
    }

    public void finishFileSession(UUID sessionID, Throwable dCause) {
        super.finishFileSession(sessionID, dCause);
        if (finishedSessions.size() == fileSessions.size()) {
            notifySessionFinished();
        }
    }

    public void startReading() {

        StringBuilder sb = new StringBuilder();

        sb.append(" Started DiskReaderTasks for the following partions [ ");
        int idx = 0;

        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, " partitionsMap is: " + partitionsMap);
        }

        for (Iterator<Map.Entry<Integer, LinkedList<FileSession>>> it = partitionsMap.entrySet().iterator(); it.hasNext();) {
            Map.Entry<Integer, LinkedList<FileSession>> entry = it.next();

            final int partitionID = entry.getKey().intValue();

            LinkedList<FileSession> files = entry.getValue();

            int realReadersCount = (readersCount < files.size()) ? readersCount : files.size();
            ArrayList<DiskReaderTask> readersTasks = new ArrayList<DiskReaderTask>(realReadersCount);

            ArrayList<LinkedList<FileSession>> fileSessionsReaders = new ArrayList<LinkedList<FileSession>>(realReadersCount);

            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, " realReadersCount = " + realReadersCount + " for partitionID: " + partitionID);
            }

            if (realReadersCount > 1) {
                FileSession[] filesArray = files.toArray(new FileSession[files.size()]);
                Arrays.sort(filesArray, new Comparator<FileSession>() {

                    public int compare(FileSession fileSession1, FileSession fileSession2) {
                        if (fileSession1.file.equals(fileSession2.file)) {
                            return fileSession1.sessionID.compareTo(fileSession2.sessionID);
                        }

                        if (fileSession1.sessionSize < fileSession2.sessionSize) {
                            return -1;
                        }

                        return 1;
                    }
                });

                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, "Sorted FileSession-s array: " + Arrays.toString(filesArray));
                }

                int ci = 0;
                for (FileSession fs : filesArray) {
                    LinkedList<FileSession> fsessions = (ci >= fileSessionsReaders.size()) ? null : fileSessionsReaders.get(ci);

                    if (fsessions == null) {
                        fsessions = new LinkedList<FileSession>();
                        fileSessionsReaders.add(fsessions);
                    }

                    fsessions.add(fs);

                    if (logger.isLoggable(Level.FINEST)) {
                        logger.log(Level.FINEST, " Added FileSession: " + fs + " for DiskReaderTask idx =  " + ci);
                    }
                    ci = (ci + 1) % realReadersCount;
                }

            } else {
                fileSessionsReaders.add(files);
            }

            execService = Utils.getStandardExecService("DiskReaderTask for " + toString(), partitionsMap.size(), partitionsMap.size()
                    * realReadersCount + 5, Thread.NORM_PRIORITY);

            for (int i = 0; i < realReadersCount; i++) {
                final DiskReaderTask drTask = new DiskReaderTask(partitionID, idx++, fileSessionsReaders.get(i), this);
                readersTasks.add(drTask);
                execService.submit(drTask);
            }

            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, " ReadersTasks for partitionID: " + partitionID + ": " + readersTasks);
            }

            readersMap.put(Integer.valueOf(partitionID), readersTasks);
            sb.append(partitionID).append(" ");
        }

        sb.append("] for FDTSession: ").append(sessionID);

        logger.log(Level.INFO, sb.toString());

    }

    private boolean doPostProcessing() throws Exception {

        if (!postProcessingDone.compareAndSet(false, true)) {
            return false;
        }

        final long sTime = System.nanoTime();
        int filtersCount = 0;

        try {
            logger.log(Level.INFO, "[ FDTReaderSession ] Post Processing started");
            final String postProcessFiltersProp = config.getPostFilters();

            if (postProcessFiltersProp == null || postProcessFiltersProp.length() == 0) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, " [ FDTReaderSession ] No FDT PostProcessor Filters defined");
                }
            } else {
                final String[] postProcessFilters = postProcessFiltersProp.split(",");
                if (postProcessFilters == null || postProcessFilters.length == 0) {
                    logger.log(Level.WARNING, "Cannot understand -postFilters");
                } else {
                    filtersCount = postProcessFilters.length;

                    for (final String filterName : postProcessFilters) {
                        Postprocessor preprocessor = (Postprocessor) (Class.forName(filterName).newInstance());
                        preprocessor.postProcessFileList(this.processorInfo, this.controlChannel.subject, downCause(), downMessage());
                    }

                }
            }
        } finally {
            StringBuffer sb = new StringBuffer();
            if (filtersCount > 0) {
                sb.append("[ FDTReaderSession ] Postprocessing: ")
                  .append(filtersCount)
                  .append(" filters in ")
                  .append(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - sTime))
                  .append(" ms");
            } else {
                sb.append("[ FDTReaderSession ] No post processing filters defined/processed.");
            }
            logger.log(Level.INFO, sb.toString());
        }

        return (filtersCount > 0);
    }

    private void notifySessionFinished() {

        if (finishNotifiedExecuted.compareAndSet(false, true)) {
            StringBuilder downNotif = null;

            try {
                if (downMessage() != null && downCause() != null) {

                    downNotif = new StringBuilder();

                    if (downMessage() != null) {
                        downNotif.append("Down message: ").append(downMessage()).append("\n");
                    }

                    if (downCause() != null) {
                        downNotif.append("Down cause:\n").append(Utils.getStackTrace(downCause())).append("\n");
                    }
                }
            } catch (Throwable t1) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, "[ FDTReaderSession ] [ notifySessionFinished ]  Got exception building the remote notify message", t1);
                }
            }

            try {
                controlChannel.sendCtrlMessage(new CtrlMsg(CtrlMsg.END_SESSION, (downNotif == null) ? md5Sums : downNotif.toString()));
            } catch (Throwable t1) {
                logger.log(Level.WARNING, " [ FDTReaderSession ] [ notifySessionFinished ] got exception sending END_SESSION message", t1);
            }
        }
    }

    private void finalCleanup() {
        if (finalCleaupExecuted.compareAndSet(false, true)) {
            final boolean isFiner = logger.isLoggable(Level.FINER);
            final boolean isFine = isFiner || logger.isLoggable(Level.FINE);

            try {
                final long sTime = System.nanoTime();
                while ((currentState() & END_RCV) != END_RCV) {
                    try {
                        Thread.sleep(1000);
                        notifySessionFinished();
                    } catch (InterruptedException ie) {
                        Thread.interrupted();
                    } catch (Throwable t) {
                        if (isFine) {
                            logger.log(Level.FINE, "[finalCleanup] exception notifying sessionFinished. Cause", t);
                        }
                    }

                    if (controlChannel.isSocketClosed()) {
                        break;
                    }

                    if (System.nanoTime() - sTime > END_RCV_WAIT_DELAY) {
                        logger.log(Level.WARNING, " Remote FDT Writer session went down since we finished reading. The session will finish");
                        break;
                    }
                }
            } catch (Throwable t) {
                if (isFine) {
                    logger.log(Level.FINE, "[finalCleanup] exception notifying sessionFinished. Cause", t);
                }
            }

            if (isFiner) {
                logger.log(Level.FINER, "\n\n\n [ FDTReaderSession ]  [ finalCleanup ]   EXECUTING !!!!!!!!!!! \n\n");
            }

            // log final statistics
            try {
                StringBuilder sb = new StringBuilder();
                sb.append("\n\nFDTReaderSession ( ").append(sessionID);
                if (monID != null) {
                    sb.append(" / ").append(monID);
                }
                sb.append(" ) final stats:");
                sb.append("\n Started: ").append(new Date(startTimeMillis));
                sb.append("\n Ended:   ").append(new Date());
                sb.append("\n Transfer period:   ").append(Utils.getETA(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTimeNanos)));
                sb.append("\n TotalBytes: ").append(getTotalBytes());
                if (transportProvider != null) {
                    sb.append("\n TotalNetworkBytes: ").append(transportProvider.getUtilBytes());
                    try {
                        if (!Utils.updateTotalReadContor(transportProvider.getUtilBytes())) {
                            if (logger.isLoggable(Level.FINEST)) {
                                logger.log(Level.FINEST, " [ FDTReaderSession ] Unable to update the contor in the update file.");
                            }
                        }
                    } catch (Throwable tu) {
                        if (logger.isLoggable(Level.FINEST)) {
                            logger.log(Level.FINEST, " [ FDTReaderSession ] Unable to update the contor in the update file. Cause: ", tu);
                        }
                    } finally {
                        transportProvider.close(downMessage(), downCause());
                    }
                } else {
                    sb.append("\n TotalNetworkBytes: 0");
                }
                sb.append("\n Exit Status: ").append((downCause() == null && downMessage() == null) ? "OK" : "Not OK");
                sb.append("\n");
                System.out.println(sb.toString());
            } catch (Throwable t) {
                logger.log(Level.WARNING,
                           "[ FDTReaderSession ] [ finalCleanup ] [ HANDLED ] Exception getting final statistics. Smth went dreadfully wrong!",
                           t);
            }

            try {
                for (final FileSession fileSession : fileSessions.values()) {
                    try {
                        fileSession.close(downMessage(), downCause());
                    } catch (Throwable ign) {
                        if (isFiner) {
                            logger.log(Level.FINER, "finalCleanup - exception closing file. Cause: ", ign);
                        }
                    }
                }
            } catch (Throwable ignore) {
                if (isFiner) {
                    logger.log(Level.FINER, "finalCleanup - exception closing files. Cause: ", ignore);
                }
            }

            try {
                doPostProcessing();
            } catch (Throwable t) {
                logger.log(Level.WARNING, "\n [ FDTReaderSession ] Got exception in postProcessing", t);
            }

            try {
                final ControlChannel controlChannel = this.controlChannel;
                if (controlChannel != null) {
                    controlChannel.close(downMessage(), downCause());
                }
            } catch (Throwable ignore) {
                if (isFiner) {
                    logger.log(Level.FINER, "finalCleanup - exception closing control channel. Cause: ", ignore);
                }
            }

            final ExecutorService execService = this.execService;
            if (execService != null) {
                try {
                    execService.shutdown();
                    execService.awaitTermination(10, TimeUnit.SECONDS);
                } catch (Throwable ignore) {
                    if (isFiner) {
                        logger.log(Level.FINER, "finalCleanup - exception closing executor service. Cause: ", ignore);
                    }
                } finally {
                    execService.shutdownNow();
                }
            }

            try {
                diskManager.removeSession(this, downMessage(), downCause());
            } catch (Throwable ignore) {
                if (isFiner) {
                    logger.log(Level.FINER, "finalCleanup - exception removing session from diskManager. Cause: ", ignore);
                }
            }

            try {
                final int retBufs = Utils.drainFileBlockQueue(fileBlockQueue);
                if (isFiner) {
                    logger.log(Level.FINER, "finalCleanup - recovered " + retBufs + " buffers");
                }
            } catch (Throwable t) {
                logger.log(Level.WARNING, "\n\n [ FDTReaderSession ] [ HANDLED ] exception returning buffers to pool \n\n ", t);
            }

            try {
                FDTSessionManager.getInstance().finishSession(sessionID, downMessage(), downCause());
            } catch (Throwable ignore) {
                if (isFiner) {
                    logger.log(Level.FINER, "finalCleanup - exception closing session in FDTSessionManager. Cause: ", ignore);
                }
            }
        }
    }

    protected void internalClose() throws Exception {

        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, " [ FDTReaderSession ] enters internalClose downMsg: " + downMessage() + " ,  downCause: " + downCause());
        }

        try {
            super.internalClose();
        } catch (Throwable t) {
            logger.log(Level.WARNING, " [ FDTReaderSession ] [ HANDLED ] internalClose exception in base class.", t);
        }

        final String downMessage = downMessage();
        final Throwable downCause = downCause();

        if (downMessage != null || downCause != null) {
            final String downLogMsg = (downMessage == null) ? "N/A" : downMessage;
            logger.log(Level.INFO,
                       "\nThe FDTReaderSession ( " + sessionID + " ) finished with error(s). downMsg: " + downLogMsg + " downCause:",
                       downCause());
            try {
                finalCleanup();
            } catch (Throwable t) {
                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, "[internalClose] Exception in finalCleanup. Cause: ", t);
                }
            }
        } else {
            Runnable r = new Runnable() {

                public void run() {
                    finalCleanup();
                }
            };

            Utils.getMonitoringExecService().schedule(r, 3, TimeUnit.SECONDS);
        }
    }

    @Override
    public void handleInitFDTSessionConf(CtrlMsg ctrlMsg) throws Exception {

        FDTSessionConfigMsg sccm = (FDTSessionConfigMsg) ctrlMsg.message;

        this.remoteDir = sccm.destinationDir;
        this.recursive = sccm.recursive;

        internalInit(sccm.fileLists, sccm.remappedFileLists);
    }

    @Override
    public void handleFinalFDTSessionConf(CtrlMsg ctrlMsg) throws Exception {
        if (!(ctrlMsg.message instanceof UUID[])) {
            FDTProcolException fpe = new FDTProcolException("Illegal message FINAL_FDT_CONF in ReaderSesssion without UUID[] as atttach. "
                    + ctrlMsg.message);
            fpe.fillInStackTrace();
            throw fpe;
        }

        UUID[] finishedSessions = (UUID[]) (ctrlMsg.message);
        for (UUID fSession : finishedSessions) {
            final FileSession fs = fileSessions.get(fSession);
            if (fs != null) {
                addAndGetUtilBytes(fs.sessionSize);
                addAndGetTotalBytes(fs.sessionSize);
            } else {
                logger.log(Level.WARNING, " No such UUID: " + fSession + " received from remote FDTWriterSession in local fileSessions list");
            }
            finishFileSession(fSession, null);
        }

    }

    @Override
    public void handleEndFDTSession(CtrlMsg ctrlMsg) throws Exception {

        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "\n\n\n\n\n\n ---------------- [ FDTReaderSession ] handleEndFDTSession. Msg: " + ctrlMsg.message);
        }

        String remoteDownMsg = null;
        try {
            // SO I can close now ... wherever the status was before
            if (ctrlMsg.message != null && ctrlMsg.message instanceof String) {
                remoteDownMsg = (String) ctrlMsg.message;
                close(remoteDownMsg, null);
                logger.log(Level.WARNING, "\n\n [ FDTReaderSession ] Remote FDTWriterSession for session [ " + sessionID
                        + " ] finished with errors:\n" + remoteDownMsg + "\n");
            } else {
                // everything went fine :)
                logger.log(Level.INFO, "[ FDTReaderSession ] Remote FDTWriterSession for session [ " + sessionID + " ] finished OK!");
                close(null, null);
            }
        } finally {
            close((remoteDownMsg != null) ? remoteDownMsg : downMessage(), downCause());
        }
    }

    /**
     * @param ctrlMsg  
     */
    @Override
    public void handleStartFDTSession(CtrlMsg ctrlMsg) throws Exception {
        // I will start the TransportProvider ... if every thing works as
        // expected I will start sending
        boolean sendCookie = true;
        if (role == CLIENT) {
            sendCookie = false;
            transportProvider = new TCPSessionWriter(this, InetAddress.getByName(config.getHostName()), config.getPort(), config.getSockNum());
        } else {
            transportProvider = new TCPSessionWriter(this);
        }

        controlChannel.sendCtrlMessage(new CtrlMsg(CtrlMsg.START_SESSION, null));
        // I'm still in sync ... if smth goes wrong the state will not be set
        setCurrentState(START_SENT);

        startReading();

        setCurrentState(TRANSFERING);
        transportProvider.startTransport(sendCookie);
    }

    public void transportWorkerDown() throws Exception {
        // fileBlockQueue.offer(FileBlock.EOF_FB);
        close("wroker down", null);
    }

    public FileBlock take() throws InterruptedException {

        FileBlock fb = null;
        // try a spinning poll(), avoid signaling
        for (int i = 0; i < MAX_TAKE_POLL_ITER; i++) {
            fb = fileBlockQueue.poll();
            if (fb != null) {
                return fb;
            }
        }

        fb = fileBlockQueue.take();
        if (fb != null) {
            totalFileBlocks++;
        }
        return fb;
    }

    public FileBlock poll() {
        final FileBlock fb = fileBlockQueue.poll();
        if (fb != null) {
            totalFileBlocks++;
        }
        return fb;
    }

    public FileBlock poll(long delay, TimeUnit unit) throws InterruptedException {
        FileBlock fb = null;
        fb = fileBlockQueue.poll(delay, unit);

        if (fb != null) {
            totalFileBlocks++;
        }

        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, " Polling for FileBlock qSize: " + fileBlockQueue.size() + " processedFBS: " + totalFileBlocks);
        }

        return fb;
    }
}
