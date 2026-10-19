package com.seeker.collector.linkedin.collection;

class CollectionBlockedException extends RuntimeException {
    private final String url;

    CollectionBlockedException(String message) {
        this(message, (Throwable) null);
    }

    CollectionBlockedException(String message, Throwable cause) {
        super(message, cause);
        url = "";
    }

    CollectionBlockedException(String message, String url) {
        super(message + "\nURL: " + url);
        this.url = url;
    }

    String url() {
        return url;
    }
}

class CardClickException extends RuntimeException {
    CardClickException(String message) {
        super(message);
    }

    CardClickException(String message, Throwable cause) {
        super(message, cause);
    }
}

class DetailsNotReadyException extends RuntimeException {
    DetailsNotReadyException(String message) {
        super(message);
    }
}
