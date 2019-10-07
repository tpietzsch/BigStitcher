/*
 * #%L
 * ImgLib2: a general-purpose, multidimensional image processing library.
 * %%
 * Copyright (C) 2009 - 2016 Tobias Pietzsch, Stephan Preibisch, Stephan Saalfeld,
 * John Bogovic, Albert Cardona, Barry DeZonia, Christian Dietz, Jan Funke,
 * Aivar Grislis, Jonathan Hale, Grant Harris, Stefan Helfrich, Mark Hiner,
 * Martin Horn, Steffen Jaensch, Lee Kamentsky, Larry Lindsey, Melissa Linkert,
 * Mark Longair, Brian Northan, Nick Perry, Curtis Rueden, Johannes Schindelin,
 * Jean-Yves Tinevez and Michael Zinsmaier.
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

package net.pietzsch.imglib.movingsum;

import net.imglib2.RandomAccess;
import net.imglib2.algorithm.convolution.LineConvolverFactory;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;

/**
 * TODO: REVISE JAVADOCS
 *
 * A 1-dimensional line convolver that operates on all {@link RealType}. It
 * implemented using a shifting window buffer that is stored in a small double[]
 * array.
 *
 * @author Tobias Pietzsch
 * @author Matthias Arzt
 *
 * @see LineConvolverFactory
 */
public final class SumDoubleType implements Runnable
{
	private final RandomAccess< DoubleType > in;

	private final RandomAccess< DoubleType > out;

	private final int d;

	private final int w;

	private final int w1;

	private final long linelen;

	private double sum;

	public SumDoubleType( final int width, final RandomAccess< DoubleType > in, final RandomAccess< DoubleType > out, final int d, final long lineLength )
	{
		// NB: This constructor is used in ConvolverFactories. It needs to be public and have this exact signature.
		this.in = in;
		this.out = out;
		this.d = d;

		w = width;
		w1 = width - 1;
		linelen = lineLength;

		history = new double[ w1 ];
		hi = 0;
	}

	private void prefill()
	{
		final double value = in.get().get();
		sum += value;
		record( value );
		in.fwd( d );
	}

	private void next()
	{
		final double value = in.get().get();
		sum += value;
		out.get().set( sum );
		sum -= record( value );
		in.fwd( d );
		out.fwd( d );
	}

	private final double[] history;

	private int hi;

	private double record( double value )
	{
		final double old = history[ hi ];
		history[ hi ] = value;
		if ( ++hi >= w1 )
			hi = 0;
		return old;
	}

	@Override
	public void run()
	{
		sum = 0;
		for ( int i = 0; i < w1; ++i )
			prefill();
		for ( long i = 0; i < linelen; ++i )
			next();
	}
}
