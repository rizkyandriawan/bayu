package id.bayu.example.controller;

import id.bayu.core.annotation.Autowired;
import id.bayu.example.service.GreetingService;
import id.bayu.web.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class HelloController {

    @Autowired
    private GreetingService greetingService;

    public void setGreetingService(GreetingService greetingService) {
        this.greetingService = greetingService;
    }

    @GetMapping("/hello/{name}")
    public Map<String, String> hello(@PathVariable String name) {
        return Map.of("message", greetingService.greet(name));
    }

    @GetMapping("/hello")
    public Map<String, String> helloDefault(@RequestParam(value = "name") String name) {
        String resolved = (name != null) ? name : "World";
        return Map.of("message", greetingService.greet(resolved));
    }

    @PostMapping("/echo")
    public Map<String, Object> echo(@RequestBody Map<String, Object> body) {
        return Map.of("received", body);
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "framework", "Bayu 0.1.0");
    }
}
