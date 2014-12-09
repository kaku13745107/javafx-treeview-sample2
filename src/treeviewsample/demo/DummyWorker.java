package treeviewsample.demo;

import java.io.File;
import java.util.Random;

public class DummyWorker {

	private final File orgMusicFile;
	private static Random rndDuration = new Random();
	private static Random rndResult = new Random();

	//一つのファイルを変換したら使い捨てにする。
	public DummyWorker(File orgMusicFile) {
		this.orgMusicFile = orgMusicFile;
	}

	public ConvertResultExp start() throws InterruptedException {
		int exitCode = rndResult.nextInt(2); //0 or 1
		int duration = 1 + rndDuration.nextInt(10);
		Thread.sleep(duration * 1000);

		return new ConvertResultExp(orgMusicFile, duration, exitCode);
	}

}
