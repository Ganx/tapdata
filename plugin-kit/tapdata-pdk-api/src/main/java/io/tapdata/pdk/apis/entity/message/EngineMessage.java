package io.tapdata.pdk.apis.entity.message;

public abstract class EngineMessage {
	protected String id;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public abstract String key();
}
