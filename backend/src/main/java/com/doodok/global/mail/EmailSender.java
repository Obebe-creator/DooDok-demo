package com.doodok.global.mail;

/**
 * 메일 발송. 구현을 바꿔 끼울 수 있도록 인터페이스로 둔다.
 * 실제 발송은 {@link SmtpEmailSender}(로컬 Mailpit, 운영 AWS SES), 테스트는 가짜 구현을 쓴다.
 */
public interface EmailSender {

    void send(String to, String subject, String text);
}
