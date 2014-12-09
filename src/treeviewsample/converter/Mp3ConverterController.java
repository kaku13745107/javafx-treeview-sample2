package treeviewsample.converter;

import java.io.File;
import java.io.FilenameFilter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.Semaphore;

import javafx.application.Application.Parameters;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.cell.CheckBoxTreeCell;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import treeviewsample.CheckboxSelectionStateChangeListener;
import treeviewsample.ExtraProperty;
import treeviewsample.FileTreeItem;
import treeviewsample.MusicConvertStatus;

@SuppressWarnings("restriction")
public class Mp3ConverterController implements Initializable, CheckboxSelectionStateChangeListener {

	private static final String DEST_FOLDER_LABEL = "変換したファイルの格納先：";

	@FXML
	private Button destFolderChoiceButton;

	@FXML
	private Button orginalFolderChoiceButton;

	@FXML
	private Label destFolderLabel;

	@FXML
	private ResourceBundle resources;

	@FXML
	private URL location;

	//http://docs.oracle.com/javafx/2/api/javafx/concurrent/Task.htmlの"A Task Which Returns An ObservableList"のようにする。
	//また、treeviewにイメージアイコン、チェックボックス、文字列を表示する。
	//これにより、ツリービュー上の各ファイルの近くに変換途中であることと、変換結果(成功・失敗)を順次表示する。
	@FXML
	private TreeView<ExtraProperty> fileTree;

	@FXML
	ProgressBar progressBar;

	@FXML
	private Button exitButton;

	@FXML
	private Button startButton;

	private FilenameFilter filenameFilter;

	private final List<File> convertFiles = new ArrayList<>();
	private final Map<File, FileTreeItem> file2TreeItem = new HashMap<>();
	private File destFolder;
	private File topFolder;
	private FileTreeItem topItem;
	private boolean isDestFolderSpecified = false;

	private boolean isCanceled;
	private final Semaphore semaphore = new Semaphore(5);
	private int fileCount = 0;
	private int progressEndCount;

	protected boolean isDemo = false;

	@FXML
	void onOriginalFolderChoiceButtonClicked(ActionEvent event) {
		topFolder = showFolderChoiceDialog("変換元のフォルダーを指定してください。", orginalFolderChoiceButton.getScene().getWindow());

		fileTree.setCellFactory(CheckBoxTreeCell.<ExtraProperty>forTreeView());

		topItem = buildFileSystemTree(topFolder);
		if(topItem != null) {
			topItem.setExpanded(true);
			fileTree.setRoot(topItem);
			fileTree.setShowRoot(false);
		}
	}

	@FXML
	void onDestFolderChoiceButtonClicked(ActionEvent event) {
		destFolder = showFolderChoiceDialog("変換したファイルを格納するフォルダーを指定してください。", destFolderChoiceButton.getScene().getWindow());
		if(destFolder != null) {
			destFolderLabel.setText(DEST_FOLDER_LABEL + destFolder.toString());
			isDestFolderSpecified  = true;

			changeStartButtonState();
		}
	}

	@FXML
	void onStartButtonClicked(ActionEvent event) {
		if("Start".equals(startButton.getText())) {
			isCanceled = false;

			dumpCheckedFiles();

			fileCount = 0;
			progressEndCount = convertFiles.size();
			System.out.println(progressEndCount + "個のファイルを変換します。");

			//leafとなっている各ファイルの状態が見えるように、チェックされたleaf要素を含む親ノードを自動的に展開する。
			traverseTreeForExpanding(topItem);
			setButtonStateRunning();

			//JavaFXではServiceあるいはTaskでバックグラウンド処理を行うのが定石。
			//http://www.torutk.com/projects/swe/wiki/JavaFXとマルチスレッド

			for (final File file : convertFiles) {
				if(isCanceled) break;
				//各ファイルごとにthreadを生成する。
				//処理するfile数だけthreadが一気に生成されないよう、createTask()の中で同時に生成するthread数を制限している。
				final Task<MusicConvertStatus> task = createTask(file);

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
						System.out.println("task.setOnSucceeded() thread: " + Platform.isFxApplicationThread());
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
			} //for loop
			//注意：ここに到達しても変換はまだ終わっていない(別スレッドで実行中)
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
		assert orginalFolderChoiceButton != null : "fx:id=\"originalFolderChoiceButton\" was not injected: check your FXML file 'mp3converter3.fxml'.";
		assert destFolderChoiceButton != null : "fx:id=\"originalFolderChoiceButton\" was not injected: check your FXML file 'mp3converter3.fxml'.";
		assert exitButton != null : "fx:id=\"exitButton\" was not injected: check your FXML file 'mp3converter3.fxml'.";
		assert startButton != null : "fx:id=\"startButton\" was not injected: check your FXML file 'mp3converter3.fxml'.";
		startButton.setDisable(true);
		destFolderLabel.setText(DEST_FOLDER_LABEL);

		if(!ConvertWorker.checkCommandExists()) {
			destFolderLabel.setText("ffmpegコマンドが見つかりませんでした。");
			orginalFolderChoiceButton.setDisable(true);
			destFolderChoiceButton.setDisable(true);
		}

		filenameFilter = new FilenameFilter() {
			@Override
			public boolean accept(File parentDir, String name) {
				if(parentDir == null || name == null) return false;
				File f = new File(parentDir, name);
				boolean b = f.isDirectory() || name.toLowerCase().endsWith(".wav");
				return (b);
			}
		};

		if(url != null) {
			System.out.println("url:" + url);
		}

		if(rb != null) {
			Enumeration<String> e = rb.getKeys();
			while(e.hasMoreElements()) {
				String key = e.nextElement();
				System.out.println("key: " + key);
			}
		}
//		paramsがセットされるのは後。
//		if(params != null) {
//	        for(String param : params.getRaw()) {
//	        	System.out.println("param:" + param);
//	        }
//		} else {
//			System.out.println("params はnull");
//		}
 	}

	//TreeViewの各アイテムにつけたチェックボックスの状態が変わったときに呼ばれる。
	//必ずしもこの方法でなくてもよい(traverseTreeForExpanding()のように、treeviewをたどりながらチェックの状態を調べられる）
	//が、トータルの対象数がスタート前に分かっていた方がいいので、このままにしておく。
	//TODO 変換中にチェックボックスの状態が変えられないよう、変換中はチェックボックスを無効にしたかったが、やり方がわからない。
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

	public void setCommandLineArgs(Parameters params) {
		System.out.println("paramsがセットされた。");
		if(params != null) {
	        for(String param : params.getRaw()) {
	        	System.out.println("param:" + param);
	        	isDemo = ("-isDemo".equals(param));
	        	if(isDemo) {
	        		System.out.println("デモ・モードにします。実際の変換は行いません");
	        		break;
	        	}
	        }
		 }
	}

	private Task<MusicConvertStatus> createTask(final File file) {
		return new Task<MusicConvertStatus>() {
			@Override
			protected MusicConvertStatus call() {
				if (isCancelled()) {
					System.out.println("canselされました。");
					return null;
				}

				MusicConvertStatus stat = null;

				try {
					System.out.println("start converting: file:" + file);

					//同時に実行するスレッド数を制限する。数はコンストラクタで指定。
					semaphore.acquire();
					changeIconLater(file, MusicConvertStatus.CONVERTING);
					ConvertResult result = new ConvertWorker(topFolder, destFolder, file, isDemo).start();
					System.out.println("convert done for file:" + file);

					stat = result.getStatus();
					System.out.println("result:" + stat);

					//以下は最後に実行すること。(失敗の場合、これより後は実行されない)
					if(stat == MusicConvertStatus.FAILED) {
						System.out.println("msg\n" + result.getMessage());
						//失敗を返すためのダミーの例外。他により良い方法がないものか？
						throw new IllegalStateException();
					}
				} catch (InterruptedException e) {
					//aquire()とConverterWorker.start()の両方で起こり得る
					e.printStackTrace();
				} finally {
					semaphore.release();
				}
				return stat;
			}
		};
	}

	// 根の要素に各ファイルを追加していく
	//注意：JavaFX Application threadで呼ばれるので、ファイル数が多いと画面が固まる。
	private FileTreeItem buildFileSystemTree(File dir) {
		return findTargetFiles(dir);
	}

	//再帰呼び出し
	private FileTreeItem findTargetFiles(final File nodeFile) {
		FileTreeItem node = null;
		if(nodeFile != null) {
			node = new FileTreeItem(this, nodeFile, false);
			//filtering
			for(File f : nodeFile.listFiles(filenameFilter)){
				if(f.isFile()) {
					node.getChildren().add(new FileTreeItem(this, f, true));
				} else if(f.isDirectory()) {
					node.getChildren().add(findTargetFiles(f));
				}
			}
		}
		return node;
	}

	//選択されたチェックボックスの配下のツリーをすべて展開する。
	//注意：これは再帰呼び出しされる。
	private void traverseTreeForExpanding(final TreeItem<ExtraProperty> item) {
		//以下のchildはFileTreeItemにはできない(コンパイルエラーになる)
		//以下のように、ExtraPropertyを使うことにより解決できた。
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
							anchestor.setExpanded(true);
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
					convertFiles.clear();
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
		System.out.println("ファイル" + file + " のアイコンを" + status.name() + "に変えようとしています。");
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

	private File showFolderChoiceDialog(String title, Window window) {
		DirectoryChooser chooser = new DirectoryChooser();
		chooser.setTitle(title);
		//デフォルトのディレクトリとして、ユーザーのホームの下にある"Music"を使う。(win7, Ubuntu, OS XではこれでOK)
		String musicDir = System.getProperty("user.home") + File.separator + "Music";
		chooser.setInitialDirectory(new File(musicDir));
		return chooser.showDialog(window);
	}

	private void changeStartButtonState() {
		boolean b = (isDestFolderSpecified && convertFiles.size() > 0);
		startButton.setDisable(!b);
	}

	//以下のようにServiceの実行状態に応じてボタンを有効・無効にしたり、プログレスバーを更新するのが定石らしいが、やっていない。
	//バインディングをカスタマイズする方法が、https://docs.oracle.com/javafx/2/binding/jfxpub-binding.htm にあるので、
	//アプリの実行状態に応じてボタンを制御した方がいいと思う。（プログラム全体の見通しも良くなる)

//	progressBar.progressProperty().bind(service.progressProperty());
//	progressBar.visibleProperty().bind(service.runningProperty());
//	orginalFolderChoiceButton.disableProperty().bind(service.runningProperty());
//	destFolderChoiceButton.disableProperty().bind(service.runningProperty());
	//論理を逆にしたい場合、以下のようにnot()を使うらしい（未確認)
	//exitButton.disableProperty().bind(service.runningProperty().not());
	//他にもand(), or()がある。xor()は無い。
//	exitButton.disableProperty().bind(service.runningProperty());

	private void setButtonStateComplete() {
		topItem.setSelected(false);
		startButton.setDisable(false);
		startButton.setText("Start");
		exitButton.setDisable(false);
		orginalFolderChoiceButton.setDisable(false);
		destFolderChoiceButton.setDisable(false);
	}

	private void setButtonStateRunning() {
		Platform.runLater(new Runnable(){
			@Override
			public void run() {
				startButton.setText("Stop");
				exitButton.setDisable(true);
				orginalFolderChoiceButton.setDisable(true);
				destFolderChoiceButton.setDisable(true);
			}
		});
	}

	private void dumpCheckedFiles() {
		for (File file : convertFiles) {
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
			System.out.println("ファイル" + file + " のアイコンを" + status.name() + "に変えました。");
			FileTreeItem item = file2TreeItem.get(file);
			if(item != null) {
				item.setStatusIcon(status);
				//Swingのようなrepaint()はないが、以下のように親フォルダーをいったん閉じてから即座に展開することによりリフレッシュできた。
				item.getParent().setExpanded(false);
				item.getParent().setExpanded(true);
			} else {
				System.out.println("FileItem for " + file.getName() + " not found.");
			}
		}
	}
}
