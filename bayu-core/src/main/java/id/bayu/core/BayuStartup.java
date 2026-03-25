package id.bayu.core;

/**
 * Implement this interface on a @Component to run code after all beans are initialized.
 * Called before the HTTP server starts.
 */
public interface BayuStartup {
    void onStartup();
}
