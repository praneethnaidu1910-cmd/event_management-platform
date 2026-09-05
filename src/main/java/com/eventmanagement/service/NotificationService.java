package com.eventmanagement.service;

import com.eventmanagement.entity.Order;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class NotificationService {

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public NotificationService(
            JavaMailSender mailSender,
            @Value("${app.mail.from:no-reply@eventmanagement.com}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    /**
     * Best-effort: a broken mail server should never fail the purchase that
     * already committed, so any send failure is logged and swallowed here.
     */
    public void sendPurchaseConfirmation(Order order) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(order.getUser().getEmail());
        message.setSubject("Your tickets for " + order.getEvent().getTitle());
        message.setText(buildBody(order));

        try {
            mailSender.send(message);
        } catch (MailException ex) {
            log.warn("Failed to send purchase confirmation to {} for order {}: {}",
                    order.getUser().getEmail(), order.getId(), ex.getMessage());
        }
    }

    private String buildBody(Order order) {
        return String.format(
                "Hi %s,%n%nYour order for \"%s\" is confirmed.%n%nTickets purchased: %d%nTotal paid: $%s%n%nSee you there!",
                order.getUser().getFirstName(),
                order.getEvent().getTitle(),
                order.getTickets().size(),
                order.getTotalAmount());
    }
}
