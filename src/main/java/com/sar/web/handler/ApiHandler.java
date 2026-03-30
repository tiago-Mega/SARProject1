package com.sar.web.handler;

import com.sar.model.Group;
import com.sar.server.Main;
import com.sar.web.http.Request;
import com.sar.web.http.Response;
import com.sar.web.http.ReplyCode;
import com.sar.service.GroupService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
* ApiHandler provides RESTful JSON API for group management.
* 
* This handler returns JSON responses, not HTML pages.
* The index.html page uses JavaScript to call these endpoints via AJAX.
* 
* Endpoints:
* - GET /api → Returns JSON array of all groups
* - POST /api → Creates/updates a group, returns JSON response
* 
* Response format should be JSON with appropriate HTTP headers.
*/
public class ApiHandler extends AbstractRequestHandler {
    private static final Logger logger = LoggerFactory.getLogger(ApiHandler.class);
    private final GroupService groupService;

    public ApiHandler(GroupService groupService) {
        this.groupService = groupService;
    }

    /**
    * Handles GET /api - Returns all groups as JSON.
    * 
    * The response should contain group data in JSON format that the
    * JavaScript in index.html can parse and display in the table.
    * 
    * Appropriate HTTP headers must be set for JSON responses.
    */
    @Override
    protected void handleGet(Request request, Response response) {
        logger.debug("GET /api - Fetching all groups");
        
        try {
            // Fetch groups from the service
            var groups = groupService.getAllGroups();

            // Convert groups to JSON (using a simple manual approach here)
            StringBuilder json = new StringBuilder();
            json.append("[");
            for (int i = 0; i < groups.size(); i++) {
                var group = groups.get(i);
                json.append("{")
                    .append("\"groupNumber\":\"").append(group.getGroupNumber()).append("\",")
                    .append("\"accessCount\":").append(group.getAccessCount()).append(",")
                    .append("\"counter\":").append(group.isCounter()).append(",")
                    .append("\"lastUpdate\":\"").append(group.getLastUpdate()).append("\",")
                    .append("\"members\":[");

                for (int m = 0; m < Main.GROUP_SIZE; m++) {
                    Group.Member member = group.getMember(m);
                    json.append("{")
                        .append("\"number\":\"").append(member != null ? member.getNumber() : "").append("\",")
                        .append("\"name\":\"").append(member != null ? member.getName() : "").append("\"")
                        .append("}");
                    if (m < Main.GROUP_SIZE - 1) json.append(",");
                }

                json.append("]}");
                if (i < groups.size() - 1) json.append(",");
            }
            json.append("]");

            response.setCode(ReplyCode.OK);
            response.setVersion(request.version);
            response.setHeader("Content-Type", "application/json");
            response.setText(json.toString());

        } catch (Exception e) {
            logger.error("Error fetching groups", e);

            response.setCode(ReplyCode.INTERNALERROR);
            response.setVersion(request.version);
            response.setText("{\"message\":\"Error fetching groups\"}");
            response.setHeader("Content-Type", "application/json");
        }
    }

    /**
    * Handles POST /api - Create or update a group.
    * 
    * The form data from index.html contains group information that
    * should be validated and persisted using the GroupService.
    * 
    * Response should be JSON indicating success or failure.
    * Appropriate HTTP headers must be set.
    */
    @Override
    protected void handlePost(Request request, Response response) {
        logger.debug("POST /api - Creating/updating group");
        
        try {
            String groupNumber = request.getPostParameters().getProperty("groupNumber");
            String action = request.getPostParameters().getProperty("action", "save");
            String counterStr = request.getPostParameters().getProperty("counter", "false");

            if (groupNumber == null || groupNumber.isEmpty()) {
                response.setCode(ReplyCode.BADREQ);
                response.setText("{\"error\":\"Group number is required\"}");
                response.setHeader("Content-Type", "application/json");
                return;
            }

            if ("delete".equalsIgnoreCase(action)) {
                groupService.deleteGroup(groupNumber);

                response.setCode(ReplyCode.OK);
                response.setVersion(request.version);
                response.setHeader("Content-Type", "application/json");
                response.setText("{\"message\":\"Group deleted successfully\"}");
                return;
            }

            String[] names = new String[Main.GROUP_SIZE];
            String[] numbers = new String[Main.GROUP_SIZE];
            boolean counter = "on".equalsIgnoreCase(counterStr) || "true".equalsIgnoreCase(counterStr);

            // Populate member data
            for (int m = 0; m < Main.GROUP_SIZE; m++) {
                numbers[m] = request.getPostParameters().getProperty("number" + m);
                names[m] = request.getPostParameters().getProperty("name" + m);

                if (numbers[m] == null || numbers[m].isBlank() || names[m] == null || names[m].isBlank()) {
                    response.setCode(ReplyCode.BADREQ);
                    response.setVersion(request.version);
                    response.setHeader("Content-Type", "application/json");
                    response.setText("{\"error\":\"Member " + m + " data is incomplete\"}");
                    return;
                }
            }

            groupService.saveGroup(groupNumber, numbers, names, counter);

            // Set cookie with last group number for user convenience
            response.setHeader("Set-Cookie", "lastGroupNumber=" + groupNumber + "; Path=/; Max-Age=86400");

            // Respond with success message
            response.setCode(ReplyCode.OK);
            response.setVersion(request.version);
            response.setHeader("Content-Type", "application/json");
            response.setText("{\"message\":\"Group saved successfully\"}");

        } catch (Exception e) {
            logger.error("Error saving group", e);

            response.setCode(ReplyCode.INTERNALERROR);
            response.setVersion(request.version);
            response.setText("{\"error\":\"Failed to save group\"}");
            response.setHeader("Content-Type", "application/json");
        }
    }
}