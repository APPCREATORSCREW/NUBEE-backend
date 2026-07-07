package com.solux31.nubee_BE.global.security.util;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public class SecurityResponseUtil {

    public static void writeErrorResponse(HttpServletResponse response,
                                          int status,
                                          String code,
                                          String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                String.format("{\"isSuccess\":false,\"code\":\"%s\",\"message\":\"%s\"}", code, message)
        );
    }
}