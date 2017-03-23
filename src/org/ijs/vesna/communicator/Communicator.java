 /*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.ijs.vesna.communicator;

import gnu.io.CommPort;
import gnu.io.CommPortIdentifier;
import gnu.io.SerialPort;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Enumeration;
import java.util.concurrent.Semaphore;
import java.util.zip.CRC32;
import java.util.zip.Checksum;
import javax.net.ServerSocketFactory;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import org.apache.log4j.Logger;

/**
 *
 * @author Matevz
 */
public class Communicator {
    //constants

    private static final String nl = "\n";
    private static final String CR_LF = "\r\n";
    private static final String GET = "GET ";
    private static final String POST = "POST ";
    private static final String LEN = "Length=";
    private static final String CRC = "CRC=";
    private static final String RESPONSE_END = "\r\nOK\r\n";
    private static final String JUNK_INPUT = "\r\nJUNK-INPUT\r\n";
    private static final String CORRUPTED_DATA = "\r\nCORRUPTED-DATA\r\n";
    private static final String ERROR = "\r\nERROR\r\n";
    private static final String NODES = "NODES";
    private static final String NODES_JUNK = "junk";
    private static final int SEMAPHORE = 1;
    private static final int MAX_PORTS = 20;
    private static final int TIMEOUT = 300000; // transmission timeout in ms
    private static final Logger logger = Logger.getLogger(Communicator.class);
    private static final int MEGABYTE = 1024 * 1024;
    private InputStream inputStream;
    private OutputStream outputStream;
    private String receivedBufferStr = "";
    private ByteBuffer receivedBuffer = ByteBuffer.allocate(MEGABYTE);
    private final Semaphore semaphore = new Semaphore(SEMAPHORE, true);
    private String[] tempPortList, portList; //list of ports for combobox dropdown
    private CommPort commPort;
    private SerialPort serialPort;
    private String portName = "";
    private CommPortIdentifier portIdentifier = null;
    private boolean open = false;
    private boolean sslServerRunning = false;
    private boolean sendTimeoutSsl = false;
    private SslServer sslServer = null;
    private TcpServer tcpServer = null;
    private String commonPortName;
    private final Object lock = new Object();

    public boolean isSslServerRunning() {
        return sslServerRunning;
    }

    public String getPortName() {
        return portName;
    }

    public boolean isOpen() {
        return open;
    }
    private int baudRate = 115200;

    public int getBaudRate() {
        return baudRate;
    }

    public String resetSocket() {
        String resp = "The socket was not reseted.";

        if (sslServer != null) {
            if (sslServer.closeSslSocket()) {
                resp = "The SSL socket reset successfully.";
            } else {
                resp = "The SSL socket was not reset.";
            }
        } else if (tcpServer != null) {
            if (tcpServer.closeTcpSocket()) {
                resp = "The TCP socket reset successfully.";
            } else {
                resp = "The TCP socket was not reset.";
            }
        }
        return resp;
    }

    private byte[] removeOkFromReceivedBuffer() {
        receivedBuffer.flip();
        byte[] responseBuff;
        if (receivedBuffer.limit() >= 6) {
            responseBuff = new byte[receivedBuffer.limit() - 6];
            receivedBuffer.get(responseBuff, 0, receivedBuffer.limit() - 6);
            receivedBuffer.clear();
            receivedBuffer.put(responseBuff);
            return responseBuff;
        } else {
            responseBuff = new byte[receivedBuffer.limit()];
            receivedBuffer.get(responseBuff, 0, receivedBuffer.limit());
            receivedBuffer.clear();
            receivedBuffer.put(responseBuff);
            return responseBuff;
        }
    }

    public boolean isSendTimeout() {
        return sendTimeoutSsl;
    }

    public void setSendTimeoutSsl(boolean sendTimeoutSsl) {
        this.sendTimeoutSsl = sendTimeoutSsl;
        logger.debug("The SSL server listening on port " + commonPortName + " will send data with timeout!");
    }

    public byte[] sendGet(byte[] params) {
        if (semaphore.tryAcquire()) {
            boolean timeout = false;
            byte[] response;
            try {
                receivedBufferStr = ""; //TODO make it local
                receivedBuffer.clear();

                outputStream.write(GET.getBytes());
                outputStream.write(params);
                outputStream.write(CR_LF.getBytes());

                long start = System.currentTimeMillis();
                while (!receivedBufferStr.contains(RESPONSE_END) && open) {
                    long stop = System.currentTimeMillis();
                    if ((stop - start) > TIMEOUT) {
                        timeout = true;
                        break;
                    }
                    synchronized (lock) {
                        lock.wait(1000);
                    }
                }

                if (receivedBufferStr.contains(JUNK_INPUT)
                        || receivedBufferStr.contains(CORRUPTED_DATA)) {
                    receivedBufferStr = receivedBufferStr.replace(RESPONSE_END, "");
                    removeOkFromReceivedBuffer();
                    sendRecoveryRequest();
                } else if (receivedBufferStr.contains(ERROR)) {
                    if (receivedBufferStr.contains(NODES) && receivedBufferStr.contains(NODES_JUNK)) {
                        String nodeRequest = new String(params);
                        String xbitAddr = nodeRequest.substring(nodeRequest.indexOf('?') + 1, nodeRequest.indexOf('/'));
                        receivedBufferStr = receivedBufferStr.replace(RESPONSE_END, "");
                        removeOkFromReceivedBuffer();
                        sendNodeRecoveryRequest(xbitAddr);
                    }
                }
            } catch (Exception ex) {
                logger.error(ex);
            } finally {
                receivedBufferStr = receivedBufferStr.replace(RESPONSE_END, "");
                response = removeOkFromReceivedBuffer();
                semaphore.release();
            }
            return response;
        } else {
            return "ERROR: Communication in progress".getBytes();
        }
    }

    public byte[] sendPost(byte[] params, byte[] content) {
        if (semaphore.tryAcquire()) {
            boolean timeout = false;
            byte[] response;
            try {
                String len = LEN + content.length + CR_LF;

                Checksum checksum = new CRC32();
                checksum.update(POST.getBytes(), 0, POST.getBytes().length);
                checksum.update(params, 0, params.length);
                checksum.update(CR_LF.getBytes(), 0, CR_LF.getBytes().length);
                checksum.update(len.getBytes(), 0, len.getBytes().length);
                checksum.update(content, 0, content.length);
                checksum.update(CR_LF.getBytes(), 0, CR_LF.getBytes().length);

                String reqEnd = CRC + checksum.getValue() + CR_LF;

                receivedBufferStr = "";
                receivedBuffer.clear();

                outputStream.write(POST.getBytes());
                outputStream.write(params);
                outputStream.write(CR_LF.getBytes());
                outputStream.write(len.getBytes());
                outputStream.write(content);
                outputStream.write(CR_LF.getBytes());
                outputStream.write(reqEnd.getBytes());

                long start = System.currentTimeMillis();
                while (!receivedBufferStr.contains(RESPONSE_END) && open) {
                    long stop = System.currentTimeMillis();
                    if ((stop - start) > TIMEOUT) {
                        timeout = true;
                        break;
                    }
                    synchronized (lock) {
                        lock.wait(1000);
                    }
                }
                if (receivedBufferStr.contains(JUNK_INPUT)
                        || receivedBufferStr.contains(CORRUPTED_DATA)) {
                    receivedBufferStr = receivedBufferStr.replace(RESPONSE_END, "");
                    removeOkFromReceivedBuffer();
                    sendRecoveryRequest();
                } else if (receivedBufferStr.contains(ERROR)) {
                    if (receivedBufferStr.contains(NODES) && receivedBufferStr.contains(NODES_JUNK)) {
                        String nodeRequest = new String(params);
                        String xbitAddr = nodeRequest.substring(nodeRequest.indexOf('?') + 1, nodeRequest.indexOf('/'));
                        receivedBufferStr = receivedBufferStr.replace(RESPONSE_END, "");
                        removeOkFromReceivedBuffer();
                        sendNodeRecoveryRequest(xbitAddr);
                    }
                }
            } catch (Exception ex) {
                logger.error(ex);
            } finally {
                receivedBufferStr = receivedBufferStr.replace(RESPONSE_END, "");
                response = removeOkFromReceivedBuffer();
                semaphore.release();
            }
            return response;
        } else {
            return "ERROR: Communication in progress".getBytes();
        }
    }

    private void sendRecoveryRequest() {
        try {
            String req = CR_LF;

            for (int i = 0; i < 5; i++) {
                outputStream.write(req.getBytes());
            }

            while (!receivedBufferStr.contains(RESPONSE_END) && open) {
                synchronized (lock) {
                    lock.wait(1000);
                }
            }
        } catch (Exception ex) {
            logger.error(ex);
        }
    }

    private void sendNodeRecoveryRequest(String xbitAddr) {
        try {
            String recoveryResource = "radio/noderesetparser?" + xbitAddr;
            byte[] params = recoveryResource.getBytes();
            byte[] content = "1".getBytes();
            String len = LEN + content.length + CR_LF;

            Checksum checksum = new CRC32();
            checksum.update(POST.getBytes(), 0, POST.getBytes().length);
            checksum.update(params, 0, params.length);
            checksum.update(CR_LF.getBytes(), 0, CR_LF.getBytes().length);
            checksum.update(len.getBytes(), 0, len.getBytes().length);
            checksum.update(content, 0, content.length);
            checksum.update(CR_LF.getBytes(), 0, CR_LF.getBytes().length);

            String reqEnd = CRC + checksum.getValue() + CR_LF;

            outputStream.write(POST.getBytes());
            outputStream.write(params);
            outputStream.write(CR_LF.getBytes());
            outputStream.write(len.getBytes());
            outputStream.write(content);
            outputStream.write(CR_LF.getBytes());
            outputStream.write(reqEnd.getBytes());

            while (!receivedBufferStr.contains(RESPONSE_END) && open) {
                synchronized (lock) {
                    lock.wait(1000);
                }
            }
        } catch (Exception ex) {
            logger.error(ex);
        }
    }

    public String sslConnect(int port) {
        try {
            sslServer = new SslServer(port);
            commonPortName = new Integer(port).toString();
            (new Thread(sslServer)).start();
            return "\nSSL server listening on port " + port + nl;
        } catch (Exception ex) {
            return "\nSSL server setup failed" + nl;
        }
    }

    public String tcpConnect(int port) {
        try {
            tcpServer = new TcpServer(port);
            commonPortName = new Integer(port).toString();
            (new Thread(tcpServer)).start();
            return "\nT server listening on port " + port + nl;
        } catch (Exception ex) {
            return "\nSSL server setup failed" + nl;
        }
    }

    public String setBaudRate(String userBaudRate) {
        String returnMsg = "";
        try {
            int tmpBaud = Integer.valueOf(userBaudRate).intValue();
            baudRate = tmpBaud;
            if (open) {
                returnMsg = "Baud rate set to " + baudRate + ".\n"
                        + "Please close and reopen serial port.\n";
            } else {
                returnMsg = "Baud rate set to " + baudRate + ".\n";
            }
        } catch (Exception ex) {
            logger.error(ex);
        }
        return returnMsg;
    }

    //open serial port
    private String serialConnect(String portName) throws Exception {
        //make sure port is not currently in use
        String msg;
        portIdentifier = CommPortIdentifier.getPortIdentifier(portName);
        if (portIdentifier.isCurrentlyOwned()) {
            msg = "Error: Port is currently in use\n";
        } else {
            //create CommPort and identify available serial/parallel ports
            commPort = portIdentifier.open(this.getClass().getName(), 2000);
            serialPort = (SerialPort) commPort;//cast all to serial
            //set baudrate, 8N1 stopbits, no parity
            serialPort.setSerialPortParams(baudRate, SerialPort.DATABITS_8,
                    SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
            //start I/O streams
            inputStream = serialPort.getInputStream();
            outputStream = serialPort.getOutputStream();
            msg = "Serial port: " + portName + " is now opened.\n";
            open = true;
        }
        return msg;
    }

    public String[] getPorts() {
        Enumeration portEnum = CommPortIdentifier.getPortIdentifiers();
        tempPortList = new String[MAX_PORTS]; //create array of 20 ports
        int numports = 0;
        int i;
        tempPortList[0] = "Select Port";
        //fill up a temporary list of length MAX_PORTS with the portnames
        while (portEnum.hasMoreElements()) {
            portIdentifier = (CommPortIdentifier) portEnum.nextElement();
            numports++;
            tempPortList[numports] = portIdentifier.getName();
        }
        //make the actual port list only as long as necessary
        portList = new String[numports];
        for (i = 0; i < numports; i++) {
            portList[i] = tempPortList[i];
        }
        return portList;
    }

    public String setPortName(String portName) {
        String response;
        if (open) { //if port open, make user close port before changing port
            response = "Must Close Port Before Changing Port.\n";
        } else if (portName.equals("Select Port")) {
            response = "Must Select Valid Port.\n";
        } else {
            this.portName = portName;
            commonPortName = portName;
            response = "Port Selected: " + portName + ", Baud Rate: " + baudRate + ".\n";
        }
        return response;
    }

    public String openSerialConnection() {
        String response = "";
        if (portIdentifier.isCurrentlyOwned()) {
            //close input stream
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException ex) {
                    logger.error(ex);
                }
            }
            //close output stream
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException ex) {
                    logger.error(ex);
                }
            }
            //close serial port                   
            if (serialPort != null) {
                serialPort.close();
            }
            open = false;
        } else {//else port is closed, so open it
            try {
                if (!portName.equals("")) {
                    response = serialConnect(portName);
                    (new Thread(new SerialReader(inputStream))).start();
                } else {
                    response = "Must Select Valid Port.\n";
                }
            } catch (Exception e) {
                logger.error(e);
            }
        }
        return response;
    }

    public String closeSerialConnection() {
        //when user closes, make sure to close open ports and open I/O streams
        String response = "";
        if (portIdentifier.isCurrentlyOwned()) { //if port open, close port
            open = false;
            if (inputStream != null) //close input stream
            {
                try {
                    inputStream.close();
                } catch (IOException ex) {
                    logger.error(ex);
                }
            }
            if (outputStream != null) //close output stream
            {
                try {
                    outputStream.close();
                } catch (IOException ex) {
                    logger.error(ex);
                }
            }
            if (serialPort != null) {
                serialPort.close();
            }
            response = "Serial Port: " + portName + " is now closed.\n";
        }
        return response;
    }

    public String endSerialConnection() {
        //when user closes, make sure to close open ports and open I/O streams
        String response = "";
        if (portIdentifier != null && portIdentifier.isCurrentlyOwned()) { //if port open, close port
            open = false;
            if (inputStream != null) //close input stream
            {
                try {
                    inputStream.close();
                } catch (IOException ex) {
                    logger.error(ex);
                }
            }
            if (outputStream != null) //close output stream
            {
                try {
                    outputStream.close();
                } catch (IOException ex) {
                    logger.error(ex);
                }
            }
            if (serialPort != null) {
                serialPort.close();
            }
            response = "Serial Port: " + portName + " is now closed.\n";
            portName = "";
            baudRate = 115200;
        }
        return response;
    }

    public class SerialReader implements Runnable {

        InputStream in;

        public SerialReader(InputStream in) {
            this.in = in;
        }

        @Override
        public void run() {
            byte[] buffer = new byte[1024];
            int len;
            try {
                while (open) {
                    len = this.in.read(buffer);
                    receivedBufferStr += new String(buffer, 0, len);
                    receivedBuffer.put(buffer, 0, len);
                    synchronized (lock) {
                        lock.notify();
                    }
                }
                in.close();
            } catch (IOException e) {
                logger.error(e);
            }
        }
    }

    class ClientHandler extends Thread {

        Socket socket;
        int port;

        ClientHandler(Socket socket, int port) {
            this.socket = socket;
            this.port = port;
        }

        public void closeSocket() {
            try {
                open = false;
                if (inputStream != null) {
                    inputStream.close();
                }
                if (outputStream != null) {
                    outputStream.close();
                }
                socket.close();
            } catch (IOException ex) {
                logger.error("Closing socket failed for the client with IP "
                        + socket.getInetAddress().getHostAddress()
                        + " and port " + port + ": " + ex);
            }
        }

        @Override
        public void run() {
            try {
                open = true;
                logger.debug("Client with IP "
                        + socket.getInetAddress().getHostAddress()
                        + " successfully connected to the server on port " + port);
                try {
                    socket.setKeepAlive(true);
                    inputStream = socket.getInputStream();
                    outputStream = socket.getOutputStream();

                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = inputStream.read(buffer)) > 0) {
                        receivedBufferStr += new String(buffer, 0, len);
                        receivedBuffer.put(buffer, 0, len);
                        synchronized (lock) {
                            lock.notify();
                        }
                    }
                    logger.debug("Client with IP "
                            + socket.getInetAddress().getHostAddress()
                            + " disconnected from the server on port " + port);
                } catch (Exception e) {
                    logger.error("Client with IP "
                            + socket.getInetAddress().getHostAddress()
                            + " and port " + port + ": " + e);
                } finally {
                    if (inputStream != null) {
                        inputStream.close();
                    }
                    if (outputStream != null) {
                        outputStream.close();
                    }
                    socket.close();
                }
            } catch (Exception ex) {
                logger.error("Client with IP "
                        + socket.getInetAddress().getHostAddress()
                        + " and port " + port + ": " + ex);
            } finally {
                open = false;
                synchronized (lock) {
                    lock.notify();
                }
            }
        }
    }

    public class SslServer implements Runnable {

        private int port;
        SSLServerSocketFactory ssf = null;
        ServerSocket ss = null;
        ClientHandler clientHandler = null;

        public SslServer(int port) {
            this.port = port;
        }

        public boolean closeSslSocket() {
            boolean socketClosed;

            if (clientHandler != null) {
                clientHandler.closeSocket();
                socketClosed = true;
            } else {
                socketClosed = false;
            }
            return socketClosed;
        }

        @Override
        public void run() {
            try {
                System.setProperty("javax.net.ssl.keyStore", "mySrvKeystore");
                System.setProperty("javax.net.ssl.keyStorePassword", "123456");

                try {
                    ssf = (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
                    ss = ssf.createServerSocket(port);
                    logger.debug("The SSL Server is listening on port " + port);

                    for (;;) {
                        SSLSocket client = (SSLSocket) ss.accept();
                        if (clientHandler != null) {
                            clientHandler.closeSocket();
                        }
                        clientHandler = new ClientHandler(client, port);
                        clientHandler.start();
                    }
                } catch (Exception ex) {
                    logger.error("Client with IP "
                            + ss.getInetAddress().getHostAddress()
                            + " and port " + port + ": " + ex);
                } finally {
                    ss.close();
                }
            } catch (Exception ex) {
                logger.error("Client with IP "
                        + ss.getInetAddress().getHostAddress()
                        + " and port " + port + ": " + ex);
            }
        }
    }

    public class TcpServer implements Runnable {

        private int port;
        ServerSocketFactory ssf = null;
        ServerSocket ss = null;
        ClientHandler clientHandler = null;

        public TcpServer(int port) {
            this.port = port;
        }

        public boolean closeTcpSocket() {
            boolean socketClosed;

            if (clientHandler != null) {
                clientHandler.closeSocket();
                socketClosed = true;
            } else {
                socketClosed = false;
            }
            return socketClosed;
        }

        @Override
        public void run() {
            try {
                ssf = (ServerSocketFactory) ServerSocketFactory.getDefault();

                try {
                    ss = ssf.createServerSocket(port);
                    logger.debug("The TCP Server is listening on port " + port);

                    for (;;) {
                        Socket client = (Socket) ss.accept();
                        if (clientHandler != null) {
                            clientHandler.closeSocket();
                        }
                        clientHandler = new ClientHandler(client, port);
                        clientHandler.start();
                    }
                } catch (Exception ex) {
                    logger.error("Client with IP "
                            + ss.getInetAddress().getHostAddress()
                            + " and port " + port + ": " + ex);
                } finally {
                    ss.close();
                }
            } catch (Exception ex) {
                logger.error("Client with IP "
                        + ss.getInetAddress().getHostAddress()
                        + " and port " + port + ": " + ex);
            }
        }
    }
}
