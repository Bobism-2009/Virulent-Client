package dev.virulent.client.module.modules.misc;

import dev.virulent.client.module.Category;
import dev.virulent.client.module.Module;
import dev.virulent.client.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.lwjgl.glfw.GLFW;

public final class AutoReconnect extends Module {
	private final NumberSetting delay = addSetting(new NumberSetting("Delay", 5.0, 1.0, 60.0, 1.0));
	private final NumberSetting maxAttempts = addSetting(new NumberSetting("Max Attempts", 0.0, 0.0, 50.0, 1.0));

	private ServerData lastServer;
	private Screen disconnectParent;
	private long reconnectAtMs;
	private int attempts;
	private boolean connecting;
	private boolean exhausted;

	public AutoReconnect() {
		super("AutoReconnect", "Rejoins the last server after a disconnect.", Category.MISC, GLFW.GLFW_KEY_UNKNOWN);
	}

	public static AutoReconnect get() {
		Module module = dev.virulent.client.VirulentClient.getInstance().getModuleManager().getModule("AutoReconnect");
		return module instanceof AutoReconnect autoReconnect ? autoReconnect : null;
	}

	public static boolean isActive() {
		AutoReconnect module = get();
		return module != null && module.isEnabled();
	}

	/** Seconds remaining before reconnect, or -1 if not counting down. */
	public int secondsRemaining() {
		if (!isEnabled() || reconnectAtMs <= 0 || lastServer == null || exhausted) {
			return -1;
		}
		long remaining = reconnectAtMs - System.currentTimeMillis();
		if (remaining <= 0) {
			return 0;
		}
		return (int) Math.ceil(remaining / 1000.0);
	}

	public boolean isExhausted() {
		return exhausted;
	}

	public String targetLabel() {
		if (lastServer == null) {
			return "";
		}
		String name = lastServer.name;
		if (name == null || name.isBlank()) {
			return lastServer.ip;
		}
		return name + " (" + lastServer.ip + ")";
	}

	public int getAttempts() {
		return attempts;
	}

	/**
	 * Called every client tick, including while disconnected.
	 */
	public void clientTick() {
		if (!isEnabled()) {
			resetCountdown();
			connecting = false;
			return;
		}

		Minecraft client = mc();
		rememberServer(client);

		Screen screen = client.screen;
		if (screen instanceof ConnectScreen) {
			// Stay quiet while a connect attempt is in flight.
			return;
		}

		if (screen instanceof DisconnectedScreen disconnected) {
			if (lastServer == null) {
				resetCountdown();
				connecting = false;
				return;
			}

			int max = maxAttempts.getValue().intValue();
			if (max > 0 && attempts >= max) {
				exhausted = true;
				reconnectAtMs = 0;
				connecting = false;
				return;
			}

			// Don't re-arm the timer if we already kicked off a reconnect this cycle.
			if (connecting) {
				return;
			}

			if (reconnectAtMs <= 0 && !exhausted) {
				disconnectParent = parentOf(disconnected);
				reconnectAtMs = System.currentTimeMillis() + (long) (delay.getValue() * 1000.0);
			}

			if (!exhausted && reconnectAtMs > 0 && System.currentTimeMillis() >= reconnectAtMs) {
				reconnect(client);
			}
			return;
		}

		// Left the disconnect / connect flow — abort countdown.
		resetCountdown();
		connecting = false;
	}

	private void rememberServer(Minecraft client) {
		if (client.level == null || client.getConnection() == null) {
			return;
		}
		ServerData current = client.getCurrentServer();
		if (current == null || current.isRealm()) {
			return;
		}
		lastServer = copyServer(current);
		attempts = 0;
		exhausted = false;
	}

	private void reconnect(Minecraft client) {
		if (lastServer == null) {
			return;
		}
		attempts++;
		reconnectAtMs = 0;
		connecting = true;

		ServerAddress address = ServerAddress.parseString(lastServer.ip);
		Screen parent = disconnectParent != null ? disconnectParent : client.screen;
		ConnectScreen.startConnecting(parent, client, address, lastServer, false, null);
	}

	private void resetCountdown() {
		reconnectAtMs = 0;
		disconnectParent = null;
		exhausted = false;
	}

	private static ServerData copyServer(ServerData source) {
		ServerData copy = new ServerData(source.name, source.ip, source.type());
		copy.copyFrom(source);
		return copy;
	}

	private static Screen parentOf(DisconnectedScreen screen) {
		return ((dev.virulent.client.mixin.DisconnectedScreenAccessor) (Object) screen).virulent$getParent();
	}

	@Override
	protected void onDisable() {
		resetCountdown();
		connecting = false;
		super.onDisable();
	}
}
