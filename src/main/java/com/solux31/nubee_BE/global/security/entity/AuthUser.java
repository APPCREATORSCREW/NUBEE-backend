package com.solux31.nubee_BE.global.security.entity;

import com.solux31.nubee_BE.domain.auth.entity.User;
import com.solux31.nubee_BE.domain.auth.enums.UserStatus;
import java.time.LocalDate;
import java.time.Period;
import java.util.Optional;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter
public class AuthUser implements UserDetails {

    private final Long userId;
    private final String email;
    private final String password;
    private final UserStatus status;
    private final boolean isParentVerified;  // 추가
    private final LocalDate birthDate;       // 추가



    public AuthUser(User user) {
        this.userId = user.getId();
        this.email = user.getEmail();
        this.password = Optional.ofNullable(user.getPassword()).orElse(""); // null이면 빈 문자열
        this.status = user.getStatus();
        this.isParentVerified = user.isParentVerified();  // 추가
        this.birthDate = user.getBirthDate();              // 추가
    }

    // 만 14세 미만 여부 확인
    public boolean isUnder14() {
        if (birthDate == null) return false;
        return Period.between(birthDate, LocalDate.now()).getYears() < 14;
    }

    @Override
    public boolean isEnabled() {
        return status == UserStatus.ACTIVE;  // INACTIVE면 인증 차단
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }
}