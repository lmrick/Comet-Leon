package com.cometproject.api.events.rooms.args;

import com.cometproject.api.events.EventArgs;
import com.cometproject.api.game.rooms.IRoom;
import com.cometproject.api.game.utilities.Position;
import com.cometproject.server.game.rooms.objects.entities.RoomEntity;
import com.cometproject.server.game.rooms.types.Room;

import java.util.List;

public class OnTileChangedEventArgs extends EventArgs {

    private final IRoom room;
    private final Position position;
    private final double oldHeight;
    private final double newHeight;
    private final List<IRoomEnt> entities;

    public OnTileChangedEventArgs(IRoom room,
                                  Position position,
                                  double oldHeight,
                                  double newHeight,
                                  List<RoomEntity> entities) {
        this.room = room;
        this.position = position;
        this.oldHeight = oldHeight;
        this.newHeight = newHeight;
        this.entities = entities;
    }

    public IRoom getRoom() {
        return room;
    }

    public Position getPosition() {
        return position;
    }

    public double getOldHeight() {
        return oldHeight;
    }

    public double getNewHeight() {
        return newHeight;
    }

    public List<RoomEntity> getEntities() {
        return entities;
    }
}