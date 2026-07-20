package com.solux31.nubee_BE.global.security.filter;

import com.solux31.nubee_BE.global.security.entity.AuthUser;
import com.solux31.nubee_BE.global.security.service.CustomUserDetailsService;
import com.solux31.nubee_BE.global.security.util.JwtUtil;
import com.solux31.nubee_BE.global.security.util.SecurityResponseUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService customUserDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        // Authorization 헤더 없거나 Bearer로 시작 안 하면 통과
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7); // "Bearer " 이후 토큰 추출

        // 토큰 유효성 검증
        if (jwtUtil.isTokenValid(token) && jwtUtil.isAccessToken(token)) {
            try {
                String email = jwtUtil.getEmail(token);
                UserDetails userDetails = customUserDetailsService.loadUserByUsername(email);

                // INACTIVE 계정 차단 추가
                if (!userDetails.isEnabled()) {
                    SecurityResponseUtil.writeErrorResponse(
                            response,
                            HttpServletResponse.SC_UNAUTHORIZED,
                            "COMMON401_1",
                            "비활성화된 계정입니다."
                    );
                    return;
                }

                // 만 14세 미만 + 부모님 인증 미완료 차단 추가
                if (userDetails instanceof AuthUser authUser) {
                    if (authUser.isUnder14() && !authUser.isParentVerified()) {
                        String requestURI = request.getServletPath();
                        if (!requestURI.startsWith("/auth/parent") &&
                                !requestURI.startsWith("/auth/birthdate") &&
                                !requestURI.startsWith("/auth/logout")) {
                            SecurityResponseUtil.writeErrorResponse(response,
                                    HttpServletResponse.SC_FORBIDDEN,
                                    "COMMON403_1",
                                    "부모님 이메일 인증이 필요합니다.");
                            return;
                        }
                    }
                }

                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);

            } catch (UsernameNotFoundException e) {
                SecurityResponseUtil.writeErrorResponse(
                        response,
                        HttpServletResponse.SC_UNAUTHORIZED,
                        "COMMON401_1",
                        "존재하지 않는 유저입니다."
                );
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}