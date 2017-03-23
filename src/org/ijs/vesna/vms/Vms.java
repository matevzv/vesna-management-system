/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.ijs.vesna.vms;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.cli.*;
import org.apache.log4j.xml.DOMConfigurator;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.ijs.vesna.communicator.Communicator;

/**
 *
 * @author Matevz
 */
public class Vms {

    private static final org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(Vms.class);
    private static final int MIN_PORT = 1;
    private static final int MAX_PORT = 65535;
    private static boolean localhost = true;
    private static int jettyPort = 9000;
    private static String portName = "";
    private static String tcpCsv = "";
    private static String sslCsv = "";
    private static String sslTimeoutCsv = "";
    private static ConcurrentHashMap<String, Communicator> communicators;

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        String log4jFileName = "log4jConfig.xml";

        String log4jFilePath = System.getProperty("user.dir")
                + System.getProperty("file.separator")
                + log4jFileName;
        DOMConfigurator.configure(log4jFilePath);

        communicators = new ConcurrentHashMap<String, Communicator>();

        GnuParser cmdLineParser = new GnuParser();
        Options options = new Options();
        options.addOption("h", false, "Print help");
        options.addOption(OptionBuilder.withDescription("Listen only on localhost. Options: true, false. Default: true.").withType(String.class).hasArg().withArgName("boolean").create("l"));
        options.addOption(OptionBuilder.withDescription("Port which jetty server listens to. Integer between 0 and 65535. Default: 9000").withType(Number.class).hasArg().withArgName("int").create("j"));
        options.addOption(OptionBuilder.withDescription("Serial port name. Default: empty").withType(String.class).hasArg().withArgName("str").create("p"));
        options.addOption(OptionBuilder.withDescription("TCP port numbers. Integers between 0 and 65535. CSV format. Example: 10001,10002").withType(String.class).hasArg().withArgName("csv").create("c"));
        options.addOption(OptionBuilder.withDescription("SSL port numbers. Integers between 0 and 65535. CSV format. Example: 10001,10002").withType(String.class).hasArg().withArgName("csv").create("s"));
        options.addOption(OptionBuilder.withDescription("SSL port numbers with send timeout enabled. Integers between 0 and 65535. CSV format. Example: 10001,10002").withType(String.class).hasArg().withArgName("csv").create("t"));

        try {
            CommandLine cmdLine = cmdLineParser.parse(options, args);

            if (cmdLine.hasOption('h')) {
                HelpFormatter f = new HelpFormatter();
                f.printHelp("LOG-a-TEC Infrastructure Instructions", options);
                return;
            }
            if (cmdLine.hasOption("l")) {
                localhost = Boolean.valueOf((cmdLine.getParsedOptionValue("l")).toString());
            }
            if (cmdLine.hasOption("j")) {
                jettyPort = ((Number) cmdLine.getParsedOptionValue("j")).intValue();
                if (jettyPort >= MIN_PORT && jettyPort <= MAX_PORT) {
                    //System.out.println("Port: " + sslCsv + " selected");
                } else {
                    System.out.println("Please select proper port (integer between 0 and 65535).");
                    return;
                }
            }
            if (cmdLine.hasOption("p")) {
                portName = (cmdLine.getParsedOptionValue("p")).toString();
                Communicator SerialComm = new Communicator();
                SerialComm.getPorts();
                SerialComm.setPortName(portName);
                SerialComm.openSerialConnection();
                communicators.put(portName, SerialComm);
            }
            if (cmdLine.hasOption("c")) {
                tcpCsv = (cmdLine.getParsedOptionValue("c")).toString();
                String[] tcpPorts = tcpCsv.split(",");
                for (String tcpPort : tcpPorts) {
                    int port;
                    try {
                        port = new Integer(tcpPort);
                        if (port >= MIN_PORT && port <= MAX_PORT) {
                            // start ssl server and add the object in the sslServerHashMap
                            Communicator tcpComm = new Communicator();
                            tcpComm.tcpConnect(port);
                            communicators.put(tcpPort, tcpComm);
                        } else {
                            System.out.println("Please select proper TCP port (integer between 0 and 65535)");
                            return;
                        }
                    } catch (NumberFormatException ex) {
                        System.out.println("Error: " + tcpPort + " unknown number format!");
                        return;
                    }
                }
            }
            if (cmdLine.hasOption("s")) {
                sslCsv = (cmdLine.getParsedOptionValue("s")).toString();
                String[] sslPorts = sslCsv.split(",");
                for (String sslPort : sslPorts) {
                    int port;
                    try {
                        port = new Integer(sslPort);
                        if (port >= MIN_PORT && port <= MAX_PORT) {
                            // start ssl server and add the object in the sslServerHashMap
                            Communicator sslComm = new Communicator();
                            sslComm.sslConnect(port);
                            communicators.put(sslPort, sslComm);
                        } else {
                            System.out.println("Please select proper SSL port (integer between 0 and 65535)");
                            return;
                        }
                    } catch (NumberFormatException ex) {
                        System.out.println("Error: " + sslPort + " unknown number format!");
                        return;
                    }
                }
            }
            if (cmdLine.hasOption("t")) {
                sslTimeoutCsv = (cmdLine.getParsedOptionValue("t")).toString();
                String[] sslPorts = sslCsv.split(",");
                String[] sslTimeoutedPorts = sslTimeoutCsv.split(",");

                List<String> sslPortList = Arrays.asList(sslPorts);

                for (String sslTimeoutedPort : sslTimeoutedPorts) {
                    int port;
                    try {
                        port = new Integer(sslTimeoutedPort);
                        if (port >= MIN_PORT && port <= MAX_PORT) {
                            // start ssl server and add the object in the sslServerHashMap

                            if (sslPortList.contains(sslTimeoutedPort)) {
                                System.out.println("This feature has been deprecated!");
                            } else {
                                System.out.println("Please select proper SSL port which requires a timeout!");
                                return;
                            }
                        } else {
                            System.out.println("Please select proper SSL port (integer between 0 and 65535)");
                            return;
                        }
                    } catch (NumberFormatException ex) {
                        System.out.println("Error: " + sslTimeoutedPort + " unknown number format!");
                        return;
                    }
                }
            }
        } catch (ParseException e) {
            logger.error(e);
        }

        try {
            Server server = new Server();

            Connector connector = new SelectChannelConnector();
            if (localhost) {
                connector.setHost("localhost");
            } else {
                logger.debug("Localhost option disabled. Jetty will listen on all IP addresses.");
            }
            connector.setPort(jettyPort);
            server.addConnector(connector);

            ResourceHandler resource_handler = new ResourceHandler();
            resource_handler.setDirectoriesListed(true);
            resource_handler.setWelcomeFiles(new String[]{"vms.html"});

            resource_handler.setResourceBase("./webapp/vms/");

            HandlerList handlers = new HandlerList();
            handlers.setHandlers(new Handler[]{resource_handler, new RequestHandler(communicators)});
            server.setHandler(handlers);

            server.start();
            server.join();
        } catch (Exception ex) {
            logger.error(ex);
        }
    }
}
