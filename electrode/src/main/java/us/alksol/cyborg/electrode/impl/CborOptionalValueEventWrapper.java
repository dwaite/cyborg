package us.alksol.cyborg.electrode.impl;

import java.math.BigInteger;
import java.util.Objects;
import java.util.Optional;

import javax.validation.constraints.NotNull;

import us.alksol.bytestring.Bytes;
import us.alksol.cyborg.electrode.CborEvent;
import us.alksol.cyborg.electrode.CborException;
import us.alksol.cyborg.electrode.CborOptionalValue;

public class CborOptionalValueEventWrapper implements CborOptionalValue {
	private @NotNull CborEvent event;
	
	public CborOptionalValueEventWrapper(@NotNull CborEvent event) {
		Objects.requireNonNull(event);
		this.event = event;
	}
	
	@Override
	public Optional<Integer> intValue() throws ArithmeticException, CborException {
		if (event.isNull()) {
			return Optional.empty();
		}
		return Optional.of(event.intValue());
	}

	@Override
	public Optional<Long> longValue() throws CborException {
		if (event.isNull()) {
			return Optional.empty();
		}
		return Optional.of(event.longValue());
	}

	@Override
	public Optional<BigInteger> bigIntegerValue() throws CborException {
		if (event.isNull()) {
			return Optional.empty();
		}
		return Optional.of(event.bigIntegerValue());
	}

	@Override
	public boolean isIndefiniteLengthContainer() {
		return event.isIndefiniteLengthContainer();
	}

	@Override
	public Optional<Integer> count() throws CborException {
		if (event.isNull()) {
			return Optional.empty();
		}
		int count = event.count();
		if (count == -1) {
			return Optional.empty();
		}
		return Optional.of(count);
	}

	@Override
	public Optional<Short> halfFloatValue() throws CborException {
		if (event.isNull()) {
			return Optional.empty();
		}
		return Optional.of(event.halfFloatValue());
	}
			

	@Override
	public Optional<Float> floatValue() throws CborException {
		if (event.isNull()) {
			return Optional.empty();
		}
		return Optional.of(event.floatValue());
	}

	@Override
	public Optional<Double> doubleValue() throws CborException {
		if (event.isNull()) {
			return Optional.empty();
		}
		return Optional.of(event.doubleValue());
	}

	@Override
	public Optional<Boolean> booleanValue() throws CborException {
		if (event.isNull()) {
			return Optional.empty();
		}
		return Optional.of(event.booleanValue());

	}

	@Override
	public boolean isNull() {
		return event.isNull();
	}

	@Override
	public boolean isUndefined() {
		return event.isUndefined();
	}

	@Override
	public Optional<Bytes> bytes() {
		return Optional.ofNullable(event.bytes());
	}

	@Override
	public Optional<String> asTextValue() {
		if (event.isNull()) {
			return Optional.empty();
		}
		return Optional.ofNullable(event.asTextValue());
	}

	@Override
	public boolean isBreak() {
		return event.isBreak();
	}

	@Override
	public boolean isLiteralBreak() {
		return event.isLiteralBreak();
	}

}
