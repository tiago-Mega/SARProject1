package com.sar.service;

import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * EventBroadcaster manages Server-Sent Events (SSE) connections and broadcasts events to all connected clients.
 * 
 * This service maintains a thread-safe collection of client connections and provides methods to:
 * - Register new SSE client connections
 * - Remove disconnected clients
 * - Broadcast events to all active clients
 * 
 * Thread Safety Considerations:
 * Multiple ConnectionThread instances may simultaneously register/remove clients or trigger broadcasts.
 * The implementation must handle concurrent access without data corruption or missed events.
 * 
 * SSE Event Format:
 * Events are sent as text in the format:
 *   data: <event-data>\n\n
 * 
 * For structured data (e.g., JSON):
 *   data: {"type": "group.created", "group": {...}}\n\n
 * 
 * Integration Points:
 * - GroupServiceImpl calls broadcast() when groups are created, updated, or deleted
 * - EventHandler registers client OutputStreams when /events endpoint is requested
 * - ConnectionThread may need to clean up disconnected clients
 */
public class EventBroadcaster {

    /**
     * Thread-safe list of client output streams.
     * CopyOnWriteArrayList is suitable for scenarios with more reads than writes.
     */
    private final List<OutputStream> clients = new CopyOnWriteArrayList<>();

    /**
     * Registers a new SSE client connection.
     * The provided OutputStream should remain open for the duration of the SSE connection.
     * 
     * @param clientStream the output stream to send events to
     */
    public void registerClient(OutputStream clientStream) {
        // Implementation needed
    }

    /**
     * Removes a client connection (e.g., when the client disconnects or connection is closed).
     * 
     * @param clientStream the output stream to remove
     */
    public void removeClient(OutputStream clientStream) {
        // Implementation needed
    }

    /**
     * Broadcasts an event to all registered clients.
     * The event data will be formatted according to SSE protocol and sent to each client.
     * 
     * If a client connection fails (e.g., client disconnected), it should be removed from the list.
     * 
     * @param eventData the data to broadcast (will be formatted as SSE event)
     */
    public void broadcast(String eventData) {
        // Implementation needed
    }

    /**
     * Returns the current number of connected clients.
     * Useful for monitoring and debugging.
     * 
     * @return number of active SSE connections
     */
    public int getClientCount() {
        return clients.size();
    }
}
