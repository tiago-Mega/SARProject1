package com.sar.web.handler;

import com.sar.service.EventBroadcaster;
import com.sar.web.http.ReplyCode;
import com.sar.web.http.Request;
import com.sar.web.http.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintStream;

/**
* EventHandler manages Server-Sent Events (SSE) connections for real-time updates.
* 
* Server-Sent Events Protocol:
* SSE is a standard for pushing updates from server to client over HTTP. Unlike WebSocket,
* it's unidirectional (server → client) and uses regular HTTP connections.
* 
* Required HTTP Response Headers:
* - Content-Type: text/event-stream
* - Cache-Control: no-cache
* - Connection: keep-alive (or use chunked transfer encoding)
* 
* Event Format:
* Each event consists of one or more lines:
*   data: <message>\n\n
* 
* For example:
*   data: {"type": "group.created", "groupNumber": 42}\n\n
* 
* Connection Management:
* Unlike typical HTTP handlers that send a response and close the connection,
* SSE connections must remain open indefinitely (or until client disconnects).
* This requires:
* - Not closing the output stream after initial headers
* - Keeping the connection alive in a loop or registering it for async events
* - Detecting client disconnection and cleaning up
* 
* Integration with EventBroadcaster:
* This handler should register the client's output stream with the EventBroadcaster
* so that when events occur (group created, deleted, etc.), all connected clients
* receive the updates automatically.
* 
* Handler Pattern:
* Extends AbstractRequestHandler, implementing the template method pattern.
* The handle() method delegates to handleGet() or handlePost() based on HTTP method.
*/
public class EventHandler extends AbstractRequestHandler {
    private static final Logger logger = LoggerFactory.getLogger(EventHandler.class);

    private final EventBroadcaster eventBroadcaster;

    /**
    * Constructor with dependency injection.
    * EventBroadcaster is injected by Main during initialization.
    * 
    * @param eventBroadcaster the shared event broadcaster service
    */
    public EventHandler(EventBroadcaster eventBroadcaster) {
        this.eventBroadcaster = eventBroadcaster;
    }

    /**
    * Handles GET requests to /events endpoint.
    * Should establish an SSE connection and keep it open.
    * 
    * Steps typically include:
    * 1. Set appropriate SSE headers on the response
    * 2. Register this connection with the EventBroadcaster
    * 3. Keep the connection alive (prevent automatic closure)
    * 4. Handle cleanup when client disconnects
    */
    @Override
    protected void handleGet(Request request, Response response) {
        response.setHeader("Content-Type", "text/event-stream");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");

        PrintStream clientStream = response.getPrintStream();

        try {
            response.send_Answer(clientStream);
            response.setFullyHandled(true);     
            eventBroadcaster.registerClient(clientStream);

            // Keep the connection open indefinitely (or until client disconnects)
            while (true) {
                Thread.sleep(15000);
                clientStream.write(": heartbeat\n\n".getBytes());
                clientStream.flush();
                
            }
        } catch (Exception e) {
            // Handle exceptions (e.g., client disconnects)
            eventBroadcaster.removeClient(clientStream);
        }
    }

    /**
    * POST is not typically used for SSE.
    * SSE connections are established via GET requests.
    */
    @Override
    protected void handlePost(Request request, Response response) {
        logger.error("EventHandler does not handle POST requests.");
        response.setError(ReplyCode.NOTIMPLEMENTED, request.version);
    }
}
