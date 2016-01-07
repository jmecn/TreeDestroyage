package net.mineguild.TreeDestroyage;

import com.google.inject.Inject;
import net.mineguild.TreeDestroyage.commands.SetConfigCommand;
import net.mineguild.TreeDestroyage.event.BreakBlockHandler;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import org.slf4j.Logger;
import org.spongepowered.api.Game;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.config.DefaultConfig;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.game.state.GameInitializationEvent;
import org.spongepowered.api.event.game.state.GameStartingServerEvent;
import org.spongepowered.api.event.game.state.GameStoppingServerEvent;
import org.spongepowered.api.item.ItemType;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.plugin.PluginContainer;
import org.spongepowered.api.text.Text;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

import static org.spongepowered.api.command.args.GenericArguments.*;

@Plugin(id = "TreeDestroyage", name = "TreeDestroyage", version = "0.5a")
public class TreeDestroyage {

    @Inject
    private PluginContainer container;

    @Inject
    @DefaultConfig(sharedRoot = true)
    private ConfigurationLoader<CommentedConfigurationNode> configManager;

    @Inject
    @DefaultConfig(sharedRoot = true)
    private File defaultConfig;

    @Inject
    private Logger logger;
    @Inject
    private Game game;
    private CommentedConfigurationNode config;

    public Game getGame() {
        return game;
    }

    @Listener
    public void onInitialization(GameInitializationEvent event) {
        Sponge.getGame().getEventManager().registerListeners(this, new BreakBlockHandler(this));

        CommandSpec setSpec = CommandSpec.builder().arguments(string(Text.of("setting")), optional(firstParsing(bool(Text.of("value")), integer(Text.of("value")), catalogedElement(Text.of("value"), ItemType.class)))).description(Text.of("Change config values on-the-fly")).executor(new SetConfigCommand(this))
                .permission("TreeDestroyage.set").build();

        CommandSpec reloadSpec = CommandSpec.builder().executor((src, args) -> {
                    try {
                        config = configManager.load();
                        src.sendMessage(Text.of("Config reloaded!"));
                        return CommandResult.success();
                    } catch (IOException e) {
                        src.sendMessage(Text.of("Config couldn't be reloaded!"));
                        getLogger().error("Couldn't re-load config", e);
                        return CommandResult.empty();
                    }
                }
        ).permission("TreeDestroyage.reload").build();
        CommandSpec mainSpec = CommandSpec.builder().child(setSpec, "set").child(reloadSpec, "reload").build();
        game.getCommandManager().register(this, mainSpec, "treedestroyage");

    }

    @Listener
    public void onServerStart(GameStartingServerEvent event) {
        config = null;
        try {
            if (!defaultConfig.exists()) {
                defaultConfig.createNewFile();
                config = configManager.load();
                config.getNode("version").setValue(1);
                configManager.save(config);
            }
            config = configManager.load();
            //Migrations here
            switch (config.getNode("version").getInt()) {
                case 1:
                    getLogger().info("Migrating from config version 1 to 2");
                    config.getNode("enabled").setComment("Enable/disable this plugin functionality").setValue(true);
                    config.getNode("maxBlocks").setComment("Maximum amount of blocks that will be destroyed in one hit").setValue(200);
                    config.getNode("version").setValue(2);
                case 2:
                    getLogger().info("Migrating from config version 2 to 3");
                    config.getNode("item").setComment("The item that needs to be held.").setValue("minecraft:golden_axe");
                    config.getNode("consumeItem").setComment("Whether to consume the item after usage. NOT WORKING YET").setValue(false);
                    config.getNode("version").setValue(3);
            }

            //Validation checks here
            Optional<ItemType> type = game.getRegistry().getType(ItemType.class, config.getNode("item").getString());
            if (!type.isPresent()) {
                getLogger().warn("ItemType item is not present! Resetting");
                config.getNode("item").setValue("minecraft:golden_axe");
            }
            configManager.save(config);
        } catch (IOException e) {
            getLogger().error("Unable to load/create config!", e);
        }
    }

    @Listener
    public void onServerStop(GameStoppingServerEvent event) {
        try {
            configManager.save(config);
        } catch (IOException e) {
            getLogger().error("Unable to save config!", e);
        }
    }

    public Logger getLogger() {
        return logger;
    }

    /**
     * Get the configuration. Only use after GameStartingServerEvent is over and config is initialized.
     *
     * @return config, if loaded
     */
    public CommentedConfigurationNode getConfig() {
        if (config != null) {
            return config;
        } else {
            getLogger().error("Config is not initialized!");
            return null;
        }
    }

    public ConfigurationLoader<CommentedConfigurationNode> getConfigManager() {
        return configManager;
    }
}
