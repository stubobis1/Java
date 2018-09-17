package com.leo.sdk;

import java.util.concurrent.Executor;

public interface ExecutorManager {
    Executor get();

    void add(Runnable r);

    void end();
}
