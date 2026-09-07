package com.seeker.collector.linkedin.collection;

class CollectionBlockedException extends RuntimeException {
    CollectionBlockedException(String message) {
        super(message);
    }

    CollectionBlockedException(String message, Throwable cause) {
        super(message, cause);
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
