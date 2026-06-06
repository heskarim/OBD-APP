package com.fr3ts0n.prot;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StreamHandlerTest
{
	@Test
	void serializesOutgoingTelegramsOnOneWorkerInSubmissionOrder() throws Exception
	{
		TrackingOutputStream output = new TrackingOutputStream(20);
		StreamHandler handler = new StreamHandler(new ByteArrayInputStream(new byte[0]), output);

		for (int index = 0; index < 20; index++)
		{
			handler.writeTelegram(String.format("01%02X", index).toCharArray());
		}

		assertTrue(output.awaitTelegrams(), "Timed out waiting for outgoing telegrams");
		assertEquals(1, output.writerThreads.size(), "Outgoing telegrams must share one writer worker");

		StringBuilder expected = new StringBuilder();
		for (int index = 0; index < 20; index++)
		{
			expected.append(String.format("01%02X\r", index));
		}
		assertEquals(expected.toString(), output.asString());
	}

	private static final class TrackingOutputStream extends ByteArrayOutputStream
	{
		private final Set<Long> writerThreads = Collections.synchronizedSet(new HashSet<>());
		private final CountDownLatch telegrams;

		private TrackingOutputStream(int telegramCount)
		{
			telegrams = new CountDownLatch(telegramCount);
		}

		@Override
		public synchronized void write(byte[] buffer, int offset, int length)
		{
			writerThreads.add(Thread.currentThread().getId());
			super.write(buffer, offset, length);
			for (int index = offset; index < offset + length; index++)
			{
				if (buffer[index] == '\r')
				{
					telegrams.countDown();
				}
			}
		}

		@Override
		public synchronized void write(int value)
		{
			writerThreads.add(Thread.currentThread().getId());
			super.write(value);
			if (value == '\r')
			{
				telegrams.countDown();
			}
		}

		private boolean awaitTelegrams() throws InterruptedException
		{
			return telegrams.await(5, TimeUnit.SECONDS);
		}

		private synchronized String asString() throws IOException
		{
			return toString(StandardCharsets.UTF_8);
		}
	}
}
