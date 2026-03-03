package com.company.flowmanagement.controller;

import com.company.flowmanagement.model.User;
import com.company.flowmanagement.repository.UserRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.security.Principal;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final UserRepository userRepository;

    public AdminController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model, Principal principal) {
        addAdminDetails(model, principal);
        return "admin-dashboard";
    }

    @GetMapping("/employees")
    public String employeeManager(Model model, Principal principal) {
        addAdminDetails(model, principal);
        return "employee-manager";
    }

    private void addAdminDetails(Model model, Principal principal) {
        if (principal != null) {
            User admin = userRepository.findByUsername(principal.getName());
            if (admin != null) {
                model.addAttribute("companyName", admin.getCompanyName());
                model.addAttribute("username", admin.getUsername());
                model.addAttribute("companyLogo", admin.getCompanyLogo());
            }
        }
    }
}
