package com.seeker.guifx;

import com.seeker.api.DriverApi;
import com.seeker.collector.linkedin.collection.CollectionProgress;
import com.seeker.collector.linkedin.collection.CollectionReport;

import java.util.Objects;
import java.util.function.Consumer;

final class DirectCollectorClient implements CollectorClient {
    private final DriverApi driverApi;

    DirectCollectorClient(DriverApi driverApi) {
        this.driverApi = Objects.requireNonNull(driverApi);
    }

    @Override
    public CollectionReport collect(Consumer<CollectionProgress> progress) throws Exception {
        return driverApi.collectBatch(progress);
    }
}
