/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 * Copyright (c) 2026 Virulent Client contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 *
 * Ported from Wurst Client TreeBot (https://github.com/Wurst-Imperium/Wurst7)
 * and adapted for Virulent Client.
 */
package dev.virulent.client.module.modules.player.treebot;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;

public final class Tree {
	private final BlockPos stump;
	private final ArrayList<BlockPos> logs;

	public Tree(BlockPos stump, ArrayList<BlockPos> logs) {
		this.stump = stump;
		this.logs = logs;
	}

	public BlockPos getStump() {
		return stump;
	}

	public ArrayList<BlockPos> getLogs() {
		return logs;
	}
}
