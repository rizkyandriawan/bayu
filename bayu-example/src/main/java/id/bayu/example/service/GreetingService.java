package id.bayu.example.service;

import id.bayu.core.annotation.Service;
import id.bayu.core.annotation.Value;

@Service
public class GreetingService {

    @Value("${app.greeting:Hello}")
    private String greeting;

    public void setGreeting(String greeting) {
        this.greeting = greeting;
    }

    public String greet(String name) {
        return greeting + ", " + name + "!";
    }

    public String getGreeting() {
        return greeting;
    }
}
