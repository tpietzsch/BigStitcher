package net.pietzsch;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.imglib2.Dimensions;
import net.imglib2.Point;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.linalg.eigen.TensorEigenValues;
import net.imglib2.algorithm.localextrema.LocalExtrema;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.outofbounds.OutOfBoundsBorderFactory;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;
import net.pietzsch.imglib.StructureTensor;
import net.pietzsch.imglib.movingsum.SeparableMovingSum;

public class ShiTomasi
{
	public static class Result
	{
		public final RandomAccessibleInterval< DoubleType > gaussian;

		public final RandomAccessibleInterval< DoubleType > gradient;

		public final RandomAccessibleInterval< DoubleType > structureTensor;

		public final RandomAccessibleInterval< DoubleType > structureTensorSum;

		public final RandomAccessibleInterval< DoubleType > eigenvalues;

		public final List< Point > maxima;

		public Result(
				final RandomAccessibleInterval< DoubleType > gaussian,
				final RandomAccessibleInterval< DoubleType > gradient,
				final RandomAccessibleInterval< DoubleType > structureTensor,
				final RandomAccessibleInterval< DoubleType > structureTensorSum,
				final RandomAccessibleInterval< DoubleType > eigenvalues,
				final List< Point > maxima )
		{
			this.gaussian = gaussian;
			this.gradient = gradient;
			this.structureTensor = structureTensor;
			this.structureTensorSum = structureTensorSum;
			this.eigenvalues = eigenvalues;
			this.maxima = maxima;
		}
	}

	public static < T extends RealType< T > > Result findMaxima( final RandomAccessibleInterval< T > img, final int blockSize, final double sigma, final int minValue )
	{
		final int[] windowSize = new int[ img.numDimensions() ];
		Arrays.fill( windowSize, blockSize );
		return findMaxima( img, windowSize, sigma, minValue );
	}

	public static < T extends RealType< T > > Result findMaxima( final RandomAccessibleInterval< T > img, final int[] blockSize, final double sigma, final int minValue )
	{
		final int numthreads = Runtime.getRuntime().availableProcessors();
		final ExecutorService es = Executors.newFixedThreadPool( numthreads );
		try
		{
			final int n = img.numDimensions();
			final ArrayImgFactory< DoubleType > doubleImgFactory = new ArrayImgFactory<>( new DoubleType() );

			final RandomAccessibleInterval< DoubleType > gaussian = doubleImgFactory.create( img );

			final Dimensions gradientDims = Intervals.addDimension( img, 0, n - 1 );
			final RandomAccessibleInterval< DoubleType > gradient = doubleImgFactory.create( gradientDims );

			final Dimensions structureTensorDims = Intervals.addDimension( img, 0, n * ( n + 1 ) / 2 - 1 );
			final RandomAccessibleInterval< DoubleType > structureTensor = doubleImgFactory.create( structureTensorDims );

			final int nTasks = numthreads * 8;

			final double[] sigmas = new double[ n ];
			Arrays.fill( sigmas, 2 );

			StructureTensor.calculateMatrix(
					Views.extendBorder( img ), // final RandomAccessible< T > source,
					gaussian, // final RandomAccessibleInterval< U > gaussian,
					gradient, // final RandomAccessibleInterval< U > gradient,
					structureTensor, // final RandomAccessibleInterval< U > hessianMatrix,
					new OutOfBoundsBorderFactory<>(), // final OutOfBoundsFactory< U, ? super RandomAccessibleInterval< U > > outOfBounds,
					nTasks, es,
					sigmas );

			final RandomAccessibleInterval< DoubleType > structureTensorSum = doubleImgFactory.create( structureTensorDims );

			for ( int i = 0; i < n * ( n + 1 ) / 2; ++i )
			{
				SeparableMovingSum.convolve( blockSize,
						Views.extendBorder( Views.hyperSlice( structureTensor, n, i ) ),
						Views.hyperSlice( structureTensorSum, n, i ) );
			}

			final RandomAccessibleInterval< DoubleType > eigenvalues = TensorEigenValues.calculateEigenValuesSymmetric(
					structureTensorSum,
					TensorEigenValues.createAppropriateResultImg( structureTensorSum, doubleImgFactory ) );

			final List< Point > maxima = LocalExtrema.findLocalExtrema(
					Views.hyperSlice( eigenvalues, n, n - 1 ),
					new LocalExtrema.MaximumCheck<>( new DoubleType( minValue ) ) );

			return new Result( gaussian, gradient, structureTensor, structureTensorSum, eigenvalues, maxima );
		}
		catch ( InterruptedException | ExecutionException e )
		{
			throw new RuntimeException( e );
		}
		finally
		{
			es.shutdown();
		}
	}
}
