package com.sar.server;

import com.sar.web.http.Request;
import com.sar.web.http.Response;
import com.sar.web.http.ReplyCode;
import com.sar.controller.HttpController;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.ServerSocket;
import javax.net.ssl.SSLSocket;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;
import java.util.StringTokenizer;


public class ConnectionThread extends Thread  {
    private static final Logger logger = LoggerFactory.getLogger(ConnectionThread.class);
    private final HttpController controller;

    private final Main HTTPServer;
    private final ServerSocket ServerSock;
    private final Socket client;
    private final DateFormat HttpDateFormat;
    
    // Timeout for keep-alive connections in milliseconds
    private static final int KEEP_ALIVE_TIMEOUT = 20000;

    // Constructor for the ConnectionThread class. 
    public ConnectionThread(Main HTTPServer, ServerSocket ServerSock, 
    Socket client, HttpController controller) {
        this.HTTPServer = HTTPServer;
        this.ServerSock = ServerSock;
        this.client = client;
        this.controller = controller;
        this.HttpDateFormat = new SimpleDateFormat("EE, d MMM yyyy HH:mm:ss zz", Locale.UK);
        this.HttpDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));

        setPriority(NORM_PRIORITY - 1);
    }
    

    /** Reads a new HTTP Request from the input steam in to a Request object
    * @param TextReader   input stream Buffered Reader coonected to client socket
    * @param echo  if true, echoes the received message to the screen
    * @return Request object containing the request received from the client, or null in case of error
    * @throws java.io.IOException 
    */
    public Request GetRequest (BufferedReader TextReader) throws IOException {
        
        // Read the request line
        String request = TextReader.readLine();
        if (request == null) {
            logger.debug("Invalid request Connection closed");
            return null;
        }
        logger.info("Request: {}", request);

        // Tokenize the request line and check if it has the correct format (method, URL, version)
        StringTokenizer st= new StringTokenizer(request);
        if (st.countTokens() != 3) {
           logger.debug("Invalid request received {}", request);
           return null;  // Invalid request
        } 
        // Create a new Request object and store the method, URL and version in it
        Request req= new Request (client.getInetAddress ().getHostAddress (), client.getPort (), ServerSock.getLocalPort ());  
        req.method  = st.nextToken();    // Store HTTP method
        req.urlText = st.nextToken();    // Store URL
        req.version = st.nextToken();    // Store HTTP version
     
        // Read the HTTP headers and store them in the Request object. The headers are read until an empty line is found (that indicates the end of the headers)
        String line;
        while ((line = TextReader.readLine()) != null) {
            if (line.isEmpty()) break; // End of headers
            int idx = line.indexOf(':');
            if (idx > 0) {
                String name = line.substring(0, idx).trim();
                String value = line.substring(idx+1).trim();
                req.headers.setHeader(name, value);
                logger.debug("Header: {}: {}", name, value);
            }
        }

        req.parseCookies();
        
        // Check if there is a Content-Length header and if so read the specified number of bytes from the input stream and store it in the Request object. 
        int clength = 0;
        try {
            String len = req.headers.getHeaderValue("Content-Length");
            if (len != null) clength = Integer.parseInt(len);
        } catch (NumberFormatException e) {
            logger.error("Bad request\n");
            return null;
        }

        if (clength > 0) {
            char[] cbuf = new char[clength];
            int read = TextReader.read(cbuf, 0, clength);
            if (read != clength) {
                logger.error("Expected {} bytes but got {}", clength, read);
                return null; // Incomplete body
            }
            req.text = new String(cbuf);
        }

        return req;
    }    
   
    /**
    * Checks if the connection is an HTTP connection (not HTTPS)
    * @return
    */
    private boolean isHttpConnection() {
        return !(client instanceof SSLSocket);
    }


    /**
    * Builds the redirect URL for HTTPS connections
    * @param req
    * @return
    */
    private String buildRedirectUrl(Request req) {
        return "https://" + client.getLocalAddress().getHostName() + ":" + Main.HTTPSport + req.urlText;
    }

    
    @Override
    public void run(){

        Response res = null;   // HTTP response object
        Request req = null;    // HTTP request object
        PrintStream TextPrinter= null;

        try {
            InputStream in = client.getInputStream( );
            OutputStream out = client.getOutputStream( );
            BufferedReader TextReader = new BufferedReader(new InputStreamReader(in, "8859_1" ));
            TextPrinter = new PrintStream(out, false, "8859_1");

            // Set socket timeout for keep-alive connections
            client.setSoTimeout(KEEP_ALIVE_TIMEOUT);

            do {
                // Read and parse request
                if((req = GetRequest(TextReader)) == null) {
                    logger.debug("No valid request received, closing connection");
                    break; // Invalid request, close connection
                }          

                //Create response object. 
                res = new Response(HTTPServer.ServerName);

                // Check if the connection is HTTP and if so, redirect to HTTPS
                if (isHttpConnection()) {
                    String redirectUrl = buildRedirectUrl(req);
                    logger.info("Redirecting HTTP -> HTTPS: {}", redirectUrl);
                    res.setCode(ReplyCode.TMPREDIRECT);                      
                    res.setHeader("Location", redirectUrl);
                    res.setHeader("Content-Length", "0");
                    res.send_Answer(TextPrinter);
                    TextPrinter.flush();
                    break; // After a redirect we close — browser will reconnect on HTTPS
                }

                // Set the Connection header in the response based on whether we will keep the connection alive or not
                res.setHeader("Connection", req.headers.getHeaderValue("Connection"));

                // Let the controler (HttpContrller) handle the request and fill the response.
                controller.handleRequest(req, res);
                
                // Send response
                res.send_Answer(TextPrinter);
                TextPrinter.flush();
                
                logger.debug("Served {} {} — keepAlive = {}", req.method, req.urlText, req.headers.getHeaderValue("Connection"));

            } while("keep-alive".equalsIgnoreCase(req.getHeaderValue("Connection")));

        } catch (java.net.SocketTimeoutException e) {
            logger.debug("Keep-alive timeout, closing connection");
        } catch (Exception e) {
            logger.error("Error processing request", e);
            if (res != null) res.setError(ReplyCode.BADREQ, req != null ? req.version : "HTTP/1.1");
        } finally {
            cleanup(TextPrinter);
        }
    }   


    private void cleanup(PrintStream TextPrinter) {
        try {
            if (TextPrinter != null) TextPrinter.close();
            if (client != null) client.close();
        } catch (IOException e) {
            logger.error("Error during cleanup", e);
        } finally {
            HTTPServer.thread_ended();
            logger.debug("Connection closed for client: {}:{}", 
                client.getInetAddress().getHostAddress(), 
                client.getPort());
        }
    }

}