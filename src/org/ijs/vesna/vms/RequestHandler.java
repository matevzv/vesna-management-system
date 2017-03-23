/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.ijs.vesna.vms;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.URL;
import java.net.URLDecoder;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.CRC32;
import java.util.zip.Checksum;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileItemFactory;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.http.HttpHeaders;
import org.eclipse.jetty.http.HttpMethods;
import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.util.ByteArrayISO8859Writer;
import org.eclipse.jetty.util.IO;
import org.eclipse.jetty.util.MultiMap;
import org.eclipse.jetty.util.UrlEncoded;
import org.eclipse.jetty.util.resource.Resource;
import org.ijs.vesna.communicator.Communicator;
import org.ijs.vesna.communicator.FileInPackets;

/**
 *
 * @author Matevz
 */
public class RequestHandler extends AbstractHandler {

    private static final org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(RequestHandler.class);
    final long _faviconModified = (System.currentTimeMillis() / 1000) * 1000L;
    byte[] _favicon;
    boolean _serveIcon = true;
    boolean _showContexts = true;
    boolean firmwareUpgradeRunning = false;
    private static final long MEGABYTE = 1024 * 1024;
    private File firmwareBinary;
    private Long crc;
    private ConcurrentHashMap<String, Communicator> communicators;

    public RequestHandler(ConcurrentHashMap<String, Communicator> communicators) {
        try {
            URL fav = this.getClass().getClassLoader().getResource("org/ijs/vesna/logo/sensorlab-logo.png");
            if (fav != null) {
                Resource r = Resource.newResource(fav);
                _favicon = IO.readBytes(r.getInputStream());
            }
        } catch (Exception e) {
            logger.error(e);
        }

        this.communicators = communicators;
    }

    public long calculateCrc(File file) throws IOException {
        FileInputStream fin = new FileInputStream(file);

        Checksum checksum = new CRC32();
        int b;
        while ((b = fin.read()) != -1) {
            checksum.update(b);
        }
        return checksum.getValue();
    }

    /*
     * ------------------------------------------------------------
     */
    /*
     * @see
     * org.eclipse.jetty.server.server.Handler#handle(javax.servlet.http.HttpServletRequest,
     * javax.servlet.http.HttpServletResponse, int)
     */
    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        if (response.isCommitted() || baseRequest.isHandled()) {
            return;
        }

        baseRequest.setHandled(true);

        String method = request.getMethod();

        // little cheat for common request
        if (_serveIcon && _favicon != null && method.equals(HttpMethods.GET) && request.getRequestURI().equals("/favicon.ico")) {
            if (request.getDateHeader(HttpHeaders.IF_MODIFIED_SINCE) == _faviconModified) {
                response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
            } else {
                response.setStatus(HttpServletResponse.SC_OK);
                response.setContentType("image/x-icon");
                response.setContentLength(_favicon.length);
                response.setDateHeader(HttpHeaders.LAST_MODIFIED, _faviconModified);
                response.setHeader(HttpHeaders.CACHE_CONTROL, "max-age=360000,public");
                response.getOutputStream().write(_favicon);
            }
            return;
        }

        if (method.equals(HttpMethods.GET) && request.getRequestURI().startsWith("/request-log")) {
            try {
                String query = baseRequest.getQueryString();
                query = URLDecoder.decode(query, "UTF-8");

                String fileName = null;

                if (query.endsWith("request-response-txt.log")) {
                    fileName = query;
                } else if (query.endsWith("request-response-hex.log")) {
                    fileName = query;
                }

                if (fileName != null) {
                    String logUrl = System.getProperty("user.dir") + System.getProperty("file.separator")
                            + "logs" + System.getProperty("file.separator") + fileName;

                    File f = new File(logUrl);
                    if (f.exists()) {
                        byte[] textLogFile = IOUtils.toByteArray(new FileInputStream(f));
                        response.setStatus(HttpServletResponse.SC_OK);
                        response.setContentType("text/plain");
                        response.setContentLength(textLogFile.length);
                        response.setHeader("Content-disposition", "attachment; filename=\"" + fileName + "\"");
                        response.getOutputStream().write(textLogFile);
                        return;
                    }
                }
            } catch (Exception e) {
                logger.error(e);
            }
        }

        if (method.equals(HttpMethods.POST) && request.getRequestURI().startsWith("/firmware-bin")) {
            response.setContentType("text/html;charset=utf-8");
            response.setStatus(HttpServletResponse.SC_OK);

            if (firmwareUpgradeRunning) {
                response.getWriter().print("ERROR: Firmware upgrade already in progress!");
                return;
            }
            firmwareUpgradeRunning = true;

            boolean isMultipart = ServletFileUpload.isMultipartContent(request);

            if (isMultipart) {
                FileItemFactory factory = new DiskFileItemFactory();
                ServletFileUpload upload = new ServletFileUpload(factory);

                try {
                    List items = upload.parseRequest(request);
                    Iterator iterator = items.iterator();
                    String resource = "";
                    while (iterator.hasNext()) {
                        FileItem item = (FileItem) iterator.next();
                        long size = item.getSize();
                        float sizeInMbytes = bytesMbytes(size);
                        if (sizeInMbytes <= 1) {

                            if (!item.isFormField()) {
                                String dir = System.getProperty("user.dir")
                                        + System.getProperty("file.separator")
                                        + "firmware";
                                String fileName = item.getName();

                                File path = new File(dir);
                                if (!path.exists()) {
                                    boolean status = path.mkdirs();
                                }

                                firmwareBinary = new File(path + System.getProperty("file.separator") + fileName);
                                item.write(firmwareBinary);
                                crc = calculateCrc(firmwareBinary);
                                response.getWriter().print("File " + item.getName()
                                        + " successfully uploaded!<br>size = "
                                        + item.getSize() + " B" + "<br>CRC = "
                                        + crc + "<br>");
                                return;
                            } else {
                                resource = new String(item.get());
                                if (resource.equals("") || resource == null) {
                                    response.getWriter().print("ERROR: No file selected!");
                                    return;
                                }
                            }
                        } else {
                            response.getWriter().print("File " + item.getName()
                                    + " has size " + sizeInMbytes + " MB!"
                                    + "<br>The maximum allowed file size is 1 MB");
                            return;
                        }
                    }
                } catch (FileUploadException e) {
                    logger.error(e);
                } catch (Exception e) {
                    logger.error(e);
                } finally {
                    firmwareUpgradeRunning = false;
                    return;
                }
            }
        }

        if (method.equals(HttpMethods.POST) && request.getRequestURI().startsWith("/experiment-file")) {
            response.setContentType("text/html;charset=utf-8");
            response.setStatus(HttpServletResponse.SC_OK);

            boolean isMultipart = ServletFileUpload.isMultipartContent(request);

            if (isMultipart) {
                FileItemFactory factory = new DiskFileItemFactory();
                ServletFileUpload upload = new ServletFileUpload(factory);

                try {
                    List items = upload.parseRequest(request);
                    FileItem experiment = (FileItem) items.get(0);
                    FileItem cluster = (FileItem) items.get(1);
                    String clusterName = cluster.getFieldName();

                    long size = experiment.getSize();
                    float sizeInMbytes = bytesMbytes(size);
                    if (sizeInMbytes <= 1) {

                        if (!experiment.isFormField()) {
                            String dir = System.getProperty("user.dir")
                                    + System.getProperty("file.separator")
                                    + "experiments";
                            String fileName = experiment.getName();

                            File path = new File(dir);
                            if (!path.exists()) {
                                boolean status = path.mkdirs();
                            }

                            File experimentFile = new File(path + System.getProperty("file.separator") + fileName);
                            experiment.write(experimentFile);
                            boolean success = runExperiment(experimentFile, clusterName);
                            if (success) {
                                response.getWriter().print("Experiment file " + experiment.getName() + " (" + experiment.getSize() + " B)"
                                        + " successfully uploaded and executed. Results are available in log files!");
                            } else {
                                response.getWriter().print("ERROR: Communication channel with the coordinator in not opened!");
                            }
                            return;
                        }
                    } else {
                        response.getWriter().print("File " + experiment.getName()
                                + " has size " + sizeInMbytes + " MB!"
                                + "<br>The maximum allowed file size is 1 MB");
                        return;
                    }

                } catch (FileUploadException e) {
                    response.getWriter().print("Experiment file error");
                    logger.error(e);
                } catch (Exception e) {
                    response.getWriter().print("Experiment file error");
                    logger.error(e);
                } finally {
                    response.getWriter().print("Experiment file error");
                    return;
                }
            }
        }

        if (method.equals(HttpMethods.GET) && request.getRequestURI().equals("/connection-reset")) {
            response.setContentType("text/html;charset=utf-8");
            response.setStatus(HttpServletResponse.SC_OK);

            MultiMap<String> params = new MultiMap<String>();
            UrlEncoded.decodeTo(baseRequest.getQueryString(), params, "ISO-8859-1");
            if (params.containsKey("cluster") && params.size() == 1) {
                Communicator c = communicators.get(params.getString("cluster"));
                if (c == null || !c.isOpen()) {
                    response.getWriter().print("ERROR: Communication channel with the coordinator in not opened!");
                    return;
                } else {
                    String resetResponse = c.resetSocket();
                    response.getWriter().print(resetResponse);
                    return;
                }
            }
        }

        if (method.equals(HttpMethods.GET) && request.getRequestURI().startsWith("/firmware-upload") && baseRequest.getQueryString() != null) {
            response.setContentType("text/html;charset=utf-8");
            response.setStatus(HttpServletResponse.SC_OK);

            if (firmwareBinary == null) {
                response.getWriter().print("ERROR: Invalid firmware binary!");
                return;
            }

            MultiMap<String> params = new MultiMap<String>();
            UrlEncoded.decodeTo(baseRequest.getQueryString(), params, "ISO-8859-1");
            if (params.containsKey("cluster") && params.containsKey("resource")
                    && params.size() == 2) {
                Communicator c = communicators.get(params.getString("cluster"));
                if (c == null || !c.isOpen()) {
                    response.getWriter().print("ERROR: Communication channel with the coordinator in not opened!");
                    return;
                }
                String resource = params.getString("resource");
                if (resource.equals("")) {
                    response.getWriter().print("ERROR: Empty resource!");
                    return;
                }
                boolean firmwareUploaded = uploadFirmware(resource, c);
                if (firmwareUploaded) {
                    response.getWriter().print("Firmware successfully uploaded!");
                    return;
                } else {
                    response.getWriter().print("CRC Mismatch: Fatal error while uploading the firmware!");
                    return;
                }
            }
        }

        if (method.equals(HttpMethods.GET) && request.getRequestURI().startsWith("/communicator") && baseRequest.getQueryString() != null) {
            response.setContentType("text/html;charset=utf-8");
            response.setStatus(HttpServletResponse.SC_OK);

            MultiMap<String> params = new MultiMap<String>();
            UrlEncoded.decodeTo(baseRequest.getQueryString(), params, "ISO-8859-1");
            if ((params.containsKey("cluster") && params.getString("method").equals("get")
                    && params.containsKey("resource")
                    && params.size() == 3)
                    || (params.containsKey("cluster") && params.getString("method").equals("post")
                    && params.containsKey("resource")
                    && params.containsKey("content")
                    && params.size() == 4)) {
                Communicator c = communicators.get(params.getString("cluster"));
                if (c == null || !c.isOpen()) {
                    response.getWriter().print("ERROR: Communication channel with the coordinator in not opened!");
                    return;
                }
                String nodeMethod = params.getString("method");
                if (nodeMethod.equals("get")) {
                    String resource = params.getString("resource");
                    if (resource.equals("")) {
                        response.getWriter().print("ERROR: Empty resource!");
                        return;
                    }
                    byte[] nodeResponse = c.sendGet(resource.getBytes("ISO-8859-1"));
                    response.getOutputStream().write(nodeResponse);
                    return;
                } else if (nodeMethod.equals("post")) {
                    String resource = params.getString("resource");
                    String content = params.getString("content");
                    if (resource.equals("") || content.equals("")) {
                        response.getWriter().print("ERROR: Empty resource or content!");
                        return;
                    }
                    byte[] nodeResponse = c.sendPost(resource.getBytes("ISO-8859-1"), content.getBytes("ISO-8859-1"));
                    response.getOutputStream().write(nodeResponse);
                    return;
                } else {
                    response.getWriter().print("Incorrect request");
                    return;
                }
            } else {
                response.getWriter().print("Incorrect request");
                return;
            }
        }

        if (!method.equals(HttpMethods.GET) || !request.getRequestURI().equals("/")) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        response.setContentType(MimeTypes.TEXT_HTML);

        ByteArrayISO8859Writer writer = new ByteArrayISO8859Writer(1500);

        writer.write("<HTML>\n<HEAD>\n<TITLE>Error 404 - Not Found");
        writer.write("</TITLE>\n<BODY>\n<H2>Error 404 - Not Found.</H2>\n");
        writer.write("No context on this server matched or handled this request.<BR>");

        if (_showContexts) {
            writer.write("Contexts known to this server are: <ul>");

            Server server = getServer();
            Handler[] handlers = server == null ? null : server.getChildHandlersByClass(ContextHandler.class);

            for (int i = 0; handlers != null && i < handlers.length; i++) {
                ContextHandler context = (ContextHandler) handlers[i];
                if (context.isRunning()) {
                    writer.write("<li><a href=\"");
                    if (context.getVirtualHosts() != null && context.getVirtualHosts().length > 0) {
                        writer.write("http://" + context.getVirtualHosts()[0] + ":" + request.getLocalPort());
                    }
                    writer.write(context.getContextPath());
                    if (context.getContextPath().length() > 1 && context.getContextPath().endsWith("/")) {
                        writer.write("/");
                    }
                    writer.write("\">");
                    writer.write(context.getContextPath());
                    if (context.getVirtualHosts() != null && context.getVirtualHosts().length > 0) {
                        writer.write("&nbsp;@&nbsp;" + context.getVirtualHosts()[0] + ":" + request.getLocalPort());
                    }
                    writer.write("&nbsp;--->&nbsp;");
                    writer.write(context.toString());
                    writer.write("</a></li>\n");
                } else {
                    writer.write("<li>");
                    writer.write(context.getContextPath());
                    if (context.getVirtualHosts() != null && context.getVirtualHosts().length > 0) {
                        writer.write("&nbsp;@&nbsp;" + context.getVirtualHosts()[0] + ":" + request.getLocalPort());
                    }
                    writer.write("&nbsp;--->&nbsp;");
                    writer.write(context.toString());
                    if (context.isFailed()) {
                        writer.write(" [failed]");
                    }
                    if (context.isStopped()) {
                        writer.write(" [stopped]");
                    }
                    writer.write("</li>\n");
                }
            }
        }

        for (int i = 0; i < 10; i++) {
            writer.write("\n<!-- Padding for IE                  -->");
        }

        writer.write("\n</BODY>\n</HTML>\n");
        writer.flush();
        response.setContentLength(writer.size());
        OutputStream out = response.getOutputStream();
        writer.writeTo(out);
        out.close();
    }

    /*
     * ------------------------------------------------------------
     */
    /**
     * @return Returns true if the handle can server the jetty favicon.ico
     */
    public boolean getServeIcon() {
        return _serveIcon;
    }

    /*
     * ------------------------------------------------------------
     */
    /**
     * @param serveIcon true if the handle can server the jetty favicon.ico
     */
    public void setServeIcon(boolean serveIcon) {
        _serveIcon = serveIcon;
    }

    public boolean getShowContexts() {
        return _showContexts;
    }

    public void setShowContexts(boolean show) {
        _showContexts = show;
    }

    public float bytesMbytes(long bytes) {
        BigDecimal res = new BigDecimal((float) bytes / MEGABYTE);
        BigDecimal roundedRes = res.setScale(2, res.ROUND_HALF_DOWN);
        return roundedRes.floatValue();
    }

    public boolean uploadFirmware(String resource, Communicator c) {
        byte[] nodeResponse;
        boolean success = false;
        try {
            ArrayList<byte[]> firmwareArray = new FileInPackets().getOtaPackets(firmwareBinary);
            for (int i = 0; i < firmwareArray.size(); i++) {
                nodeResponse = c.sendPost(resource.getBytes("ISO-8859-1"), firmwareArray.get(i));
            }
            String nodePrefix = resource.substring(0, resource.indexOf("firmware"));
            String crcResource = nodePrefix + "prog/nextFirmwareCrc";
            nodeResponse = c.sendPost(crcResource.getBytes("ISO-8859-1"), crc.toString().getBytes("ISO-8859-1"));
            String responseStr = new String(nodeResponse);
            if (responseStr.contains("CRC ok")) {
                success = true;
            } else {
                success = false;
            }
        } catch (Exception ex) {
            logger.error(ex);
        } finally {
            return success;
        }
    }

    public boolean runExperiment(File experimentFile, String cluster) {
        try {
            Communicator c = communicators.get(cluster);
            if (c == null || !c.isOpen()) {
                return false;
            }

            String strExperimentFile = new FileUtils().readFileToString(experimentFile);
            String requests[] = strExperimentFile.split("\\r?\\n\\r?\\n");
            for (String request : requests) {
                if (request.contains(";")) {
                    String[] post = request.split(";");
                    String resource = post[0];
                    String content = post[1];
                    c.sendPost(resource.getBytes(), content.getBytes());
                } else {
                    c.sendGet(request.getBytes());
                }
            }
            return true;
        } catch (IOException ex) {
            logger.error(ex);
            return false;
        }
    }

    public Map<String, String> getQueryMap(String query) {
        Map<String, String> map = new HashMap<String, String>();
        try {
            String[] reqests = query.split("&");
            for (String req : reqests) {
                String name = "";
                String value = "";
                String[] params = req.split("=");
                name = params[0];
                if (params.length == 2) {
                    value = params[1];
                }
                map.put(name, value);
            }
        } catch (Exception ex) {
            logger.error(ex);
        } finally {
            return map;
        }
    }
}