package com.eventmanagement.service;

import com.eventmanagement.entity.Event;
import com.eventmanagement.entity.Order;
import com.eventmanagement.entity.Ticket;
import com.eventmanagement.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NotificationServiceTest {

    private JavaMailSender mailSender;
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        notificationService = new NotificationService(mailSender, "no-reply@eventmanagement.com");
    }

    private Order order() {
        User buyer = User.builder().id(5L).email("buyer@example.com").firstName("Ada").build();
        Event event = Event.builder().id(10L).title("Spring Boot Conf").build();
        Order order = Order.builder()
                .id(42L)
                .user(buyer)
                .event(event)
                .totalAmount(new BigDecimal("50.00"))
                .build();
        order.setTickets(List.of(new Ticket(), new Ticket()));
        return order;
    }

    @Test
    void sendPurchaseConfirmation_sendsMessageWithOrderDetails() {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);

        notificationService.sendPurchaseConfirmation(order());

        verify(mailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        assertThat(message.getFrom()).isEqualTo("no-reply@eventmanagement.com");
        assertThat(message.getTo()).containsExactly("buyer@example.com");
        assertThat(message.getSubject()).isEqualTo("Your tickets for Spring Boot Conf");
        assertThat(message.getText()).contains("Ada", "Spring Boot Conf", "2", "50.00");
    }

    @Test
    void sendPurchaseConfirmation_swallowsMailFailuresInsteadOfThrowing() {
        doThrow(new MailSendException("smtp connection refused")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThatCode(() -> notificationService.sendPurchaseConfirmation(order())).doesNotThrowAnyException();
    }
}
