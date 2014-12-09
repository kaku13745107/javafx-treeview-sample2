package treeviewsample.demo;

import java.io.File;

import treeviewsample.MusicConvertStatus;


public class ConvertResultExp {

	private final int exitCode;
	private final File orgMusicFile;
	private final int duration;

	public ConvertResultExp(File orgMusicFile, int duration, int exitCode) {
		this.orgMusicFile = orgMusicFile;
		this.duration = duration;
		this.exitCode = exitCode;
	}

	public int getDuration() {
		return duration;
	}

	public MusicConvertStatus getStatus() {
		MusicConvertStatus status = null;
		if(exitCode == 0) {
			status = MusicConvertStatus.SUCCEED;
		} else {
			status = MusicConvertStatus.FAILED;
		}

		return status;
	}

	public File getOrgMusicFile() {
		return orgMusicFile;
	}

}
