package treeviewsample.demo;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.Semaphore;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.cell.CheckBoxTreeCell;
import javafx.stage.Stage;
import treeviewsample.CheckboxSelectionStateChangeListener;
import treeviewsample.ExtraProperty;
import treeviewsample.FileTreeItem;
import treeviewsample.MusicConvertStatus;

@SuppressWarnings("restriction")
public class TreeViewProgressDemoController implements Initializable, CheckboxSelectionStateChangeListener {

	@FXML
	private ResourceBundle resources;

	@FXML
	private URL location;

	//http://docs.oracle.com/javafx/2/api/javafx/concurrent/Task.htmlの"A Task Which Returns An ObservableList"のようにする。
	//また、treeviewに文字列と、イメージアイコンを横に並べて表示する。
	//これにより、ツリービュー上の各ファイルの近くに変換途中であることと、変換結果を順次表示したい。
	@FXML
	private TreeView<ExtraProperty> fileTree;

	@FXML
	ProgressBar progressBar;

	@FXML
	private Button exitButton;

	@FXML
	private Button startButton;

	private List<File> convertFiles;
	private final Map<File, FileTreeItem> file2TreeItem = new HashMap<>();
	private File topFolder;
	private FileTreeItem topItem;

	private boolean isCanceled;
	private final Semaphore semaphore = new Semaphore(5);
	private volatile Integer fileCount;

	private int progressEndCount;

	@FXML
	void onStartButtonClicked(ActionEvent event) {
		if("Start".equals(startButton.getText())) {
			isCanceled = false;

			//以下をやっておかないとconvertFilesが実行中に要素が増える。
//			topItem.removeEventHandler();

			fileCount = 0;
			progressEndCount = convertFiles.size();
			System.out.println(progressEndCount + "個のファイルを変換します。");
//			final CountDownLatch latch = new CountDownLatch(progressEndCount - 1);

			//leafとなっている各ファイルの状態が見えるように、チェックされたleaf要素を含む親ノードを自動的に展開する。
			traverseTreeForExpanding(topItem);
			setButtonStateRunning();

			for (int i = 0; i < progressEndCount; i++) {
				if(isCanceled) break;
				final File file = convertFiles.get(i);
				//各ファイルごとにthreadを生成する。
				//処理するfile数だけthreadが一気に生成されないよう、Semaphoeを使ってthread数を制限している。
				Task<Void> task = createTask(file);

				task.setOnRunning(new EventHandler<WorkerStateEvent>() {
					@Override
					public void handle(WorkerStateEvent evt) {
						startAnimation(file);
					}
				});
				task.setOnCancelled(new EventHandler<WorkerStateEvent>() {
					@Override
					public void handle(WorkerStateEvent evt) {
						changeIcon(file,  MusicConvertStatus.INIT);
						updateProgressBar();
					}
				});
				task.setOnSucceeded(new EventHandler<WorkerStateEvent>() {
					@Override
					public void handle(WorkerStateEvent evt) {
						changeIcon(file,  MusicConvertStatus.SUCCEED);
						updateProgressBar();
					}
				});
				task.setOnFailed(new EventHandler<WorkerStateEvent>() {
					@Override
					public void handle(WorkerStateEvent evt) {
						changeIcon(file,  MusicConvertStatus.FAILED);
						updateProgressBar();
					}
				});
				new Thread(task).start();
			}
			//ここが実行されるのは、すべてのファイルが変換開始されたとき。
		} else if("Stop".equals(startButton.getText())) {
			startButton.setText("Start");
			isCanceled = true;
		}
	}

	@FXML
	void onExitButtonClicked(ActionEvent event) {
		Stage stage = (Stage) exitButton.getScene().getWindow();
		System.out.println("exit button clicked.");
		stage.close();
		Platform.exit();
	}

	@FXML
	void initialize() {
	}

	@SuppressWarnings("unchecked")
	@Override
	public void initialize(URL url, ResourceBundle rb) {
		assert exitButton != null : "fx:id=\"exitButton\" was not injected: check your FXML file 'mp3converter3.fxml'.";
		assert startButton != null : "fx:id=\"startButton\" was not injected: check your FXML file 'mp3converter3.fxml'.";

		exitButton.setDisable(false);
		convertFiles = new ArrayList<>();
		selectFiles();
	}

	//これは必ずしもこの方法で対処する必要はなくなった(traverseTreeForExpanding()のように、treeviewをたどりながらチェックの状態を調べられるようになったため）
	//が、このままにしておく。
	//TODO 変換中にチェックボックスの状態が変わると以下のconvertFileListの要素数が変わり、ConcurrentModification例外になるため、
	//変換中はチェックボックスを無効にする必要がある。あるいはlistをcloneするか、変換中ならこのメソッド呼び出しを受け付けないようにする。
	@Override
	public void changeCheckboxSelectionState(FileTreeItem item, File file, boolean selected) {
		if(selected) {
			convertFiles.add(file);
			file2TreeItem.put(file, item);
		} else {
			if(convertFiles.contains(file)) {
				convertFiles.remove(file);
				file2TreeItem.remove(file);
			}
		}
		changeStartButtonState();
	}

	private Task<Void> createTask(final File file) {
		return new Task<Void>() {
			@Override
			protected Void call() {
				if (isCancelled()) {
					System.out.println("canselされました。");
					changeIconLater(file, MusicConvertStatus.INIT);
					return null;
				}

				try {
					//同時に実行するスレッド数を制限する。数はコンストラクタで指定。
					semaphore.acquire();
					//以下が無いと正常にアニメーションされない。
					changeIconLater(file, MusicConvertStatus.CONVERTING);
					System.out.println("starting convert file:" + file);

					ConvertResultExp convertResult = new DummyWorker(file).start();
					System.out.println("convert done for file:" + file);

					MusicConvertStatus stat = convertResult.getStatus();
					System.out.println("result:" +  stat + ", duration:" + convertResult.getDuration());

					//以下は最後に実行すること。(失敗の場合、これより後は実行されない)
					if(stat == MusicConvertStatus.FAILED) {
						//失敗を返すためのダミーの例外。他により良い方法がないものか？
						throw new IllegalStateException();
					}
				} catch (InterruptedException e) {
					e.printStackTrace();
				} finally {
					semaphore.release();
				}
				return null;
			}
		};
	}

	private void selectFiles() {
		topFolder = new File(System.getProperty("user.home") + File.separator + "Music");

		fileTree.setCellFactory(CheckBoxTreeCell.<ExtraProperty>forTreeView());

		topItem = buildFileSystemTree(topFolder);
		topItem.setExpanded(true);
		fileTree.setRoot(topItem);
		fileTree.setShowRoot(false);
	}

	private FileTreeItem buildFileSystemTree(File dir) {
		return findTargetFiles(dir);
	}

	private FileTreeItem findTargetFiles(final File nodeFile) {
		FileTreeItem node = new FileTreeItem(this, nodeFile, false);
		// 根の要素に各ルートファイルを追加していく
		for(File f : nodeFile.listFiles()){
			if(f.isFile()) {
				node.getChildren().add(new FileTreeItem(this, f, true));
			} else if(f.isDirectory()) {
				node.getChildren().add(findTargetFiles(f));
			}
		}
		return node;
	}

	//注意：これは再帰呼び出しされる。
	private void traverseTreeForExpanding(final TreeItem<ExtraProperty> item) {
		//以下のchildはFileTreeItemにはできない(コンパイルエラーになる)
		//以下のように、ExtraPropertyを使い、これに必要なプロパティを設定することにより解決できた。
		//Node.setUserData()というのもある。fileTreeには使えるが、TreeItemに対して使えないと意味がない。
		//http://stackoverflow.com/questions/22173688/javafx-adding-more-context-information-to-an-event
		for(TreeItem<ExtraProperty> child : item.getChildren()) {
			if(!child.isLeaf()) {
				traverseTreeForExpanding(child);//再帰呼び出しする。
			} else {
				//valueProperty()により、ExtraPropertyのインスタンスが取得できる。
				ObjectProperty<ExtraProperty> prop = child.valueProperty();
				if(prop.getValue().isSelected()) {
					// 祖先をたどって全てのnodeを展開する。
					TreeItem<ExtraProperty> anchestor;
					anchestor = child;
					do {
						anchestor = anchestor.getParent();
						if(anchestor != null) {
//							Platform.runLater(new Runnable() {
//								@Override
//								public void run() {
									anchestor.setExpanded(true);
//								}
//							});
						}
					} while(anchestor != null);
					//childが一つでもチェックされていたら、残りは調べなくてもいい。
					break;
				}
			}
		}
	}

	private void updateProgressBar() {
		//taskは一つのファイルを処理したら二度と使わないため、ProgressPropertyのbind()は使えないので、progressBarを自分で更新する。
		Platform.runLater(new Runnable(){
			@Override
			public void run() {
				progressBar.setProgress((double)(fileCount + 1) / (double)(progressEndCount));
				fileCount++;
				if(fileCount == progressEndCount) {
					setButtonStateComplete();
				}
			}
		});
	}

	private void startAnimation(File file) {
		FileTreeItem item = file2TreeItem.get(file);
		if(item != null) {
			item.setStatusIconAnimated();
		}
	}

	private void changeIconLater(final File file, final MusicConvertStatus status) {
		IconUpdateRunner runner = new IconUpdateRunner(file, status);
		Platform.runLater(runner);
	}

	private void changeIcon(final File file, final MusicConvertStatus status) {
		FileTreeItem item = file2TreeItem.get(file);
		if(item != null) {
			item.setStatusIcon(status);
			item.getParent().setExpanded(false);
			item.getParent().setExpanded(true);
		} else {
			System.out.println("FileItem for " + file.getName() + " not found.");
		}
	}

	private void changeStartButtonState() {
		boolean b = (convertFiles.size() > 0);
		startButton.setDisable(!b);
	}

	private void setButtonStateComplete() {
		convertFiles.clear();
		topItem.setSelected(false);
		startButton.setDisable(false);
		startButton.setText("Start");
		exitButton.setDisable(false);
	}

	private void setButtonStateRunning() {
		Platform.runLater(new Runnable(){
			@Override
			public void run() {
				startButton.setText("Stop");
				exitButton.setDisable(true);
			}
		});
	}

	private void dumpCheckedFiles(File[] convertFileArray) {
		for (File file : convertFileArray) {
			System.out.println(file.getAbsolutePath());
		}
	}

	class IconUpdateRunner implements Runnable {
		private final File file;
		private final MusicConvertStatus status;

		public IconUpdateRunner(File file, MusicConvertStatus status) {
			this.file = file;
			this.status = status;
		}

		@Override
		public void run() {
//			System.out.println("ファイル" + file + " のアイコンを" + status.name() + "に変えます。");
			changeIcon(file, status);
		}
	}
}
