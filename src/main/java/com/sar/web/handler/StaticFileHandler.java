package com.sar.web.handler;

import com.sar.web.http.Request;
import com.sar.web.http.Response;
import com.sar.web.http.ReplyCode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Map;
import java.util.HashMap;

public class StaticFileHandler extends AbstractRequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(StaticFileHandler.class);

    private final String homeFileName;
    private final String baseDirectory;
    private final Map<String, String> mimeTypes;

    public StaticFileHandler(String baseDirectory, String homeFileName) {
        this.mimeTypes = MIME_TYPES;
        this.homeFileName = homeFileName;
        this.baseDirectory = baseDirectory;
    }

    private static final Map<String, String> MIME_TYPES = new HashMap<>();
    
    static {
        MIME_TYPES.put(".html", "text/html");
        MIME_TYPES.put(".htm" , "text/html");
        MIME_TYPES.put(".css" , "text/css");
        MIME_TYPES.put(".js"  , "text/javascript");
        MIME_TYPES.put(".jpg" , "image/jpeg");
        MIME_TYPES.put(".jpeg", "image/jpeg");
        MIME_TYPES.put(".png" , "image/png");
        MIME_TYPES.put(".gif" , "image/gif");
    }

    @Override
    protected void handleGet(Request request, Response response) {
        String path = request.urlText;
        if (path.equals("/")) {
            path = "/" + homeFileName;
        }

        String fullPath = baseDirectory + path;
        File file = new File(fullPath);
        
        try {
            if (file.exists() && file.isFile()) {
                String mimeType = getMimeType(path);
                response.setFile(file);
                response.setCode(ReplyCode.OK);
                response.setVersion(request.version);
                response.setHeader("Content-Type", mimeType);
                response.setHeader("Content-Length", String.valueOf(file.length()));
                response.setHeader("Connection", request.headers.getHeaderValue("Connection"));
                logger.info("Serving file: {} ({})", fullPath, mimeType);
            } else {
                response.setError(ReplyCode.NOTFOUND, request.version);
                response.setHeader("Content-Length", "0");
                response.setHeader("Connection", request.headers.getHeaderValue("Connection"));
                logger.warn("File not found: {}. Returning 404 error.", fullPath);
            }
        } catch (Exception e) {
            response.setError(ReplyCode.INTERNALERROR, request.version);
            response.setHeader("Content-Length", "0");
            response.setHeader("Connection", "close");
            logger.error("Error handling GET request for file: {}", fullPath, e);
        }
    }

    @Override
    protected void handlePost(Request request, Response response) {
        // Static files don't handle POST requests
        response.setError(ReplyCode.NOTIMPLEMENTED, request.version);
        response.setHeader("Content-Length", "0");
        response.setHeader("Connection", "close");
        logger.error("StaticFileHandler does not handle POST requests.");
    }

    private String getMimeType(String path) {
        int dotIndex = path.lastIndexOf('.');
        if (dotIndex > 0) {
            String extension = path.substring(dotIndex).toLowerCase();
            return mimeTypes.getOrDefault(extension, DEFAULT_MIME_TYPE);
        }
        return DEFAULT_MIME_TYPE;
    }
}