package com.company.flowmanagement.controller;

import com.company.flowmanagement.model.User;
import com.company.flowmanagement.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.ui.Model;

@ControllerAdvice
public class GlobalControllerAdvice {

    private final UserRepository userRepository;

    public GlobalControllerAdvice(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @ModelAttribute
    public void addGlobalAttributes(Model model, Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            String username = authentication.getName();
            User user = userRepository.findByUsername(username);
            if (user != null) {
                // Add common attributes for fragments like admin-navbar
                model.addAttribute("companyName", user.getCompanyName());
                model.addAttribute("username", user.getUsername());
                model.addAttribute("userEmail", user.getEmail());
                model.addAttribute("companyLogo", user.getCompanyLogo());
                model.addAttribute("userRole", user.getRole());
            }
        }
    }
}
