package com.example.homesandbacks.data;

import com.example.homesandbacks.HomesAndBacksMod;
import com.google.gson.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class HomeManager {

    public static final HomeManager INSTANCE = new HomeManager();

    // player UUID -> world key -> home name -> location
    private final Map<UUID, Map<String, Map<String, HomeLocation>>> homes = new HashMap<>();
    private final Map<UUID, HomeLocation> backs = new HashMap<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private Path dataFile;

    private HomeManager() {}

    public void init(MinecraftServer server) {
        homes.clear();
        backs.clear();
        this.dataFile = server.getSavePath(WorldSavePath.ROOT).resolve("homesandbacks.json");
        load();
    }

    public void setHome(UUID playerId, String worldKey, String name, HomeLocation location) {
        homes.computeIfAbsent(playerId, k -> new HashMap<>())
             .computeIfAbsent(worldKey, k -> new HashMap<>())
             .put(name.toLowerCase(), location);
        save();
    }

    public Optional<HomeLocation> getHome(UUID playerId, String worldKey, String name) {
        Map<String, Map<String, HomeLocation>> playerHomes = homes.get(playerId);
        if (playerHomes == null) return Optional.empty();
        Map<String, HomeLocation> worldHomes = playerHomes.get(worldKey);
        if (worldHomes == null) return Optional.empty();
        return Optional.ofNullable(worldHomes.get(name.toLowerCase()));
    }

    public Map<String, HomeLocation> getHomes(UUID playerId, String worldKey) {
        Map<String, Map<String, HomeLocation>> playerHomes = homes.get(playerId);
        if (playerHomes == null) return Collections.emptyMap();
        return playerHomes.getOrDefault(worldKey, Collections.emptyMap());
    }

    public boolean deleteHome(UUID playerId, String worldKey, String name) {
        Map<String, Map<String, HomeLocation>> playerHomes = homes.get(playerId);
        if (playerHomes == null) return false;
        Map<String, HomeLocation> worldHomes = playerHomes.get(worldKey);
        if (worldHomes == null) return false;
        boolean removed = worldHomes.remove(name.toLowerCase()) != null;
        if (removed) save();
        return removed;
    }

    public void setBack(UUID playerId, HomeLocation location) {
        backs.put(playerId, location);
    }

    public Optional<HomeLocation> getBack(UUID playerId) {
        return Optional.ofNullable(backs.get(playerId));
    }

    private void load() {
        if (!Files.exists(dataFile)) return;
        try (Reader reader = Files.newBufferedReader(dataFile)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

            if (root.has("homes")) {
                for (Map.Entry<String, JsonElement> playerEntry : root.getAsJsonObject("homes").entrySet()) {
                    UUID playerId = UUID.fromString(playerEntry.getKey());
                    Map<String, Map<String, HomeLocation>> playerHomes = new HashMap<>();
                    for (Map.Entry<String, JsonElement> worldEntry : playerEntry.getValue().getAsJsonObject().entrySet()) {
                        Map<String, HomeLocation> worldHomes = new HashMap<>();
                        for (Map.Entry<String, JsonElement> homeEntry : worldEntry.getValue().getAsJsonObject().entrySet()) {
                            worldHomes.put(homeEntry.getKey(), parseLocation(homeEntry.getValue().getAsJsonObject()));
                        }
                        playerHomes.put(worldEntry.getKey(), worldHomes);
                    }
                    homes.put(playerId, playerHomes);
                }
            }

            if (root.has("backs")) {
                for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("backs").entrySet()) {
                    backs.put(UUID.fromString(entry.getKey()), parseLocation(entry.getValue().getAsJsonObject()));
                }
            }
        } catch (Exception e) {
            HomesAndBacksMod.LOGGER.error("Failed to load HomesAndBacks data", e);
        }
    }

    public void save() {
        if (dataFile == null) return;
        try {
            Files.createDirectories(dataFile.getParent());

            JsonObject root = new JsonObject();

            JsonObject homesObj = new JsonObject();
            for (Map.Entry<UUID, Map<String, Map<String, HomeLocation>>> playerEntry : homes.entrySet()) {
                JsonObject playerObj = new JsonObject();
                for (Map.Entry<String, Map<String, HomeLocation>> worldEntry : playerEntry.getValue().entrySet()) {
                    JsonObject worldObj = new JsonObject();
                    for (Map.Entry<String, HomeLocation> homeEntry : worldEntry.getValue().entrySet()) {
                        worldObj.add(homeEntry.getKey(), locationToJson(homeEntry.getValue()));
                    }
                    playerObj.add(worldEntry.getKey(), worldObj);
                }
                homesObj.add(playerEntry.getKey().toString(), playerObj);
            }
            root.add("homes", homesObj);

            JsonObject backsObj = new JsonObject();
            for (Map.Entry<UUID, HomeLocation> entry : backs.entrySet()) {
                backsObj.add(entry.getKey().toString(), locationToJson(entry.getValue()));
            }
            root.add("backs", backsObj);

            try (Writer writer = Files.newBufferedWriter(dataFile)) {
                gson.toJson(root, writer);
            }
        } catch (Exception e) {
            HomesAndBacksMod.LOGGER.error("Failed to save HomesAndBacks data", e);
        }
    }

    private HomeLocation parseLocation(JsonObject obj) {
        return new HomeLocation(
            obj.get("world").getAsString(),
            obj.get("x").getAsDouble(),
            obj.get("y").getAsDouble(),
            obj.get("z").getAsDouble(),
            obj.get("yaw").getAsFloat(),
            obj.get("pitch").getAsFloat()
        );
    }

    private JsonObject locationToJson(HomeLocation loc) {
        JsonObject obj = new JsonObject();
        obj.addProperty("world", loc.world);
        obj.addProperty("x", loc.x);
        obj.addProperty("y", loc.y);
        obj.addProperty("z", loc.z);
        obj.addProperty("yaw", loc.yaw);
        obj.addProperty("pitch", loc.pitch);
        return obj;
    }
}
