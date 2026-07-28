package dev.virulent.client.setting;

import net.minecraft.world.level.block.Block;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class BlockListSetting extends Setting<Set<Block>> {
	public BlockListSetting(String name, Block... defaults) {
		super(name, Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(defaults))));
	}

	public boolean contains(Block block) {
		return getValue().contains(block);
	}

	public int size() {
		return getValue().size();
	}

	public void toggle(Block block) {
		Set<Block> next = new LinkedHashSet<>(getValue());
		if (!next.add(block)) {
			next.remove(block);
		}
		setValue(Collections.unmodifiableSet(next));
	}

	public void clear() {
		setValue(Set.of());
	}

	@Override
	public void setValue(Set<Block> value) {
		super.setValue(Collections.unmodifiableSet(new LinkedHashSet<>(value)));
	}
}
