package com.github.dwaite.cyborg.electrode;

import java.io.IOException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import com.github.dwaite.bytestring.Bytes;
import com.github.dwaite.cyborg.electrode.InitialByte.Major;

public interface CborParser extends Iterator<CborEvent> {
	/**
	 * Passive read of next element, without changing the results of the next calls to {@link #hasNext()} 
	 * and {@link #next()} (or {@link #read()}).
	 * 
	 * @return CborElement if more elements present
	 * @throws NoSuchElementException if no elements present
	 * @throws IOException if underlying parser cannot peek another element due to issues with underlying
	 * input stream
	 */
	CborEvent peek() throws IOException, NoSuchElementException;

	/**
	 * Beyond {@link Iterator#next()} requirements, the parser may be determining the next event via reading
	 * from an IO source or computing the next event. In these cases, 
	 */
	@Override
	CborEvent next() throws NoSuchElementException;
	
	default CborEvent read() throws IOException {
		try {
		if (hasNext())
			return next();
		return null;
		}
		catch (NoSuchElementException e) {
			Throwable inner = e.getCause();
			if (inner instanceof IOException) {
				throw (IOException) inner;
			}
			if (inner instanceof RuntimeException) {
				throw (RuntimeException) inner;
			}
			throw e;
		}
	}
	
	default boolean readBoolean() throws IOException {
		CborEvent event = peek();
		boolean retval = event.booleanValue();
		next();
		return retval;
	}
	
	default void readNull() throws IOException {
		CborEvent event = peek();
		if (event.isNull()) {
			next();
		} else {
			throw new IllegalStateException();
		}
	}
	
	default void readUndefined() throws IOException {
		CborEvent event = peek();
		if (event.isUndefined()) {
			next();
		} else {
			throw new IllegalStateException();
		}
	}

	default int readSimpleValue() throws IOException {
		CborEvent event = peek();
		if (!event.isSimpleValue()) {
			throw new IllegalStateException();
		}
		int retval = event.additionalInfoAsInt();
		next();
		return retval;
	}
	
	default int readInteger() throws IOException {
		CborEvent event = peek();
		try {
			event.getInitialByte().assertMajorType(Major.INTEGER, Major.NEGATIVE_INTEGER);
		}
		catch (CborException e) {
			throw new IllegalStateException(e);
		}
		int retval = event.additionalInfoAsInt();
		next();
		return retval;
	}
	
	default long readLong() throws IOException {
		CborEvent event = peek();
		try {
			event.getInitialByte().assertMajorType(Major.INTEGER, Major.NEGATIVE_INTEGER);
		}
		catch (CborException e) {
			throw new IllegalStateException(e);
		}
		long retval = event.additionalInfoAsLong();
		next();
		return retval;
	}

	default long readUnsignedLong() throws IOException {
		CborEvent event = peek();
		try {
			event.getInitialByte().assertMajorType(Major.INTEGER);
		}
		catch (CborException e) {
			throw new IllegalStateException(e);
		}
		long retval = event.additionalInfoAsLong();
		next();
		return retval;
	}
	
	default <A,R> R collectBinary(Major majorType, Collector<Bytes, A, R> collector) throws IOException {
		Objects.requireNonNull(majorType);
		Objects.requireNonNull(collector);
		if (!majorType.isPossiblyFollowedByBinaryData()) {
			throw new IllegalStateException();
		}
		CborEvent event = peek();
		
		if (event.getInitialByte().getMajor() != majorType) {
			throw new IllegalStateException();
		}
		A result = collector.supplier().get();
		BiConsumer<A, Bytes> accumulator = collector.accumulator();
		Function<A, R> finisher = collector.finisher();
		if (!event.isStartOfIndefiniteLengthContainer()) {
			accumulator.accept(result, event.bytes());
			next();
			return finisher.apply(result);
		}
		try {
			while (hasNext() && !peek().isBreak()) {
				if (event.getInitialByte().getMajor() != Major.BYTE_STRING) {
					throw new IOException("Unexpected data within indefinite length byte string");
				}
				accumulator.accept(result, next().bytes());
			}
			next(); // consume break
			return finisher.apply(result);
		}
		catch (NoSuchElementException e) {
			Throwable cause = e.getCause();
			if (cause instanceof IOException) {
				throw (IOException) cause;
			}
			if (cause instanceof RuntimeException) {
				throw (RuntimeException) cause;
			}
			throw e;
		}
	}
	
	default void consumeBinary(Major majorType, Consumer<Bytes> consumer) throws IOException {
		Supplier<Consumer<Bytes>> supplier = () -> consumer;
		BiConsumer<Consumer<Bytes>, Bytes> biConsumer = Consumer::accept;
		BinaryOperator<Consumer<Bytes>> accumulator = (a, b) -> a;
		Function<Consumer<Bytes>, Void> finisher = (a) -> null;
		Collector<Bytes, Consumer<Bytes>, Void> collector = Collector.of(
				supplier,
				biConsumer,
				accumulator,
				finisher,
				new Collector.Characteristics[]{});
		collectBinary(majorType, collector);
	}
	
	default Bytes readBinary() throws IOException {
		return collectBinary(Major.BYTE_STRING, Collectors.reducing(Bytes.empty(),
				(Bytes b1, Bytes b2) -> { return b1.concat(b2);}));
	}

	default String readText() throws IOException {
		return collectBinary(Major.TEXT_STRING, Collectors.reducing(Bytes.empty(), 
				(Bytes b1, Bytes b2) -> { return b1.concat(b2); })).asUTF8String();
	}

	default short readHalfFloat() throws IOException {
		CborEvent event = peek();
		short retval = event.additionalInfoAsHalfFloat();
		next();
		return retval;
	}
	default float readFloat() throws IOException {
		CborEvent event = peek();
		float retval = event.additionalInfoAsFloat();
		next();
		return retval;
	}
	default double readDouble() throws IOException {
		CborEvent event = peek();
		double retval = event.additionalInfoAsDouble();
		next();
		return retval;
	}
	
	default long readTag() throws IOException {
		CborEvent event = peek();
		try {
			event.getInitialByte().assertMajorType(Major.TAG);
		}
		catch (CborException e) {
			throw new IllegalStateException(e);
		}
		long retval = event.additionalInfoAsLong();
		next();
		return retval;
	}

	default int readStartArray() throws IOException {
		CborEvent event = peek();
		Major major = event.getInitialByte().getMajor();
		if (major != Major.ARRAY || event.isStartOfIndefiniteLengthContainer()) {
			throw new IllegalStateException();			
		}
		int retval = (int) event.additionalInfoAsCount();
		next();
		return retval;
	}

	default void readStartIndefiniteArray() throws IOException {
		CborEvent event = peek();
		Major major = event.getInitialByte().getMajor();
		if (major != Major.ARRAY || !event.isStartOfIndefiniteLengthContainer()) {
			throw new IllegalStateException();			
		}
		next();
	}

	default int readStartMap() throws IOException {
		CborEvent event = peek();
		Major major = event.getInitialByte().getMajor();
		if (major != Major.MAP || event.isStartOfIndefiniteLengthContainer()) {
			throw new IllegalStateException();			
		}
		int retval = (int) event.additionalInfoAsCount();
		next();
		return retval;
	}

	default void readStartIndefiniteMap() throws IOException {
		CborEvent event = peek();
		Major major = event.getInitialByte().getMajor();
		if (major != Major.MAP || !event.isStartOfIndefiniteLengthContainer()) {
			throw new IllegalStateException();			
		}
		next();
	}
	
	default void readBreak() throws IOException {
		CborEvent event = peek();
		if (!event.isBreak()) {
			throw new IllegalArgumentException();
		}
		next();
	}
}
