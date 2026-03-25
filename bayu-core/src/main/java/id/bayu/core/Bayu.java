package id.bayu.core;

import java.util.ServiceLoader;

public final class Bayu {

    private Bayu() {}

    public static ApplicationContext run(Class<?> mainClass, String... args) {
        ServiceLoader<ApplicationContext> loader = ServiceLoader.load(ApplicationContext.class);
        ApplicationContext context = loader.findFirst()
                .orElseThrow(() -> new RuntimeException(
                        "No BayuGeneratedContext found. Did you add bayu-processor to your annotation processor path?"));

        context.initialize(args);

        String appName = mainClass.getSimpleName();
        System.out.println("""

                  ____                    \s
                 |  _ \\                   \s
                 | |_) | __ _ _   _ _   _ \s
                 |  _ < / _` | | | | | | |\s
                 | |_) | (_| | |_| | |_| |\s
                 |____/ \\__,_|\\__, |\\__,_|\s
                               __/ |      \s
                              |___/       \s
                """ + " :: " + appName + " :: Bayu Framework 0.1.0\n");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down " + appName + "...");
            context.close();
        }));

        return context;
    }
}
