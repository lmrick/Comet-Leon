package com.cometproject.server.game.rooms.types.components;

import com.cometproject.api.config.CometSettings;
import com.cometproject.api.events.rooms.args.OnTileChangedEventArgs;
import com.cometproject.api.game.furniture.types.*;
import com.cometproject.api.game.players.data.components.inventory.PlayerItem;
import com.cometproject.api.game.rooms.objects.data.RoomItemData;
import com.cometproject.api.game.utilities.Position;
import com.cometproject.server.composers.catalog.UnseenItemsMessageComposer;
import com.cometproject.server.config.Locale;
import com.cometproject.server.game.items.ItemManager;
import com.cometproject.server.game.players.types.Player;
import com.cometproject.server.game.rooms.objects.entities.RoomEntity;
import com.cometproject.server.game.rooms.objects.entities.pathfinding.AffectedTile;
import com.cometproject.server.game.rooms.objects.items.*;
import com.cometproject.server.game.rooms.objects.items.types.floor.*;
import com.cometproject.server.game.rooms.objects.items.types.floor.games.GameTimerFloorItem;
import com.cometproject.server.game.rooms.objects.items.types.floor.wired.WiredFloorItem;
import com.cometproject.server.game.rooms.objects.items.types.floor.wired.highscore.HighscoreFloorItem;
import com.cometproject.server.game.rooms.objects.items.types.wall.MoodlightWallItem;
import com.cometproject.server.game.rooms.types.Room;
import com.cometproject.server.game.rooms.types.mapping.RoomTile;
import com.cometproject.server.modules.ModuleManager;
import com.cometproject.server.network.NetworkManager;
import com.cometproject.server.network.messages.outgoing.notification.NotificationMessageComposer;
import com.cometproject.server.network.messages.outgoing.room.engine.UpdateStackMapMessageComposer;
import com.cometproject.server.network.messages.outgoing.room.items.*;
import com.cometproject.server.network.messages.outgoing.user.inventory.UpdateInventoryMessageComposer;
import com.cometproject.server.network.sessions.Session;
import com.cometproject.server.storage.cache.objects.items.FloorItemDataObject;
import com.cometproject.server.storage.cache.objects.items.WallItemDataObject;
import com.cometproject.storage.api.StorageContext;
import com.cometproject.storage.api.data.Data;
import com.google.common.collect.*;
import org.apache.log4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ItemsComponent {

    private static final int MAX_FOOTBALLS = 15;

    private final Logger log;
    private final Map<Long, RoomItemFloor> floorItems = new ConcurrentHashMap<>();
    private final Map<Long, RoomItemWall> wallItems = new ConcurrentHashMap<>();
    private final Map<Integer, String> itemOwners = new ConcurrentHashMap<>();

    private final Room room;

    private final Map<Class<? extends RoomItemFloor>, Set<Long>> itemClassIndex = new ConcurrentHashMap<>();
    private final Map<String, Set<Long>> itemInteractionIndex = new ConcurrentHashMap<>();

    private long soundMachineId = 0;
    private long moodlightId;

    public ItemsComponent(Room room) {
        this.room = room;
        this.log = Logger.getLogger("Room Items Component [" + room.getData().getName() + "]");

        this.itemClassIndex.put(HighscoreFloorItem.class, Sets.newConcurrentHashSet());
        this.itemClassIndex.put(GameTimerFloorItem.class, Sets.newConcurrentHashSet());

        this.loadItems();
    }

   
    private void updateTile(Position pos) {
        var tile = room.getMapping().getTile(pos.getX(), pos.getY());
        if (tile == null) return;

        double oldHeight = tile.getWalkHeight();

        tile.reload();

        double newHeight = tile.getWalkHeight();

        var entities = new ArrayList<>(room.getEntities().getEntitiesAt(pos));

        ModuleManager.getInstance()
                .getEventHandler()
                .triggerEvent(new OnTileChangedEventArgs(
                        room,
                        pos,
                        oldHeight,
                        newHeight,
                        entities
                ));

        room.getEntities().broadcastMessage(new UpdateStackMapMessageComposer(tile));
    }


    private void loadItems() {
        if (room.getCachedData() != null) {
            for (FloorItemDataObject obj : room.getCachedData().getFloorItems()) {
                RoomItemData data = new RoomItemData(
                        obj.getId(),
                        obj.getItemDefinitionId(),
                        obj.getOwner(),
                        obj.getOwnerName(),
                        obj.getPosition(),
                        obj.getRotation(),
                        obj.getData(),
                        "",
                        obj.getLimitedEditionItemData()
                );

                floorItems.put(obj.getId(), RoomItemFactory.createFloor(
                        data, room,
                        ItemManager.getInstance().getDefinition(obj.getItemDefinitionId())));
            }

            for (WallItemDataObject obj : room.getCachedData().getWallItems()) {
                RoomItemData data = new RoomItemData(
                        obj.getId(),
                        obj.getItemDefinitionId(),
                        obj.getOwner(),
                        obj.getOwnerName(),
                        new Position(),
                        0,
                        obj.getData(),
                        obj.getWallPosition(),
                        obj.getLimitedEditionItemData()
                );

                wallItems.put(obj.getId(), RoomItemFactory.createWall(
                        data, room,
                        ItemManager.getInstance().getDefinition(obj.getItemDefinitionId())));
            }
        } else {
            Data<List<RoomItemData>> items = Data.createEmpty();

            StorageContext.getCurrentContext()
                    .getRoomItemRepository()
                    .getItemsByRoomId(room.getId(), items::set);

            if (items.has()) {
                for (RoomItemData item : items.get()) {
                    var def = ItemManager.getInstance().getDefinition(item.getItemId());
                    if (def == null) continue;

                    if (def.getItemType() == ItemType.FLOOR)
                        floorItems.put(item.getId(), RoomItemFactory.createFloor(item, room, def));
                    else
                        wallItems.put(item.getId(), RoomItemFactory.createWall(item, room, def));
                }
            }
        }

        indexItems();
    }

    private void indexItems() {
        for (RoomItemFloor item : floorItems.values()) {
            if (item instanceof SoundMachineFloorItem) {
                soundMachineId = item.getId();
            }

            indexItem(item);
        }
    }

    private void indexItem(RoomItemFloor item) {
        itemOwners.put(item.getItemData().getOwnerId(), item.getItemData().getOwnerName());

        itemClassIndex.computeIfAbsent(item.getClass(), k -> new HashSet<>()).add(item.getId());
        itemInteractionIndex.computeIfAbsent(item.getDefinition().getInteraction(), k -> new HashSet<>()).add(item.getId());
    }

    public void removeItem(RoomItemFloor item, Session session, boolean toInventory, boolean delete) {

        List<Position> tiles = new ArrayList<>();

        tiles.add(new Position(item.getPosition().getX(), item.getPosition().getY(), 0));

        for (RoomEntity entity : room.getEntities().getEntitiesAt(item.getPosition())) {
            item.onEntityStepOff(entity);
        }

        for (AffectedTile t : AffectedTile.getAffectedTilesAt(
                item.getDefinition().getLength(),
                item.getDefinition().getWidth(),
                item.getPosition().getX(),
                item.getPosition().getY(),
                item.getRotation())) {

            var pos = new Position(t.x, t.y, 0);
            tiles.add(pos);

            for (RoomEntity e : room.getEntities().getEntitiesAt(pos)) {
                item.onEntityStepOff(e);
            }
        }

        int owner = item.getItemData().getOwnerId();

        room.getEntities().broadcastMessage(
                new RemoveFloorItemMessageComposer(item.getVirtualId(), owner));

        floorItems.remove(item.getId());

        StorageContext.getCurrentContext()
                .getRoomItemRepository()
                .removeItemFromRoom(item.getId(), owner, item.getDataObject());

        if (toInventory && session != null) {
            session.getPlayer().getInventory().add(
                    item.getId(),
                    item.getItemData().getItemId(),
                    item.getItemData().getData(),
                    null,
                    item.getLimitedEditionItemData()
            );

            session.sendQueue(new UpdateInventoryMessageComposer());
            session.flush();
        }

        for (var pos : tiles) {
            updateTile(pos);
        }
    }

    public boolean moveFloorItem(long itemId, Position newPos, int rot) {
        var item = floorItems.get(itemId);
        if (item == null) return false;

        Position oldPos = new Position(
                item.getPosition().getX(),
                item.getPosition().getY(),
                item.getPosition().getZ()
        );

        item.getPosition().setX(newPos.getX());
        item.getPosition().setY(newPos.getY());
        item.getItemData().setRotation(rot);

        updateTile(oldPos);
        updateTile(newPos);

        return true;
    }

    public Map<Long, RoomItemFloor> getFloorItems() {
        return floorItems;
    }

    public Map<Long, RoomItemWall> getWallItems() {
        return wallItems;
    }

    public Room getRoom() {
        return room;
    }
}