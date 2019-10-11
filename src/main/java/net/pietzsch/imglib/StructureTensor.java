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

package net.pietzsch.imglib;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.stream.IntStream;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.gauss3.Gauss3;
import net.imglib2.algorithm.gradient.PartialDerivative;
import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.outofbounds.OutOfBoundsFactory;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Intervals;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

/**
 * TODO: REVISE JAVADOC
 * Compute entries of n-dimensional structure tensor (second-moment matrix).
 *
 * @author Philipp Hanslovsky
 * @author Tobias Pietzsch
 */
public class StructureTensor
{
	/**
	 * @param source
	 *            n-dimensional input {@link RandomAccessibleInterval}
	 * @param gaussian
	 *            n-dimensional output parameter
	 *            {@link RandomAccessibleInterval}
	 * @param gradient
	 *            n+1-dimensional {@link RandomAccessibleInterval} for storing
	 *            the gradients along all axes of the smoothed source (size of
	 *            last dimension is n)
	 * @param structureTensor
	 *            n+1-dimensional {@link RandomAccessibleInterval} for storing
	 *            the upper triangular matrix of the structure tensor as
	 *            a linear representation
	 *            (size of last dimension is n * ( n + 1 ) / 2) : For n-dimensional input,
	 *            <code>structureTensor</code> (m) will be populated along the nth
	 *            dimension like this: [m11, m12, ... , m1n, m22, m23, ... ,
	 *            mnn]
	 * @param outOfBounds
	 *            {@link OutOfBoundsFactory} that specifies how out of bound
	 *            pixels of intermediate results should be handled (necessary
	 *            for gradient computation).
	 * @param nTasks
	 *            Number of tasks used for parallel computation of eigenvalues.
	 * @param es
	 *            {@link ExecutorService} providing workers for parallel
	 *            computation. Service is managed (created, shutdown) by caller.
	 * @param sigma
	 *            Scale for Gaussian smoothing.
	 *
	 * @return {@code structureTensor} that was passed as output parameter.
	 * @throws IncompatibleTypeException
	 * @throws ExecutionException
	 * @throws InterruptedException
	 */
	public static < T extends RealType< T >, U extends RealType< U > > RandomAccessibleInterval< U > calculateMatrix(
			final RandomAccessible< T > source,
			final RandomAccessibleInterval< U > gaussian,
			final RandomAccessibleInterval< U > gradient,
			final RandomAccessibleInterval< U > structureTensor,
			final OutOfBoundsFactory< U, ? super RandomAccessibleInterval< U > > outOfBounds,
			final int nTasks,
			final ExecutorService es,
			final double... sigma ) throws IncompatibleTypeException, InterruptedException, ExecutionException
	{

		if ( sigma.length == 1 )
			Gauss3.gauss( IntStream.range( 0, source.numDimensions() ).mapToDouble( i -> sigma[ 0 ] ).toArray(), source, gaussian, es );
		else
			Gauss3.gauss( sigma, source, gaussian, es );
		return calculateMatrix( Views.extend( gaussian, outOfBounds ), gradient, structureTensor, outOfBounds, nTasks, es );
	}

	/**
	 * @param source
	 *            n-dimensional pre-smoothed {@link RandomAccessible}. It is the
	 *            callers responsibility to smooth the input at the desired
	 *            scales.
	 * @param gradient
	 *            n+1-dimensional {@link RandomAccessibleInterval} for storing
	 *            the gradients along all axes of the smoothed source (size of
	 *            last dimension is n)
	 * @param structureTensor
	 *            n+1-dimensional {@link RandomAccessibleInterval} for storing
	 *            the upper triangular matrix of the structure tensor as
	 *            a linear representation
	 *            (size of last dimension is n * ( n + 1 ) / 2) : For n-dimensional input,
	 *            <code>structureTensor</code> (m) will be populated along the nth
	 *            dimension like this: [m11, m12, ... , m1n, m22, m23, ... ,
	 *            mnn]
	 * @param outOfBounds
	 *            {@link OutOfBoundsFactory} that specifies how out of bound
	 *            pixels of intermediate results should be handled (necessary
	 *            for gradient computation).
	 * @param nTasks
	 *            Number of tasks used for parallel computation of eigenvalues.
	 * @param es
	 *            {@link ExecutorService} providing workers for parallel
	 *            computation. Service is managed (created, shutdown) by caller.
	 *
	 * @return {@code structureTensor} that was passed as output parameter.
	 * @throws IncompatibleTypeException
	 * @throws ExecutionException
	 * @throws InterruptedException
	 */
	public static < T extends RealType< T > > RandomAccessibleInterval< T > calculateMatrix(
			final RandomAccessible< T > source,
			final RandomAccessibleInterval< T > gradient,
			final RandomAccessibleInterval< T > structureTensor,
			final OutOfBoundsFactory< T, ? super RandomAccessibleInterval< T > > outOfBounds,
			final int nTasks,
			final ExecutorService es ) throws IncompatibleTypeException, InterruptedException, ExecutionException
	{

		final int nDim = gradient.numDimensions() - 1;

		for ( long d = 0; d < nDim; ++d )
			PartialDerivative.gradientCentralDifferenceParallel( source, Views.hyperSlice( gradient, nDim, d ), ( int ) d, nTasks, es );

		return calculateMatrix( Views.extend( gradient, outOfBounds ), structureTensor );
	}

	/**
	 *
	 * @param gradient
	 *            n+1-dimensional {@link RandomAccessible} containing the
	 *            gradients along all axes of the smoothed source (size of last
	 *            dimension is n)
	 * @param structureTensor
	 *            n+1-dimensional {@link RandomAccessibleInterval} for storing
	 *            the upper triangular matrix of the structure tensor as
	 *            a linear representation
	 *            (size of last dimension is n * ( n + 1 ) / 2) : For n-dimensional input,
	 *            <code>structureTensor</code> (m) will be populated along the nth
	 *            dimension like this: [m11, m12, ... , m1n, m22, m23, ... ,
	 *            mnn]
	 *
	 * @return {@code structureTensor} that was passed as output parameter.
	 */
	public static < T extends RealType< T > > RandomAccessibleInterval< T > calculateMatrix(
			final RandomAccessible< T > gradient,
			final RandomAccessibleInterval< T > structureTensor )
	{
		final int nDim = gradient.numDimensions() - 1;

		final FinalInterval interval = Intervals.hyperSlice( structureTensor, nDim );

		long count = 0;
		for ( long d1 = 0; d1 < nDim; ++d1 )
		{
			final RandomAccessibleInterval< T > g1 = Views.interval( Views.hyperSlice( gradient, nDim, d1 ), interval );
			for ( long d2 = d1; d2 < nDim; ++d2 )
			{
				final RandomAccessibleInterval< T > g2 = Views.interval( Views.hyperSlice( gradient, nDim, d2 ), interval );
				final IntervalView< T > hs2 = Views.hyperSlice( structureTensor, nDim, count );
				LoopBuilder.setImages( g1, g2, hs2 ).multiThreaded().forEachPixel( ( a, b, c ) -> {
					c.set( a );
					c.mul( b );
				} );
				++count;
			}
		}
		return structureTensor;
	}
}