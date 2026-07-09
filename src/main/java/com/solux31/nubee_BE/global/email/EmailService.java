package com.solux31.nubee_BE.global.email;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String mailUsername;

    // 6자리 인증 코드 생성
    public String generateCode() {
        SecureRandom random = new SecureRandom();
        int code = random.nextInt(900000) + 100000; // 100000 ~ 999999
        return String.valueOf(code);
    }

    // 비밀번호 찾기 인증 코드 발송
    public void sendPasswordResetEmail(String toEmail, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailUsername);
        message.setTo(toEmail);
        message.setSubject("[Nubee] 비밀번호 재설정 인증 코드");
        message.setText("안녕하세요, Nubee입니다.\n\n"
                + "요청하신 비밀번호 재설정 인증 코드가 생성되었습니다.\n\n"
                + "비밀번호 재설정 인증 코드: " + code + "\n\n"
                + "인증 코드는 5분간 유효합니다.\n"
                + "본인이 요청하지 않은 경우 이 메일을 무시해주세요.");
        mailSender.send(message);
    }

    // 부모님 이메일 인증 코드 발송
    public void sendParentVerifyEmail(String toEmail, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailUsername);
        message.setTo(toEmail);
        message.setSubject("[Nubee] 보호자 이메일 인증 코드");
        message.setText("안녕하세요, Nubee입니다.\n\n"
                + "요청하신 보호자 이메일 인증 코드가 생성되었습니다.\n\n"
                + "자녀의 회원가입을 위한 보호자 인증 코드: " + code + "\n\n"
                + "인증 코드는 5분간 유효합니다.\n"
                + "본인이 요청하지 않은 경우 이 메일을 무시해주세요.");
        mailSender.send(message);
    }
}