package com.cometproject.server.game.rooms.types.mapping;

import com.cometproject.api.modules.BaseModule;

public class RoomTileModule extends BaseModule {


    public RoomTileModule(ModuleConfig config, IGameService gameService) {
        super(config, gameService);

        this.registerEvent(new OnTileChangedEvent(this::onTileChanged));
    }

    private void onTileChanged(OnTileChangedEventArgs event) {
        if (event.getOldHeight() == event.getNewHeight()) {
            return;
        }

        var room = event.getRoom();
        var mapping = room.getMapping();
        var pos = event.getPosition();

        var tile = mapping.getTile(pos.getX(), pos.getY());
        if (tile == null) return;

        double correctHeight = tile.getWalkHeight();

        for (RoomEntity entity : event.getEntities()) {
            var currentZ = entity.getPosition().getZ();

            if (currentZ == correctHeight) {
                continue;
            }

            entity.getPosition().setZ(correctHeight);

            room.getEntities().broadcastMessage(
                new AvatarUpdateMessageComposer(entity)
            );
        }
    }

}