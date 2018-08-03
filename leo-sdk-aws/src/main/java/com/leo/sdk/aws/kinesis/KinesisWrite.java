package com.leo.sdk.aws.kinesis;

import com.amazonaws.services.kinesis.producer.KinesisProducer;
import com.amazonaws.services.kinesis.producer.KinesisProducerConfiguration;
import com.amazonaws.services.kinesis.producer.UserRecordResult;
import com.leo.sdk.StreamStats;
import com.leo.sdk.aws.payload.PayloadIdentifier;
import com.leo.sdk.config.ConnectorConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.AbstractMap.SimpleEntry;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import static com.amazonaws.services.kinesis.producer.KinesisProducerConfiguration.ThreadingModel.POOLED;
import static java.util.concurrent.TimeUnit.MINUTES;

public class KinesisWrite {
    private static final Logger log = LoggerFactory.getLogger(KinesisWrite.class);
    private final KinesisResults resultsProcessor;
    private final KinesisProducer kinesis;
    private final String stream;
    private final ExecutorService asyncComplete = Executors.newWorkStealingPool();

    @Inject
    public KinesisWrite(ConnectorConfig config, KinesisResults resultsProcessor) {
        this.stream = config.value("Stream.Name");
        this.resultsProcessor = resultsProcessor;
        KinesisProducerConfiguration kCfg = new KinesisProducerConfiguration()
                .setRegion(config.valueOrElse("Region", "us-east-1"))
                .setRecordMaxBufferedTime(config.longValueOrElse("Stream.MaxBatchAge", 200L))
                .setCollectionMaxCount(config.longValueOrElse("Stream.MaxBatchRecords", 500L))
                .setRequestTimeout(60000)
                .setMaxConnections(48)
                .setMetricsNamespace("LEO Java SDK")
                .setThreadingModel(POOLED)
                .setThreadPoolSize(128);
        this.kinesis = new KinesisProducer(kCfg);
    }

    void write(PayloadIdentifier payload) {
        CompletableFuture
                .supplyAsync(() -> addRecord(payload), asyncComplete)
                .whenComplete(processResult());
    }

    StreamStats end() {
        asyncComplete.shutdown();
        try {
            kinesis.flushSync();
            if (!asyncComplete.awaitTermination(4L, MINUTES)) {
                asyncComplete.shutdownNow();
            }
            kinesis.destroy();
        } catch (InterruptedException e) {
            log.warn("Could not shutdown async writer pool");
        }
        return getStats();
    }

    private Entry<String, UserRecordResult> addRecord(PayloadIdentifier payload) {
        try {
            UserRecordResult result = kinesis.addUserRecord(stream, "0", payload.getPayload()).get();
            return new SimpleEntry<>(payload.getId(), result);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private BiConsumer<Entry<String, UserRecordResult>, Throwable> processResult() {
        return (result, t) -> {
            if (success(t)) {
                resultsProcessor.addSuccess(result.getKey(), result.getValue());
            } else {
                resultsProcessor.addFailure(result.getKey(), t);
            }
        };
    }

    private StreamStats getStats() {
        return new StreamStats() {
            @Override
            public Stream<String> successIds() {
                return resultsProcessor.successes();
            }

            @Override
            public Stream<String> failedIds() {
                return resultsProcessor.failures();
            }

            @Override
            public Duration totalTime() {
                return Duration.between(resultsProcessor.start(), Instant.now());
            }
        };
    }

    private boolean success(Throwable throwable) {
        return !Optional.ofNullable(throwable).isPresent();
    }
}
