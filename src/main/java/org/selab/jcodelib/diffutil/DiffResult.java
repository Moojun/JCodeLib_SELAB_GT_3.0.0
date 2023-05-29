package org.selab.jcodelib.diffutil;

import kr.ac.seoultech.selab.esscore.model.Script;

public class DiffResult {

	private Script script;
	private long runtime;

	public DiffResult() {
		this(new Script(), 0);
	}

	public DiffResult(Script script) {
		this(script, 0);
	}

	public DiffResult(Script script, long runtime) {
		super();
		this.script = script;
		this.runtime = runtime;
	}

	public Script getScript() {
		return script;
	}

	public void setScript(Script script) {
		this.script = script;
	}

	public long getRuntime() {
		return runtime;
	}

	public void setRuntime(long runtime) {
		this.runtime = runtime;
	}

}
