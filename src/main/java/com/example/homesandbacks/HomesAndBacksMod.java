package com.example.homesandbacks;

import com.example.homesandbacks.command.HomeCommand;
import com.example.homesandbacks.data.HomeManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HomesAndBacksMod implements ModInitializer {

    public static final String MOD_ID = "homesandbacks";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            HomeCommand.register(dispatcher)
        );

        ServerLifecycleEvents.SERVER_STARTED.register(server -> HomeManager.INSTANCE.init(server));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> HomeManager.INSTANCE.save());

        LOGGER.info("HomesAndBacks loaded!");
    }
}
