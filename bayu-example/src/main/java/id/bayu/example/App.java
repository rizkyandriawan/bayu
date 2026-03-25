package id.bayu.example;

import id.bayu.core.Bayu;
import id.bayu.core.annotation.BayuApplication;

@BayuApplication
public class App {
    public static void main(String[] args) {
        Bayu.run(App.class, args);
    }
}
