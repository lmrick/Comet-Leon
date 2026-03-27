package com.cometproject.api.events.rooms;

import com.cometproject.api.events.Event;
import com.cometproject.api.events.rooms.args.OnTileChangedEventArgs;

import java.util.function.Consumer;

public class OnTileChangedEvent extends Event<OnTileChangedEventArgs> {

    public OnTileChangedEvent(Consumer<OnTileChangedEventArgs> consumer) {
        super(consumer);
    }

    @Override
    public boolean isAsync() {
        return false; // importante → sincronizado com o engine
    }

}