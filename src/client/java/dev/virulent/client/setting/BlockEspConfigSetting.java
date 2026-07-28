package dev.virulent.client.setting;

public final class BlockEspConfigSetting extends Setting<BlockEspConfig> {
	public BlockEspConfigSetting(String name, BlockEspConfig defaultValue) {
		super(name, defaultValue.copy());
	}

	@Override
	public void setValue(BlockEspConfig value) {
		super.setValue(value == null ? BlockEspConfig.defaults() : value.copy());
	}

	@Override
	public void reset() {
		setValue(getDefaultValue().copy());
	}
}
