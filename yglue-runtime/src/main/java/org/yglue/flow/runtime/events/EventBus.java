package org.yglue.flow.runtime.events;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class EventBus {

    private final List<Consumer<Object>> listeners = new ArrayList<>();

    public void publish(Object event) {
        for (Consumer<Object> listener : listeners) {
            try {
                listener.accept(event);
            } catch (Exception ignored) {
            }
        }
    }

    public void register(Consumer<Object> listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public static EventBus noop() {
        return new EventBus();
    }
}
