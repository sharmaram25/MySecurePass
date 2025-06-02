package com.nov.passwordgeneratortool.service;

import com.nov.passwordgeneratortool.model.Mail;
import com.nov.passwordgeneratortool.model.MailRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class PasswordMailService {

    Logger logger = LoggerFactory.getLogger(PasswordMailService.class);
    
    @Autowired
    private JavaMailSender mailSender;
    
    public void sendingMail(MailRequest mailRequest) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(mailRequest.getEmail());
            message.setSubject("Your Secure Password - MySecurePass");
            message.setText(getEmailContent(mailRequest));
            
            mailSender.send(message);
            logger.info("Email sent successfully to: {}", mailRequest.getEmail());
        } catch (Exception e) {
            logger.error("Failed to send email: {}", e.getMessage());
        }
    }

    private static String getEmailContent(MailRequest mailRequest) {
        return "Dear " + mailRequest.getName() + ",\n\n"
                + "Thank you for using MySecurePass!\n\n"
                + "Here's your secure password: " + mailRequest.getPassword() + "\n\n"
                + "Important Security Tips:\n"
                + "• Store this password in a secure password manager\n"
                + "• Never share this password with anyone\n"
                + "• Consider changing your passwords every 3-6 months\n\n"
                + "This email was sent automatically. Please do not reply to this message.\n\n"
                + "Best regards,\n"
                + "The Security Team";
    }
}
