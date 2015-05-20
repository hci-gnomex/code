package lia.util.net.copy;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Date;
import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.gsi.FDTGSIServer;
import lia.util.net.common.AbstractFDTCloseable;
import lia.util.net.common.Config;
import lia.util.net.common.NetMatcher;
import lia.util.net.common.Utils;
import lia.util.net.copy.transport.ControlChannel;
import lia.util.net.copy.transport.gui.ServerSessionManager;

/**
 * The accept() task will run in it's own thread ( including here the server )
 * 
 * @author ramiro
 */
public class FDTServer extends AbstractFDTCloseable {

    private static final Logger logger = Logger.getLogger(FDTServer.class.getName());

    private static final Config config = Config.getInstance();

    private static final FDTSessionManager fdtSessionManager = FDTSessionManager.getInstance();

    final ServerSocketChannel ssc;

    final ServerSocket ss;

    final Selector sel;

    final int port;

    //used by the AcceptableTask-s
    final ExecutorService executor;

    //signals the server stop
    final AtomicBoolean hasToRun;

    static final class FDTServerMonitorTask implements Runnable {

        public void run() {
            // TODO Later
        }
    }

    private static final class AcceptableTask implements Runnable {

        final SocketChannel sc;

        final Socket s;

        AcceptableTask(final SocketChannel sc) {

            if (sc == null) {
                throw new NullPointerException("SocketChannel cannot be null in AcceptableTask");
            }

            if (sc.socket() == null) {
                throw new NullPointerException("Null Socket for SocketChannel in AcceptableTask");
            }

            this.sc = sc;
            this.s = sc.socket();
        }

        public void run() {

            if (!FDTServer.filterSourceAddress(s))
                return;

            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, " AcceptableTask for " + sc + " STARTED!");
            }
            final String sdpConfFlag = System.getProperty("com.sun.sdp.conf");
            final boolean bSDP = (sdpConfFlag != null && !sdpConfFlag.isEmpty());
            if (!bSDP) {
                try {
                    s.setKeepAlive(true);
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "[ FDTServer ] [ AcceptableTask ] Cannot set KEEP_ALIVE for " + sc
                            + ". Will ignore the error. Contact your sys admin.", t);
                }

                try {
                    // IPTOS_LOWCOST (0x02) IPTOS_RELIABILITY (0x04) IPTOS_THROUGHPUT (0x08) IPTOS_LOWDELAY (0x10)
                    s.setTrafficClass(0x04 | 0x08 | 0x010);
                } catch (Throwable t) {
                    logger.log(Level.WARNING,
                               "[ FDTServer ] [ AcceptableTask ] Cannot set traffic class for "
                                       + sc
                                       + "[ IPTOS_RELIABILITY (0x04) | IPTOS_THROUGHPUT (0x08) | IPTOS_LOWDELAY (0x10) ] Will ignore the error. Contact your sys admin.",
                               t);
                }
            }

            final ByteBuffer firstByte = ByteBuffer.allocate(1);
            final ByteBuffer clientIDBuff = ByteBuffer.allocate(16);

            UUID clientSessionID;
            Selector tmpSelector = null;
            SelectionKey sk = null;

            try {

                int count = -1;
                while (firstByte.hasRemaining()) {
                    count = sc.read(firstByte);
                    if (count < 0) {
                        logger.log(Level.WARNING, "[ FDTServer ] [ AcceptableTask ] Unable to read header for socket [ " + s
                                + " ] The stream will be closed.");
                        try {
                            sc.close();
                        } catch (Throwable _) {
                            // ignore
                        }
                        return;
                    }

                    if (firstByte.hasRemaining()) {
                        tmpSelector = Selector.open();
                        sk = sc.register(tmpSelector, SelectionKey.OP_READ);
                        tmpSelector.select();
                    }
                }

                if (sk != null) {
                    sk.cancel();
                    sk = null;
                }

                firstByte.flip();
                final byte firstB = firstByte.get();

                switch (firstB) {

                // Control channel
                    case 0: {
                        if (config.isGSIModeEnabled() || config.isGSISSHModeEnabled()) {
                            logger.log(Level.WARNING, "[ FDTServer ] [ AcceptableTask ] Got a remote control channel [ " + s
                                    + " ] but in GSI mode ... will be rejected.");
                            try {
                                sc.close();
                            } catch (Throwable _) {
                                // ignore
                            }
                            return;
                        }

                        sc.configureBlocking(true);
                        ControlChannel ct = null;

                        try {
                            ct = new ControlChannel(s, fdtSessionManager);
                            fdtSessionManager.addFDTClientSession(ct);
                        } catch (Throwable t) {
                            logger.log(Level.WARNING, "[ FDTServer ] [ AcceptableTask ] Cannot instantiate ControlChannel", t);
                            ct = null;
                        }

                        if (ct != null) {
                            new Thread(ct, "ControlChannel thread for [ " + s.getInetAddress() + ":" + s.getPort() + " ]").start();
                        }
                        break;
                    }

                    // Worker channel
                    case 1: {
                        if (config.isBlocking()) {
                            sc.configureBlocking(true);
                        } else {
                            sc.configureBlocking(false);
                        }

                        // TODO - Use SelectionManager(?),
                        while (clientIDBuff.hasRemaining()) {
                            count = sc.read(clientIDBuff);
                            if (count < 0) {
                                logger.log(Level.WARNING, "[ FDTServer ] [ AcceptableTask ] Unable to read clientID. The stream will be closed");
                                Utils.closeIgnoringExceptions(sc);
                                return;
                            }

                            if (clientIDBuff.hasRemaining()) {
                                // THIS CANNOT?!? happen in blocking mode ( JVM should throw exception if this happens
                                // ... but )
                                if (config.isBlocking()) {
                                    logger.log(Level.WARNING,
                                               "[ FDTServer ] [ AcceptableTask ] Blocking mode ... unable to read clientID. The stream will be closed");
                                    Utils.closeIgnoringExceptions(sc);
                                    return;
                                }
                            } else {
                                // everything has been read
                                break;
                            }

                            if (tmpSelector == null) {
                                tmpSelector = Selector.open();
                            }

                            if (!config.isBlocking()) {
                                sk = sc.register(tmpSelector, SelectionKey.OP_READ);
                                tmpSelector.select();
                            }
                        }// while

                        if (sk != null) {
                            sk.cancel();
                        }

                        clientIDBuff.flip();
                        clientSessionID = new UUID(clientIDBuff.getLong(), clientIDBuff.getLong());
                        if (logger.isLoggable(Level.FINE)) {
                            logger.log(Level.FINE, "[ FDTServer ] [ AcceptableTask ] New socket from clientID: " + clientSessionID);
                        }

                        fdtSessionManager.addWorker(clientSessionID, sc);
                        break;
                    }

                    // Ping channel - RTT
                    case 2: {
                        break;
                    }

                    // GUI special (sounds special, doesn't it) channel
                    case 3: {
                        sc.configureBlocking(true);
                        ServerSessionManager sm = null;
                        try {
                            sm = new ServerSessionManager(s);
                            new Thread(sm, "GUIControlChannel thread for [ " + s.getInetAddress() + ":" + s.getPort() + " ]").start();
                        } catch (Throwable t) {
                            logger.log(Level.WARNING, "[ FDTServer ] [ AcceptableTask ] Cannot instantiate GUI ControlChannel", t);
                        }
                        break;
                    }

                    default: {
                        logger.log(Level.WARNING, "[ FDTServer ] [ AcceptableTask ] Unable to understand initial cookie: " + firstB);
                        Utils.closeIgnoringExceptions(s);
                        return;
                    }
                }

            } catch (Throwable t) {
                logger.log(Level.WARNING, "[ FDTServer ] [ AcceptableTask ] Exception: ", t);
                Utils.closeIgnoringExceptions(sc);
            } finally {
                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, " AcceptableTask for " + s + " FINISHED!");
                }
                Utils.closeIgnoringExceptions(tmpSelector);
            }
        }
    }

    public FDTServer() throws Exception {
        hasToRun = new AtomicBoolean(true);

        // We are not very happy to welcome new clients ... so the priority will be lower
        executor = Utils.getStandardExecService("[ Acceptable ServersThreadPool ] ",
                                                5,
                                                10,
                                                new ArrayBlockingQueue<Runnable>(65500),
                                                Thread.NORM_PRIORITY - 2);
        port = config.getPort();
        ssc = ServerSocketChannel.open();
        ssc.configureBlocking(false);

        ss = ssc.socket();
        ss.bind(new InetSocketAddress(port));

        sel = Selector.open();
        ssc.register(sel, SelectionKey.OP_ACCEPT);

        if (config.isGSIModeEnabled()) {
            FDTGSIServer gsiServer = new FDTGSIServer(config.getGSIPort());
            gsiServer.start();
        }
        // Monitoring & Nice Prnting
        final ScheduledExecutorService monitoringService = Utils.getMonitoringExecService();

        monitoringService.scheduleWithFixedDelay(new FDTServerMonitorTask(), 10, 10, TimeUnit.SECONDS);

        // in SSH mode this is a ACK message for the client to inform it that the server started ok
        // (the server stdout is piped to client through the SSH channel)
        System.out.println("READY");
    }

    /**
     * Safe to call multiple times; will return false if the server was already signaled to stop
     * </br>
     * <p>
     * <b>Note:</b> Invoking this method acts as a signal for the server. Any ongoing transfers will continue until they finish
     * </p>
     * @return true if server was signaled to stop
     */
    public boolean stopServer() {
        return hasToRun.compareAndSet(true, false);
    }

    public static final boolean filterSourceAddress(java.net.Socket socket) {
        /**
         * check if remote client is allowed (based on optional filter specified in command line) if the address does
         * not match, reject this IP (i.e DO NOT accept the session-tag
         * from this client)
         */
        final NetMatcher filter = config.getSourceAddressFilter();
        if (filter != null) {
            logger.info("Enforcing source address filter: " + filter);
            final String sourceIPAddress = socket.getInetAddress().getHostAddress();
            if (!filter.matchInetNetwork(sourceIPAddress)) {
                Utils.closeIgnoringExceptions(socket);
                logger.warning(" Client [" + sourceIPAddress + "] is not allowed to transfer. Socket closed!");
                return false;
            }
        }
        return true;

    }

    public void doWork() throws Exception {

        Thread.currentThread().setName(" FDTServer - Main loop worker ");
        logger.info("FDTServer start listening on port: " + ss.getLocalPort());

        final boolean isStandAlone = config.isStandAlone();
        try {
            for (;;) {
                if (!isStandAlone) {
                    if (fdtSessionManager.isInited() && fdtSessionManager.sessionsNumber() == 0) {
                        logger.log(Level.INFO, "FDTServer will finish. No more sessions to serve.");
                        return;
                    }
                } else {
                    if (!hasToRun.get()) {
                        // stopServer was called
                        if (fdtSessionManager.isInited() && fdtSessionManager.sessionsNumber() == 0) {
                            logger.log(Level.INFO, "FDTServer will finish. No more sessions to serve.");
                            return;
                        }
                    }
                }
                final int count = sel.select(2000);

                if (count == 0)
                    continue;

                Iterator<SelectionKey> it = sel.selectedKeys().iterator();
                while (it.hasNext()) {
                    final SelectionKey sk = it.next();
                    it.remove();

                    if (!sk.isValid())
                        continue;// closed socket ?

                    if (sk.isAcceptable()) {
                        final ServerSocketChannel ssc = (ServerSocketChannel) sk.channel();
                        final SocketChannel sc = ssc.accept();

                        try {
                            executor.execute(new AcceptableTask(sc));
                        } catch (Throwable t) {
                            StringBuilder sb = new StringBuilder();
                            sb.append("[ FDTServer ] got exception in while sumbiting the AcceptableTask for SocketChannel: ").append(sc);
                            if (sc != null) {
                                sb.append(" Socket: ").append(sc.socket());
                            }
                            sb.append(" Cause: ");
                            logger.log(Level.WARNING, sb.toString(), t);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, "[FDTServer] Exception in main loop!", t);
            throw new Exception(t);
        } finally {
            logger.log(Level.INFO, "[FDTServer] main loop FINISHED!");
            // close all the stuff
            Utils.closeIgnoringExceptions(ssc);
            Utils.closeIgnoringExceptions(sel);
            Utils.closeIgnoringExceptions(ss);
            if (executor != null) {
                executor.shutdown();
            }
        }
    }

    public static final void main(String[] args) throws Exception {
        FDTServer jncs = new FDTServer();
        jncs.doWork();
    }

    public void run() {

        try {
            doWork();
        } catch (Throwable t) {
            logger.log(Level.WARNING, "[ FDTServer ] exception main loop", t);
            close("[ FDTServer ] exception main loop", t);
        }

        close(null, null);

        logger.info(" \n\n FDTServer finishes @ " + new Date().toString() + "!\n\n");
    }

    @Override
    protected void internalClose() {
        // TODO Auto-generated method stub

    }

}
