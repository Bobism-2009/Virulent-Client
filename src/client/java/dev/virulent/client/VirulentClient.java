package dev.virulent.client;

import dev.virulent.client.config.ConfigManager;
import dev.virulent.client.config.GuiSettings;
import dev.virulent.client.event.EventBus;
import dev.virulent.client.event.events.Render2DEvent;
import dev.virulent.client.event.events.Render3DEvent;
import dev.virulent.client.event.events.TickEvent;
import dev.virulent.client.friend.FriendsManager;
import dev.virulent.client.gui.clickgui.ClickGuiScreen;
import dev.virulent.client.gui.hud.HudRenderer;
import dev.virulent.client.input.ClientKeybinds;
import dev.virulent.client.module.Module;
import dev.virulent.client.module.ModuleManager;
import dev.virulent.client.module.modules.misc.AutoReconnect;
import dev.virulent.client.seed.SeedState;
import dev.virulent.client.waypoint.WaypointManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class VirulentClient implements ClientModInitializer {
	public static final String MOD_ID = "virulent";
	public static final String NAME = "Virulent Client";
	public static final String VERSION = "1.10.7";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static VirulentClient instance;

	private final EventBus eventBus = new EventBus();
	private final ModuleManager moduleManager = new ModuleManager();
	private final ConfigManager configManager = new ConfigManager();
	private final GuiSettings guiSettings = new GuiSettings();
	private final WaypointManager waypointManager = new WaypointManager();
	private final FriendsManager friendsManager = new FriendsManager();
	private final ClickGuiScreen clickGui = new ClickGuiScreen(guiSettings);
	private HudRenderer hudRenderer;

	public static VirulentClient getInstance() {
		return instance;
	}

	public EventBus getEventBus() {
		return eventBus;
	}

	public ModuleManager getModuleManager() {
		return moduleManager;
	}

	public ConfigManager getConfigManager() {
		return configManager;
	}

	public ClickGuiScreen getClickGui() {
		return clickGui;
	}

	public GuiSettings getGuiSettings() {
		return guiSettings;
	}

	public WaypointManager getWaypointManager() {
		return waypointManager;
	}

	public FriendsManager getFriendsManager() {
		return friendsManager;
	}

	@Override
	public void onInitializeClient() {
		instance = this;
		guiSettings.load();
		waypointManager.load();
		friendsManager.load();
		SeedState.get().load();
		moduleManager.init();
		ClientKeybinds.register();
		configManager.load();
		registerEvents();
		hudRenderer = new HudRenderer(eventBus, guiSettings);
		LOGGER.info("{} v{} initialized", NAME, VERSION);
	}

	private void registerEvents() {
		// START so movement/attack key presses apply before KeyboardInput + continueAttack this tick.
		ClientTickEvents.START_CLIENT_TICK.register(client -> {
			if (client.player == null) {
				return;
			}
			moduleManager.onTick();
			eventBus.post(new TickEvent());
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			configManager.tick();
			guiSettings.tick();
			waypointManager.tick();
			friendsManager.tick();
			if (hudRenderer != null) {
				hudRenderer.tick();
			}

			Module autoReconnect = moduleManager.getModule("AutoReconnect");
			if (autoReconnect instanceof AutoReconnect module) {
				module.clientTick();
			}
		});

		// BEFORE_GIZMOS has a prepared LevelRenderContext and runs before gizmo collection finalizes.
		LevelRenderEvents.BEFORE_GIZMOS.register(context -> {
			if (!moduleManager.needsWorldRender()) {
				return;
			}

			eventBus.post(new Render3DEvent(context));
		});

		HudElementRegistry.attachElementAfter(
			VanillaHudElements.MISC_OVERLAYS,
			Identifier.fromNamespaceAndPath(MOD_ID, "module_hud"),
			(graphics, tickCounter) -> eventBus.post(new Render2DEvent(graphics, tickCounter))
		);
	}
}
