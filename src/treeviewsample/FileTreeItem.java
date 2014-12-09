package treeviewsample;

import java.io.File;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.RotateTransition;
import javafx.collections.ObservableList;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.scene.Node;
import javafx.scene.control.CheckBoxTreeItem;
import javafx.scene.control.TreeItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.util.Duration;

// ファイルツリーの根や分岐や葉を表すクラス
// 根は値を持たないものとする
@SuppressWarnings("restriction")
public class FileTreeItem extends CheckBoxTreeItem<ExtraProperty> implements EventHandler<Event>{
//	private static final boolean doAnimation = false;
	private static final boolean doAnimation = true;
	private final boolean isFile;
	private final CheckboxSelectionStateChangeListener handler;
	private final File file;

	//これはインスタンス間で共有できる。
	private static Image folderImage = new Image(FileTreeItem.class.getResourceAsStream("folder.png"));
	private static Image fileImage = new Image(FileTreeItem.class.getResourceAsStream("onpu.png"));
	private static Image convertImage = new Image(FileTreeItem.class.getResourceAsStream("converting.png"));
	private static Image succeedImage = new Image(FileTreeItem.class.getResourceAsStream("succeed.png"));
	private static Image failedImage = new Image(FileTreeItem.class.getResourceAsStream("failed.png"));

	//これはインスタンス毎に定義しないと、アイコンが表示されなかった。
	private final Node FOLDER_ICON = new ImageView(folderImage);
	private final Node INIT_ICON = new ImageView(fileImage);
	private final Node CONVERTING_ICON = new ImageView(convertImage);
	private final Node SUCCEED_ICON = new ImageView(succeedImage);
	private final Node FAILED_ICON = new ImageView(failedImage);

	public FileTreeItem() {
        this(null, null, false);
    }

	public FileTreeItem(CheckboxSelectionStateChangeListener handler, final File file, boolean isFile) {
		super(new ExtraProperty(file));

		super.addEventHandler(EventType.ROOT, this);

		this.handler = handler;
		this.file = file;
        this.isFile = isFile;

        if(isFile) {
        	super.setGraphic(INIT_ICON);
        } else {
        	super.setGraphic(FOLDER_ICON);
        }
    }

	public void removeEventHandler() {
		super.removeEventHandler(EventType.ROOT, this);
		//TODO チェックボックスの状態変更をできなくしたい。
		//super.setStyle("-fx-opacy: 1");
		//super.setEditable(false);
	}

    // 木の要素が葉かどうか(末端かどうか)を返す
    // 要素が持つ値は根の場合はnullで，
    // それ以外では通常のFileオブジェクトになる
    @Override
    public boolean isLeaf() {
    	return isFile;
    }

    @Override
    public ObservableList<TreeItem<ExtraProperty>> getChildren(){
        return super.getChildren();
    }

    @Override
    public String toString() {
    	return super.toString();
    }

    /**
     * チェックボックスの状態が変わった時に呼ばれる。
     */
	@Override
	public void handle(Event evt) {
		valueProperty().get().setSelected(isSelected());
//		System.out.println(FileTreeItem.class.getName() + " handle event fired");
		if(isFile) {
			handler.changeCheckboxSelectionState(this, file, isSelected());
		}
	}

	public void setStatusIconAnimated() {
		if(doAnimation) {
			RotateTransition rt = new RotateTransition(Duration.seconds(1), CONVERTING_ICON);
			rt.setByAngle(360);
			rt.setCycleCount(Animation.INDEFINITE);
			rt.setInterpolator(Interpolator.LINEAR);
			rt.play();
		}
	}

	public void setStatusIcon(MusicConvertStatus status) {
		switch(status) {
		case INIT:
			super.setGraphic(INIT_ICON);
			break;
		case CONVERTING:
			// アニメーションにしたい。
			//http://www.torutk.com/projects/swe/wiki/JavaFXとアナログ時計
			super.setGraphic(CONVERTING_ICON);
			break;
		case SUCCEED:
			super.setGraphic(SUCCEED_ICON);
			break;
		case FAILED:
			super.setGraphic(FAILED_ICON);
			break;
		}
	}
}
