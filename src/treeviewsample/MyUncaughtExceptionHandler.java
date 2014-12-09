package treeviewsample;

import java.lang.Thread.UncaughtExceptionHandler;

public class MyUncaughtExceptionHandler implements UncaughtExceptionHandler {
    @Override
	public void uncaughtException(Thread thread, Throwable throwable) {
        //アプリケーションの終了
        System.out.println("例外が発生しました。");
        throwable.printStackTrace();
//        System.out.println("スレッドを再起動します。");
//        thread.start();
    }
}