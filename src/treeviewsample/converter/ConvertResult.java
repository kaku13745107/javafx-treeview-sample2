package treeviewsample.converter;

import java.io.File;

import lombok.Getter;
import treeviewsample.MusicConvertStatus;

@Getter
public class ConvertResult {
	private final File musicFile;
	private final boolean outDirExists;
	private final boolean isMusicFileExist;
	private final boolean isWaveFile;
	private final int exitCode;
	private final String message;
	private final MusicConvertStatus status;

	public ConvertResult(File musicFile, boolean outDirExists, boolean isMusicFileExist,
			boolean isWaveFile, boolean isTimeout, int exitCode, String msg) {
		this.musicFile = musicFile;
		this.outDirExists = outDirExists;
		this.isMusicFileExist = isMusicFileExist;
		this.isWaveFile = isWaveFile;
		this.exitCode = exitCode;
		this.message = msg;

		if(isTimeout) {
			status = MusicConvertStatus.FAILED;
		} else {
			if(exitCode == 0) {
				status = MusicConvertStatus.SUCCEED;
			} else {
				status = MusicConvertStatus.FAILED;
			}
		}
	}
}
