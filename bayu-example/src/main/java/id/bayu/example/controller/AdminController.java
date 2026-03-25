package id.bayu.example.controller;

import id.bayu.core.annotation.Autowired;
import id.bayu.example.service.UserService;
import id.bayu.security.annotation.Secured;
import id.bayu.security.interceptor.SecurityContext;
import id.bayu.web.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired
    private UserService userService;

    public void setUserService(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/stats")
    @Secured({"ROLE_ADMIN"})
    public Map<String, Object> stats() {
        return Map.of(
                "totalUsers", userService.findAll().size(),
                "message", "Admin only endpoint"
        );
    }

    @GetMapping("/me")
    @Secured({"ROLE_USER"})
    public Map<String, Object> me() {
        var principal = SecurityContext.getPrincipal();
        return Map.of(
                "username", principal != null ? principal.getName() : "anonymous",
                "roles", SecurityContext.getRoles().toString()
        );
    }
}
