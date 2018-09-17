package com.leo.sdk;

import com.leo.sdk.config.ConnectorConfig;
import com.leo.sdk.config.FileConfig;
import dagger.Module;
import dagger.Provides;

import javax.inject.Named;
import javax.inject.Singleton;

@Module
public class SDKModule {
    @Singleton
    @Provides
    public static ConnectorConfig provideConnectorConfig() {
        return new FileConfig();
    }

    @Singleton
    @Provides
    @Named("Internal")
    public static ExecutorManager provideExecutorManager(ConnectorConfig config) {
        return new InternalExecutorManager(config);
    }
}
