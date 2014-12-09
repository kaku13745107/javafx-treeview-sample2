package treeviewsample;

import java.io.File;

public interface CheckboxSelectionStateChangeListener {

	void changeCheckboxSelectionState(FileTreeItem item, File file, boolean selected);

}
