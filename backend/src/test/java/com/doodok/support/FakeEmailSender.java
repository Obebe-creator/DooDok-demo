package com.doodok.support;

import com.doodok.global.mail.EmailSender;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 테스트용 메일 발송기. 실제로 보내지 않고 보낸 내용을 기록한다. */
public class FakeEmailSender implements EmailSender {

    private static final Pattern CODE = Pattern.compile("\\b(\\d{6})\\b");

    private final List<SentEmail> sent = new CopyOnWriteArrayList<>();

    public record SentEmail(String to, String subject, String text) {
    }

    @Override
    public void send(String to, String subject, String text) {
        sent.add(new SentEmail(to, subject, text));
    }

    public List<SentEmail> sentTo(String to) {
        return sent.stream().filter(email -> email.to().equals(to)).toList();
    }

    /** 해당 주소로 마지막에 보낸 메일의 6자리 인증 코드 */
    public String latestCodeFor(String to) {
        List<SentEmail> emails = sentTo(to);
        if (emails.isEmpty()) {
            throw new IllegalStateException("No email sent to " + to);
        }
        Matcher matcher = CODE.matcher(emails.getLast().text());
        if (!matcher.find()) {
            throw new IllegalStateException("No code in email to " + to);
        }
        return matcher.group(1);
    }

    public void clear() {
        sent.clear();
    }
}
