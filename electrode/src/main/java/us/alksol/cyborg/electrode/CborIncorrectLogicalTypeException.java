package us.alksol.cyborg.electrode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import us.alksol.cyborg.electrode.InitialByte.LogicalType;
import us.alksol.cyborg.electrode.InitialByte.Major;

public class CborIncorrectLogicalTypeException extends CborException {
	private static final long serialVersionUID = 1L;

	private final List<LogicalType> expected;
	private final InitialByte ib;

	public CborIncorrectLogicalTypeException(InitialByte header, LogicalType expected) {
		super("Received data item with initial byte " + header + ", expected data of logical type " + expected);
		this.ib = header;
		this.expected = Collections.singletonList(expected);
	}

	public CborIncorrectLogicalTypeException(InitialByte header, LogicalType... expected) {
		super("Received data item with initial byte " + header + ", expected data of one of the logical types " + Arrays.toString(expected));
		this.ib = header;
		this.expected = Collections.unmodifiableList(new ArrayList<>(Arrays.asList(expected)));
	}

	public List<LogicalType> getExpected() {
		return expected;
	}

	public Major getActual() {
		return ib.getMajor();
	}
	
	public InitialByte getInitialByte() {
		return ib;
	}

}
