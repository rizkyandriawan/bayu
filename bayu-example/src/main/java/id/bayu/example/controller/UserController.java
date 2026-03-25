package id.bayu.example.controller;

import id.bayu.core.annotation.Autowired;
import id.bayu.example.model.User;
import id.bayu.example.service.UserService;
import id.bayu.web.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    public void setUserService(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<User> findAll() {
        return userService.findAll();
    }

    @GetMapping("/{id}")
    public Object findById(@PathVariable Long id) {
        return userService.findById(id)
                .orElse(null);
    }

    @PostMapping
    @ResponseStatus(201)
    public User create(@RequestBody User user) {
        return userService.save(user);
    }

    @PutMapping("/{id}")
    public Object update(@PathVariable Long id, @RequestBody User user) {
        if (!userService.findById(id).isPresent()) {
            return Map.of("error", "User not found");
        }
        user.setId(id);
        return userService.save(user);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(204)
    public void delete(@PathVariable Long id) {
        userService.deleteById(id);
    }

    @GetMapping("/search/by-name")
    public List<User> findByName(@RequestParam("name") String name) {
        return userService.findByName(name);
    }

    @GetMapping("/search/by-email")
    public Object findByEmail(@RequestParam("email") String email) {
        return userService.findByEmail(email).orElse(null);
    }
}
