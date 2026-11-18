package com.doodok.global.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * SMTP로 메일을 보낸다. 어느 서버로 보낼지는 spring.mail 설정이 정한다. (로컬: Mailpit, 운영: AWS SES)
 * 메일 서버가 느려도 API 응답이 늦어지지 않도록 별도 스레드에서 보낸다.
 */
@Slf4j
@Component
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpEmailSender(JavaMailSender mailSender, @Value("${doodok.mail.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Async
    @Override
    public void send(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        try {
            mailSender.send(message);
        } catch (MailException e) {
            // 비동기라 호출한 쪽으로 예외가 전달되지 않는다. 로그로 남기고, 사용자는 재발송으로 다시 받는다.
            log.error("Failed to send email: subject={}", subject, e);
        }
    }
}
