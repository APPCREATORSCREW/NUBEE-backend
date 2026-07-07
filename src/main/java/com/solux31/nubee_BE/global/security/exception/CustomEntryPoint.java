package com.solux31.nubee_BE.global.security.exception;

import com.solux31.nubee_BE.global.security.util.SecurityResponseUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class CustomEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        SecurityResponseUtil.writeErrorResponse(
                response,
                HttpServletResponse.SC_UNAUTHORIZED,
                "COMMON401_1",
                "인증이 필요합니다."
        );
    }
}