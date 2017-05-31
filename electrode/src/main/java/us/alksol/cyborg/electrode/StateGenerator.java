package us.alksol.cyborg.electrode;

import java.io.IOException;
import java.util.Objects;

/** 
 * For modular CBOR generators, a parent 'state generator' allows switching the consumer
 * of events at runtime.
 * 
 * It is the responsibility of a generator to restore the state generator to the previous
 * 'parent' generator once it is complete, as well as to call any interface on the parent 
 * generator to supply data.
 *
 */
public class StateGenerator implements CborGenerator {
	private CborGenerator state;

	public StateGenerator(CborGenerator generator) {
		Objects.requireNonNull(generator);
		state = generator;
	}
	@Override
	public CborGenerator next(CborEvent event) throws IOException {
		state.next(event);
		return this;
	}
	public CborGenerator getGenerator() {
		return state;
	}
	public void setGenerator(CborGenerator generator) {
		Objects.requireNonNull(generator);
		this.state = generator;
	}
}
