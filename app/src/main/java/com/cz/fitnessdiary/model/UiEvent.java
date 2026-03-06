package com.cz.fitnessdiary.model;

public class UiEvent<T> {
    private final T content;
    private boolean handled;

    public UiEvent(T content) {
        this.content = content;
        this.handled = false;
    }

    public T getContentIfNotHandled() {
        if (handled) {
            return null;
        }
        handled = true;
        return content;
    }
}

