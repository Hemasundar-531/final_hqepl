package com.company.flowmanagement.controller;

import com.company.flowmanagement.model.FMSStep;
import com.company.flowmanagement.repository.FMSStepRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/employee/fms-steps")
public class FMSStepController {

    private final FMSStepRepository fmsStepRepository;
    private final com.company.flowmanagement.service.EmployeeService employeeService;

    public FMSStepController(FMSStepRepository fmsStepRepository,
            com.company.flowmanagement.service.EmployeeService employeeService) {
        this.fmsStepRepository = fmsStepRepository;
        this.employeeService = employeeService;
    }

    @GetMapping
    public String getAllSteps(org.springframework.security.core.Authentication authentication, Model model) {
        String username = authentication.getName();
        model.addAllAttributes(employeeService.getEmployeeContext(username));

        List<FMSStep> steps = fmsStepRepository.findAll();
        model.addAttribute("steps", steps);
        return "fms-steps";
    }
}
