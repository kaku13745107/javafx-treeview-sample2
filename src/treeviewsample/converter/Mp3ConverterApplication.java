package treeviewsample.converter;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Mp3ConverterApplication extends Application {

    @Override
    public void start(Stage stage) throws Exception {
//        Parent root = FXMLLoader.load(getClass().getResource("mp3converter3.fxml"));

    	//Applicationのlaunch()メソッドの引数として受け取ったargsは、Application.getParameters()で受け取れる。
        //しかし、コントローラー側でこれをやろうとしても、Applicationオブジェクトをgetする方法が見つからなかった。
        //そこで、http://stackoverflow.com/questions/13246211/javafx-how-to-get-stage-from-controller-during-initialization
    	//にある、コントローラーに任意のオブジェクトを渡す方法を使う。
        Parameters params = this.getParameters();

    	FXMLLoader loader = new FXMLLoader(getClass().getResource("mp3converter.fxml"));
    	Parent root = (Parent)loader.load();
    	Mp3ConverterController controller = (Mp3ConverterController)loader.getController();

    	controller.setCommandLineArgs(params);

        Scene scene = new Scene(root);
        stage.setScene(scene);
        stage.show();
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }

}
