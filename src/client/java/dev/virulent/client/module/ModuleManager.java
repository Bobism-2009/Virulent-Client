package dev.virulent.client.module;

import dev.virulent.client.event.events.KeyEvent;
import dev.virulent.client.gui.clickgui.ClickGuiScreen;
import dev.virulent.client.module.modules.combat.AutoClicker;
import dev.virulent.client.module.modules.combat.AutoTotem;
import dev.virulent.client.module.modules.combat.KillAura;
import dev.virulent.client.module.modules.combat.MaceKill;
import dev.virulent.client.module.modules.combat.TriggerBot;
import dev.virulent.client.module.modules.combat.Velocity;
import dev.virulent.client.module.modules.misc.AutoReconnect;
import dev.virulent.client.module.modules.misc.ChatFeedback;
import dev.virulent.client.module.modules.misc.Freecam;
import dev.virulent.client.module.modules.misc.Friends;
import dev.virulent.client.module.modules.misc.Panic;
import dev.virulent.client.module.modules.misc.SeedCracker;
import dev.virulent.client.module.modules.misc.Teleport;
import dev.virulent.client.module.modules.misc.Zoom;
import dev.virulent.client.module.modules.movement.AirJump;
import dev.virulent.client.module.modules.movement.BoatFly;
import dev.virulent.client.module.modules.movement.Flight;
import dev.virulent.client.module.modules.movement.Jesus;
import dev.virulent.client.module.modules.movement.NoClip;
import dev.virulent.client.module.modules.movement.NoFall;
import dev.virulent.client.module.modules.movement.NoSlow;
import dev.virulent.client.module.modules.movement.SafeWalk;
import dev.virulent.client.module.modules.movement.Speed;
import dev.virulent.client.module.modules.movement.Sprint;
import dev.virulent.client.module.modules.movement.Step;
import dev.virulent.client.module.modules.performance.FpsBooster;
import dev.virulent.client.module.modules.performance.FpsHud;
import dev.virulent.client.module.modules.player.AntiHunger;
import dev.virulent.client.module.modules.player.AutoTool;
import dev.virulent.client.module.modules.player.FastBreak;
import dev.virulent.client.module.modules.player.FastPlace;
import dev.virulent.client.module.modules.player.MultiTask;
import dev.virulent.client.module.modules.player.NoInteract;
import dev.virulent.client.module.modules.player.Scaffold;
import dev.virulent.client.module.modules.player.TreeBot;
import dev.virulent.client.module.modules.player.Tunneler;
import dev.virulent.client.module.modules.render.ArmorHud;
import dev.virulent.client.module.modules.render.BaseFinder;
import dev.virulent.client.module.modules.render.BlockEsp;
import dev.virulent.client.module.modules.render.ESP;
import dev.virulent.client.module.modules.render.Fullbright;
import dev.virulent.client.module.modules.render.HandView;
import dev.virulent.client.module.modules.render.ItemEsp;
import dev.virulent.client.module.modules.render.Nametags;
import dev.virulent.client.module.modules.render.NoFire;
import dev.virulent.client.module.modules.render.NoHurtCam;
import dev.virulent.client.module.modules.render.NoWeather;
import dev.virulent.client.module.modules.render.Tracers;
import dev.virulent.client.module.modules.render.WallHack;
import dev.virulent.client.module.modules.render.Waypoints;
import dev.virulent.client.module.modules.render.Xray;
import dev.virulent.client.input.ClientKeybinds;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ModuleManager {
	private final List<Module> modules = new ArrayList<>();
	private boolean suppressToggleFeedback;

	public void init() {
		register(
			new KillAura(),
			new MaceKill(),
			new Velocity(),
			new TriggerBot(),
			new AutoClicker(),
			new AutoTotem(),
			new Sprint(),
			new Flight(),
			new NoClip(),
			new BoatFly(),
			new Speed(),
			new Step(),
			new AirJump(),
			new NoFall(),
			new Jesus(),
			new NoSlow(),
			new SafeWalk(),
			new Fullbright(),
			new ESP(),
			new ItemEsp(),
			new Nametags(),
			new Tracers(),
			new BaseFinder(),
			new BlockEsp(),
			new Waypoints(),
			new WallHack(),
			new ArmorHud(),
			new HandView(),
			new NoHurtCam(),
			new NoFire(),
			new NoWeather(),
			new Xray(),
			new FastPlace(),
			new FastBreak(),
			new MultiTask(),
			new AutoTool(),
			new AntiHunger(),
			new Scaffold(),
			new TreeBot(),
			new Tunneler(),
			new NoInteract(),
			new Zoom(),
			new Freecam(),
			new Teleport(),
			new AutoReconnect(),
			new SeedCracker(),
			new Friends(),
			new ChatFeedback(),
			new FpsHud(),
			new FpsBooster(),
			new Panic()
		);
	}

	private void register(Module... toRegister) {
		Collections.addAll(modules, toRegister);
	}

	public List<Module> getModules() {
		return Collections.unmodifiableList(modules);
	}

	public List<Module> getModulesByCategory(Category category) {
		return modules.stream().filter(module -> module.getCategory() == category).toList();
	}

	public Module getModule(String name) {
		for (Module module : modules) {
			if (module.getName().equalsIgnoreCase(name)) {
				return module;
			}
		}
		return null;
	}

	public void disableAll(Module except) {
		suppressToggleFeedback = true;
		try {
			for (Module module : modules) {
				if (module == except) {
					continue;
				}

				if (module instanceof Zoom zoom && zoom.isZooming()) {
					zoom.stopZoom();
				}

				if (module.isEnabled()) {
					module.setEnabled(false);
				}
			}
		} finally {
			suppressToggleFeedback = false;
		}
	}

	public boolean isSuppressingToggleFeedback() {
		return suppressToggleFeedback;
	}

	public void onTick() {
		for (Module module : modules) {
			if (module.isEnabled()) {
				module.onTick();
			}
		}
	}

	public boolean hasEnabledModules() {
		for (Module module : modules) {
			if (module.isEnabled()) {
				return true;
			}
		}
		return false;
	}

	public boolean needsWorldRender() {
		Module esp = getModule("ESP");
		Module itemEsp = getModule("ItemESP");
		Module xray = getModule("Xray");
		Module baseFinder = getModule("BaseFinder");
		Module blockEsp = getModule("BlockESP");
		Module waypoints = getModule("Waypoints");
		Module seedCracker = getModule("SeedCracker");
		Module treeBot = getModule("TreeBot");
		Module tunneler = getModule("Tunneler");
		return (esp instanceof ESP espModule && espModule.needsWorldRender())
			|| (itemEsp instanceof ItemEsp itemEspModule && itemEspModule.needsWorldRender())
			|| (xray != null && xray.isEnabled())
			|| (baseFinder != null && baseFinder.isEnabled())
			|| (blockEsp != null && blockEsp.isEnabled())
			|| (waypoints != null && waypoints.isEnabled())
			|| (seedCracker != null && seedCracker.isEnabled())
			|| (treeBot != null && treeBot.isEnabled())
			|| (tunneler != null && tunneler.isEnabled());
	}

	public void onKey(KeyEvent event) {
		Minecraft client = Minecraft.getInstance();
		if (client.screen instanceof ClickGuiScreen clickGui && clickGui.isBinding()) {
			return;
		}

		Module zoomModule = getModule("Zoom");
		if (zoomModule instanceof Zoom zoom && zoom.getKeyBind() != GLFW.GLFW_KEY_UNKNOWN && event.getKey() == zoom.getKeyBind()) {
			// Hold-zoom release must still work if a screen opens mid-hold.
			if (zoom.isHoldMode()) {
				if (event.isReleased()) {
					zoom.stopZoom();
					return;
				}
				if (event.isPressed() && client.screen == null) {
					zoom.startZoom();
					return;
				}
				return;
			}
		}

		if (!event.isPressed()) {
			return;
		}

		if (event.getKey() == ClientKeybinds.clickGuiKey()) {
			dev.virulent.client.VirulentClient.getInstance().getClickGui().toggle();
			return;
		}

		// Module keybinds should not fire while any UI/screen is open.
		if (client.screen != null) {
			return;
		}

		for (Module module : modules) {
			if (module.getKeyBind() == event.getKey()) {
				module.toggle();
			}
		}
	}
}
