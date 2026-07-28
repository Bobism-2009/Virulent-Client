/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 * Copyright (c) 2026 Virulent Client contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 *
 * Ported from Wurst Client TreeBotUtils (https://github.com/Wurst-Imperium/Wurst7)
 * and adapted for Virulent Client.
 */
package dev.virulent.client.module.modules.player.treebot;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;

public final class TreeBotUtils {
	private TreeBotUtils() {
	}

	public static boolean isLog(BlockPos pos) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) {
			return false;
		}
		return client.level.getBlockState(pos).is(BlockTags.LOGS);
	}

	public static boolean isLeaves(BlockPos pos) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) {
			return false;
		}
		BlockState state = client.level.getBlockState(pos);
		return state.is(BlockTags.LEAVES) || state.is(BlockTags.WART_BLOCKS);
	}
}
