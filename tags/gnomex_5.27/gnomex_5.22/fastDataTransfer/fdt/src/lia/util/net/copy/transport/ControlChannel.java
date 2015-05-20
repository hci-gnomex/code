/*
 * $Id: ControlChannel.java,v 1.1 2012-10-29 22:30:09 HCI\rcundick Exp $
 */
package lia.util.net.copy.transport;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.security.auth.Subject;

import lia.gsi.GSIServer;
import lia.gsi.net.GSIGssSocketFactory;
import lia.util.net.common.AbstractFDTCloseable;
import lia.util.net.common.Config;
import lia.util.net.common.DirectByteBufferPool;
import lia.util.net.common.Utils;

/**
 * Encapsulates the control socket ( channel ) between two peer FDTSessios When the constructor returns the
 * communication can begin ...
 * 
 * @author ramiro
 */
public class ControlChannel extends AbstractFDTCloseable implements Runnable {

    private static final Logger logger = Logger.getLogger(ControlChannel.class.getName());

    private static final CtrlMsg versionMsg = new CtrlMsg(CtrlMsg.PROTOCOL_VERSION, Config.FDT_FULL_VERSION + "-" + Config.FDT_RELEASE_DATE);

    private static final Config config = Config.getInstance();

    public static final int CONNECT_TIMEOUT = 20 * 1000;

    public static final int SOCKET_TIMEOUT = 60 * 1000;

    private Socket controlSocket;

    private ConcurrentLinkedQueue<Object> qToSend = new ConcurrentLinkedQueue<Object>();

    private AtomicBoolean cleanupFinished = new AtomicBoolean(false);

    private UUID fdtSessionID;

    private ObjectOutputStream oos = null;

    private ObjectInputStream ois = null;

    private ControlChannelNotifier notifier;

    private String fullRemoteVersion;

    public Map<String, Object> remoteConf;

    public final InetAddress remoteAddress;

    public final int remotePort;

    public final int localPort;

    public Subject subject;

    private String myName;

    private ScheduledFuture<?> ccptFuture;

    private static final class ControlChannelPingerTask implements Runnable {

        public static final CtrlMsg pingMsg = new CtrlMsg(CtrlMsg.KEEP_ALIVE_MSG, new byte[1]);

        private final ControlChannel cc;

        ControlChannelPingerTask(ControlChannel cc) {
            this.cc = cc;
            logger.log(Level.INFO, "[ ControlChannelPingerTask ] initialized");
        }

        public void run() {

            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "[ ControlChannelPingerTask ] sending KEEP_ALIVE_MSG");
            }

            try {
                this.cc.sendCtrlMessage(pingMsg);
            } catch (Throwable t) {
                logger.log(Level.WARNING, " [ ContrlChannelPingerTask ] Unable to send msg  ... Close the socket ??? This should not happen", t);
            }

        }
    }

    /**
     * Try to connect to a remote FDT instance
     * 
     * @param address
     * @param port
     * @param sessionID
     * @param notifier
     * @throws Exception
     */
    public ControlChannel(String address, int port, UUID sessionID, ControlChannelNotifier notifier) throws Exception {
        this(InetAddress.getByName(address), port, sessionID, notifier);
    }

    /**
     * Try to connect to a remote FDT instance
     * 
     * @param address
     * @param port
     * @param fdtSessionID
     * @param notifier
     * @throws Exception
     */
    public ControlChannel(InetAddress inetAddress, int port, UUID fdtSessionID, ControlChannelNotifier notifier) throws Exception {
        try {
            this.notifier = notifier;
            this.fdtSessionID = fdtSessionID;

            if (config.isGSIModeEnabled()) {
                GSIGssSocketFactory factory = new GSIGssSocketFactory();
                controlSocket = factory.createSocket(inetAddress, config.getGSIPort(), false, false);
                this.subject = GSIGssSocketFactory.getLocalSubject(controlSocket);
            } else {
                controlSocket = new Socket();
                controlSocket.connect(new InetSocketAddress(inetAddress, port), CONNECT_TIMEOUT);
            }

            this.remoteAddress = inetAddress;
            this.remotePort = port;
            this.localPort = controlSocket.getLocalPort();

            controlSocket.setTcpNoDelay(true);

            // only the first octet will be interpreted by the AcceptTask at the other end
            if (!config.isGSIModeEnabled()) {
                controlSocket.getOutputStream().write(new byte[] {
                    0
                });
            }

            // from now on only CtrlMsg will be sent
            initStreams();
            controlSocket.setSoTimeout(1000);

            //
        } catch (Throwable t) {
            close("Cannot instantiate ControlChannel", t);
            throw new Exception(t);
        }
    }

    public boolean isSocketClosed() {
        return (this.controlSocket == null) ? true : controlSocket.isClosed();
    }

    /**
     * A remote peer connected to FDT
     * 
     * @param s
     *            - the socket
     * @throws Exception
     *             - if anything goes wrong in intialization
     */
    public ControlChannel(Socket s, ControlChannelNotifier notifier) throws Exception {
        try {
            this.controlSocket = s;

            this.remoteAddress = s.getInetAddress();
            this.remotePort = s.getPort();
            this.localPort = s.getLocalPort();

            this.notifier = notifier;

            initStreams();
            controlSocket.setSoTimeout(1000);

        } catch (Throwable t) {
            close("Cannot instantiate ControlChannel", t);
            throw new Exception(t);
        }
    }

    /**
     * 
     */
    public ControlChannel(GSIServer parent, Socket s, Subject peerSubject, ControlChannelNotifier notifier) throws Exception {
        try {

            this.controlSocket = s;
            this.subject = peerSubject;
            this.remoteAddress = s.getInetAddress();
            this.remotePort = s.getPort();
            this.localPort = s.getLocalPort();

            this.notifier = notifier;

            initStreams();
            controlSocket.setSoTimeout(1000);

        } catch (Throwable t) {
            close("Cannot instantiate ControlChannel", t);
            throw new Exception(t);
        }
    }

    public UUID fdtSessionID() {
        return fdtSessionID;
    }

    public String toString() {
        return (controlSocket == null) ? "null" : controlSocket.toString();
    }

    @SuppressWarnings("unchecked")
    private void initStreams() throws Exception {
        oos = new ObjectOutputStream(new BufferedOutputStream(controlSocket.getOutputStream()));

        // send the version

        sendMsgImpl(versionMsg);

        ois = new ObjectInputStream(new BufferedInputStream(controlSocket.getInputStream()));

        // wait for remote version
        CtrlMsg ctrlMsg = (CtrlMsg) ois.readObject();
        if (ctrlMsg.tag != CtrlMsg.PROTOCOL_VERSION) {
            throw new FDTProcolException("Unexpected remote control message. Expected PROTOCOL_VERSION tag [ " + CtrlMsg.PROTOCOL_VERSION + " ] Received tag: " + ctrlMsg.tag);
        }

        this.fullRemoteVersion = (String) ctrlMsg.message;

        ctrlMsg = new CtrlMsg(CtrlMsg.INIT_FDT_CONF, Config.getInstance().getConfigMap());
        sendMsgImpl(ctrlMsg);

        // wait for remote config
        ctrlMsg = (CtrlMsg) ois.readObject();
        if (ctrlMsg.tag != CtrlMsg.INIT_FDT_CONF) {
            throw new FDTProcolException("Unexpected remote control message. Expected INIT_FDT_CONF tag [ " + CtrlMsg.INIT_FDT_CONF + " ] Received tag: " + ctrlMsg.tag);
        }

        this.remoteConf = (HashMap<String, Object>) ctrlMsg.message;
        try {

            if (DirectByteBufferPool.initInstance(Integer.parseInt((String) remoteConf.get("-bs")), config.getMaxTakePollIter())) {
                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, "The buffer pool has been initialized");
                }
            } else {
                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, "The buffer pool is already initialized");
                }
            }

        } catch (Throwable t) {
            throw new FDTProcolException("Unable to instantiate the buffer pool", t);
        }

        if (fdtSessionID == null) {// The remote peer should send my fdtSessionID

            ctrlMsg = (CtrlMsg) ois.readObject();
            if (ctrlMsg.tag == CtrlMsg.SESSION_ID) {
                fdtSessionID = (UUID) ctrlMsg.message;
            } else {
                throw new FDTProcolException("Unexpected remote control message. Expected SESSION_ID tag [ " + CtrlMsg.SESSION_ID + " ] Received tag: " + ctrlMsg.tag);
            }
        } else {// I should send the ID to the remote peer

            sendMsgImpl(new CtrlMsg(CtrlMsg.SESSION_ID, fdtSessionID));
        }
        myName = " ControlThread for ( " + fdtSessionID + " ) " + controlSocket.getInetAddress() + ":" + controlSocket.getPort();
        logger.log(Level.INFO, "NEW CONTROL stream for " + fdtSessionID + " initialized ");

        if (this.fullRemoteVersion != null && this.fullRemoteVersion.compareTo("0.9.0") >= 0) {
            synchronized (this.closeLock) {
                ccptFuture = Utils.getMonitoringExecService().scheduleWithFixedDelay(new ControlChannelPingerTask(this), 2 * 60, 2 * 60, TimeUnit.SECONDS);
            }
        } else {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "[ ControlChannel ] remote version " + fullRemoteVersion + " does not support KEEP_ALIVE messages");
            }
        }
    }

    public String remoteVersion() {
        return fullRemoteVersion;
    }

    private final void cleanup() {
        if (cleanupFinished.compareAndSet(false, true)) {
            if (ois != null) {
                try {
                    ois.close();
                } catch (Throwable _) {
                }
            }

            try {
                if (ccptFuture != null) {
                    ccptFuture.cancel(true);
                }
            } catch (Throwable _) {
            }

            if (oos != null) {
                try {
                    oos.close();
                } catch (Throwable _) {
                }
            }

            if (controlSocket != null) {
                try {
                    controlSocket.close();
                } catch (Throwable _) {
                }
            }

            if (notifier != null) {
                try {
                    notifier.notifyCtrlSessionDown(this, downCause());
                } catch (Throwable _) {
                }
            }
        }
    }

    public void sendCtrlMessage(final Object ctrlMsg) {

        if (ctrlMsg == null) {
            throw new NullPointerException("Control message cannot be null over the ControlChannel");
        }

        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "[ CtrlChannel ] adding to send queue msg: " + ctrlMsg.toString());
            if (logger.isLoggable(Level.FINEST)) {
                // do a thread dump
                Thread.dumpStack();
            }
        }

        qToSend.add(ctrlMsg);

    }

    private void sendAllMsgs() throws Exception {
        for (;;) {
            final Object ctrlMsg = qToSend.poll();
            if (ctrlMsg == null) {
                break;
            }
            sendMsgImpl(ctrlMsg);
        }
    }

    private void sendMsgImpl(Object o) throws Exception {
        try {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, " [ ControlChannel ] sending message " + o);
            }
            oos.writeObject(o);
            oos.reset();
            oos.flush();
        } catch (Throwable t) {
            if (!isClosed()) {
                close("Exception sending control data", t);
                throw new IOException(" Cannot send ctrl message ( " + t.getCause() + " ) ");
            }
        }
    }

    public void run() {
        final BlockingQueue<Object> notifQueue = new ArrayBlockingQueue<Object>(10);

        // TODO - stupid hack; but gets stuck otherwise; no time to check in details ...
        final Thread iNotif = new Thread() {

            public void run() {
                setName("INotifier for: " + myName);
                while (controlSocket != null && !controlSocket.isClosed()) {
                    try {
                        final Object toNotif = notifQueue.poll(1, TimeUnit.SECONDS);
                        if (toNotif == null) {
                            continue;
                        }
                        if (logger.isLoggable(Level.FINEST)) {
                            logger.log(Level.FINEST, "[ ControlChannel ] [ INotifier ] notifying msg: " + toNotif);
                        }
                        notifier.notifyCtrlMsg(ControlChannel.this, toNotif);
                    } catch (Throwable t) {
                        if (logger.isLoggable(Level.FINER)) {
                            StringBuilder sb = new StringBuilder();
                            sb.append("[ ControlChannel ] [ INotifier ] Got exception. ControlChannel isClosed(): ").append(isClosed());
                            if (isClosed()) {
                                sb.append(" downMessage: ").append(downMessage()).append(" downCause: ").append(Utils.getStackTrace(downCause()));
                            }
                            sb.append(" Inotifier Exception: ");
                            logger.log(Level.FINER, sb.toString(), t);
                        }
                        close("INotifier got exception ", t);
                        cleanup();
                    }
                }
            }
        };
        iNotif.setDaemon(true);
        iNotif.start();

        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, myName + " STARTED main loop");
        }

        String internalDownMsg = null;
        Throwable internalDownCause = null;

        try {

            while (controlSocket != null && !controlSocket.isClosed()) {
                try {
                    sendAllMsgs();
                    Object o = ois.readObject();
                    if (o == null) {
                        continue;
                    }
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE, " [ ControlChannel ] received msg: " + o);
                    }

                    if (o instanceof CtrlMsg) {
                        final CtrlMsg ctrlMsg = (CtrlMsg) o;
                        if (ctrlMsg.tag == CtrlMsg.KEEP_ALIVE_MSG) {
                            // ping - like for the socket
                            continue;
                        }

                        if (ctrlMsg.tag == CtrlMsg.END_SESSION_FIN2) {
                            if (!isClosed()) {
                                final String errMsg = "Remote site will close the transfer session; FINAL timeout was reached. " 
                                    + "Most likely the TCP buffers on remote site are higher than normal. Try the blocking I/O -bio on both sides and no -ss.";
                                logger.log(Level.WARNING, errMsg);
                                close(errMsg, null);
                            }
                            break;
                        }
                    }

                    notifQueue.add(o);
                } catch (SocketTimeoutException ste) {
                    // ignore this??? or shall I close it() ?
                } catch (IOException ioe) {
                    close("Control channel got I/O Exception", ioe);
                    cleanup();
                } catch (Throwable t) {
                    t.printStackTrace();
                    close("Control channel got general exception. Will close!", t);
                    cleanup();
                }
            }// main loop

        } catch (Throwable t) {
            if (!isClosed()) {

                internalDownMsg = myName + " got exception in main loop: " + t.getMessage();
                internalDownCause = t;

                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, "Control Thread for " + myName + " got exception in main loop", t);
                }
            }
        } finally {
            if (downMessage() != null || downCause() != null) {
                close(downMessage(), downCause());
            } else {
                close(internalDownMsg, internalDownCause);
            }
        }

        logger.log(Level.INFO, myName + " FINISHED");
    }

    protected void internalClose() {

        try {
            final Thread t = new Thread() {

                public void run() {
                    setName("(ML) ControlChannel Graceful stopper thread");

                    try {
                        int retry = 0;

                        while (retry++ < 3) {
                            try {
                                Thread.sleep(1 * 1000);
                            } catch (Throwable _) {
                            }

                            try {
                                if (controlSocket == null || controlSocket.isClosed()) {
                                    break;
                                }
                                qToSend.add(new CtrlMsg(CtrlMsg.END_SESSION_FIN2, downMessage() + Utils.getStackTrace(downCause())));
                            } catch (Throwable _) {
                            }

                        }// end while

                    } finally {
                        cleanup();
                    }
                }
            };

            // if main is out ... please let me die like a real thread :)
            t.setDaemon(true);
            t.start();
        } catch (Throwable _) {
            // smth went dreadfully wrong ... just close the session now!
            try {
                cleanup();
            } catch (Throwable _2) {
            }
        }
    }
}
