package com.example.tradingsimulator.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailSenderService {

    @Autowired
    private JavaMailSender mailSender;

    public void sendEmail(String toEmail, String orderConfirmation, String emailBody) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(System.getProperty("user.name"));
        message.setTo(toEmail);
        message.setSubject(orderConfirmation);
        message.setText(emailBody);

        mailSender.send(message);
        System.out.println("Mail sent successfully");
    }
}
