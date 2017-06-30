package com.github.dwaite.cyborg.electrode.impl;

import java.io.DataOutput;
import java.io.IOException;
import java.util.Objects;

import com.github.dwaite.cyborg.electrode.CborEvent;
import com.github.dwaite.cyborg.electrode.CborGenerator;

public class CborOutput implements CborGenerator {
	private DataOutput target;
	
	public CborOutput(DataOutput target) {
		Objects.requireNonNull(target);
		this.target = target;
	}

	@Override
	public CborGenerator next(CborEvent event) throws IOException {
		event.write(target);
		return this;
	}	
}