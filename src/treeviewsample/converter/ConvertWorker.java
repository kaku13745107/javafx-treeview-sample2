package treeviewsample.converter;

//以下のようなコマンドを実行する
//	static String orgCmd = "ffmpeg -y -i \"01 Tô Voltando.wav\" -acodec libmp3lame -ab 256k \"01 Tô Voltando.mp3\"";

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class ConvertWorker {
	private static final int INPUT_INDEX = 3;
	private static final int OUTFILE_INDEX = 8;
	private static final int BUFSIZE = 10240;
	private boolean isEmulate = false;

	private Random rnd;
	private static String winBinary = "C:\\Program Files\\ffmpeg\\bin\\ffmpeg";
	private static String linuxBinary = "/usr/bin/ffmpeg";
	private static String osxBinary = "/usr/local/bin/ffmpeg";

	private static String[] cmd = {
		"", //ここに実行ファイル名
		"-y",
		"-i",
		"", //ここに変換元ファイル名(.wav)
		"-acodec",
		"libmp3lame",
		"-ab",
		"256k",
		""//ここに変換先ファイル名(.mp3)
	};

	static {
		String osname = System.getProperty("os.name");
		if (osname.startsWith("Windows")) {
			cmd[0] = winBinary;
		} else if("Linux".equals(osname)){
			cmd[0] = linuxBinary;
		} else if("Mac OS X".equals(osname)) {
			cmd[0] = osxBinary;
		}
	}

	private final File orgMusicFile;
	private final File outFile;
	private boolean outDirExists;
	private final boolean isWaveFile;
	private final boolean isMusicFileExist;
	private boolean isStarted = false;
	private boolean isTimeout;

	//動作確認用
	public static void main(String[] args) throws InterruptedException {
//		for(String arg : orgCmd.split(" ")) {
//			System.out.println("\"" + arg + "\", ");
//		}
		if(args.length > 1) {
			File topFolder = new File(args[0]);
			File outFolder = new File(args[1]);
			File musicFile = new File(args[2]);
			new ConvertWorker(topFolder, outFolder, musicFile, false).start();
		}
	}

	public static boolean checkCommandExists() {
		File exe = new File(cmd[0]);
		return (exe.exists() && exe.canExecute());
	}

	//一つのファイルを変換したら使い捨てにする。
	public ConvertWorker(File topFolder, File outDir, File orgMusicFile, boolean isEmulate) {
		this.orgMusicFile = orgMusicFile;
		this.isEmulate = isEmulate;
		if(isEmulate) {
			rnd = new Random(System.currentTimeMillis());
		}

		if(outDir.exists()) {
			outDirExists = true;
		} else {
			outDirExists = outDir.mkdirs();//作成成功でtrue
		}

		isMusicFileExist = orgMusicFile.exists();
		if(isMusicFileExist) {
			String musicFilename = orgMusicFile.getName();
			File musicFileParent = orgMusicFile.getParentFile();
			//topFolderとmusicFileParentの差をoutDirに適用する。これにより変換先にも変換元と同じディレクトリ・ツリーが作成される。
			Path diff = topFolder.toPath().relativize(musicFileParent.toPath());
			File outDir2 = new File(outDir, diff.toString());
			if(!outDir2.exists()) {
				outDirExists = outDir2.mkdirs();
			}
			this.outFile = new File(outDir2, musicFilename.replace(".wav", ".mp3"));
			isWaveFile = (musicFilename.toLowerCase().endsWith(".wav"));
		} else {
			isWaveFile = false;
			this.outFile = null;
		}
	}

	public ConvertResult start() throws InterruptedException {
		if(isStarted ) return null;
		isStarted = true;

		int exitCode = Integer.MAX_VALUE;
		String msg = null;

		if(outDirExists && isMusicFileExist && isWaveFile) {
			cmd[INPUT_INDEX] = orgMusicFile.getAbsolutePath();
			cmd[OUTFILE_INDEX] = outFile.getAbsolutePath();
			List<String> command = Arrays.asList(cmd);
			ProcessBuilder pb = new ProcessBuilder(command );
			InputStream is = null;
			ByteArrayOutputStream bos = null;
			Process process = null;
			//TODO isについて、try-with-resourcesは使えない？
			try {
				if(isEmulate) {
					Thread.sleep(3000);
					exitCode = rnd.nextBoolean()?1:0;
					isTimeout = false;
					System.out.println("emulated exitcode:" + exitCode);
				} else {
					pb.redirectErrorStream(true);
					process = pb.start();
					isTimeout = !process.waitFor(60, TimeUnit.SECONDS);
					exitCode = process.exitValue();
					is = process.getInputStream();
					bos = new ByteArrayOutputStream();
					byte[] buf = new byte[BUFSIZE];
					int count;
					while((count = is.read(buf)) > 0) {
						bos.write(buf, 0, count);
					}
					System.out.println("timeout:" + isTimeout + ", exit code:" + exitCode);
					msg = new String(bos.toByteArray());
				}
			} catch (IOException e) {
				e.printStackTrace();
			} catch (InterruptedException e) {
				//http://stackoverflow.com/questions/7065067/how-to-close-std-streams-from-java-lang-process-appropriate
				if(process != null)	process.destroy();
				throw(e);
//				e.printStackTrace();
			} finally {
				try {
					if(bos != null) bos.close();
				} catch (IOException e) {
					e.printStackTrace();
				} finally {
					try {
						if(is != null) is.close();
					} catch (IOException e1) {
						e1.printStackTrace();
					}
				}
			}
		}

		return new ConvertResult(orgMusicFile, outDirExists, isMusicFileExist, isWaveFile, isTimeout, exitCode, msg);
	}
}
