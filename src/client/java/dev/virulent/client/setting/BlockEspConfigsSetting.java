package dev.virulent.client.setting;

import net.minecraft.world.level.block.Block;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class BlockEspConfigsSetting extends Setting<Map<Block, BlockEspConfig>> {
	public BlockEspConfigsSetting(String name) {
		super(name, Map.of());
	}

	public BlockEspConfig get(Block block) {
		return getValue().get(block);
	}

	public void put(Block block, BlockEspConfig config) {
		Map<Block, BlockEspConfig> next = new LinkedHashMap<>(getValue());
		next.put(block, config.copy());
		setValue(next);
	}

	public void remove(Block block) {
		if (!getValue().containsKey(block)) {
			return;
		}
		Map<Block, BlockEspConfig> next = new LinkedHashMap<>(getValue());
		next.remove(block);
		setValue(next);
	}

	public int size() {
		return getValue().size();
	}

	@Override
	public void setValue(Map<Block, BlockEspConfig> value) {
		Map<Block, BlockEspConfig> copy = new LinkedHashMap<>();
		if (value != null) {
			value.forEach((block, config) -> copy.put(block, config.copy()));
		}
		super.setValue(Collections.unmodifiableMap(copy));
	}
}
