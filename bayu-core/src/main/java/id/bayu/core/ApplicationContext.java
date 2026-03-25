package id.bayu.core;

public interface ApplicationContext extends AutoCloseable {

    void initialize(String[] args);

    <T> T getBean(Class<T> type);

    <T> T getBean(String name, Class<T> type);

    @Override
    void close();
}
