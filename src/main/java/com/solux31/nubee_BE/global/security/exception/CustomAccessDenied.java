package com.solux31.nubee_BE.global.security.exception;

import com.solux31.nubee_BE.global.security.util.SecurityResponseUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class CustomAccessDenied implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        SecurityResponseUtil.writeErrorResponse(
                response,
                HttpServletResponse.SC_FORBIDDEN,
                "COMMON403_1",
                "접근 권한이 없습니다."
        );
    }
}