package treeviewsample;

import java.io.File;

import lombok.Getter;
import lombok.Setter;

public class ExtraProperty {
	@Getter
	@Setter
	private boolean isSelected;
	@Getter
	private final String name;

	public ExtraProperty(File file) {
		if(file != null) {
			this.name = file.getName();
		} else {
			name = null;
		}
	}

	@Override
	public String toString() {
		return name;
	}
}
