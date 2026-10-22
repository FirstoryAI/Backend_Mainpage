package com.firstory.global.storage;

import java.util.Objects;

public record StoredFile(byte[] bytes, String contentType) {

    public StoredFile {
        Objects.requireNonNull(bytes);
        Objects.requireNonNull(contentType);
    }

    @Override
    public String toString() {
        return "StoredFile[" + contentType + ", " + bytes.length + " bytes]";
    }
}
