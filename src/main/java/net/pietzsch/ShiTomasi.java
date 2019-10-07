package net.pietzsch;

import bdv.util.Bdv;
import bdv.util.BdvFunctions;
import ij.IJ;
import ij.ImagePlus;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.imglib2.Dimensions;
import net.imglib2.Localizable;
import net.imglib2.Point;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.linalg.eigen.TensorEigenValues;
import net.imglib2.algorithm.localextrema.LocalExtrema;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import net.imglib2.algorithm.neighborhood.RectangleShape;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.outofbounds.OutOfBoundsBorderFactory;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Util;
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


	public static Result shiTomasi( final RandomAccessibleInterval< UnsignedByteType > img, final int blockSize, final double sigma, final int minValue )
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
				final int[] windowSize = new int[ n ];
				Arrays.fill( windowSize, blockSize );
				SeparableMovingSum.convolve( windowSize,
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



	// sort...

	static class Detection implements Localizable
	{
		public final Localizable point;

		private final double value;

		public Detection( final Localizable point, final double value )
		{
			this.point = point;
			this.value = value;
		}

		@Override
		public boolean equals( final Object o )
		{
			if ( this == o )
				return true;
			if ( !( o instanceof Detection ) )
				return false;

			final Detection detection = ( Detection ) o;

			if ( Double.compare( detection.value, value ) != 0 )
				return false;
			return point.equals( detection.point );
		}

		@Override
		public int hashCode()
		{
			int result;
			long temp;
			result = point.hashCode();
			temp = Double.doubleToLongBits( value );
			result = 31 * result + ( int ) ( temp ^ ( temp >>> 32 ) );
			return result;
		}

		public double getValue()
		{
			return value;
		}

		@Override
		public long getLongPosition( final int d )
		{
			return point.getLongPosition( d );
		}

		@Override
		public int numDimensions()
		{
			return point.numDimensions();
		}
	}

	public static void main( String[] args ) throws ExecutionException, InterruptedException
	{
		ImagePlus imp = IJ.openImage( "/Users/pietzsch/Desktop/David Chen/20190210 - Liver180712 SeeDB DAPI GFP TdT for stitch slices/Slice1/DAPI/Slice1_DAPI_Tile1.tif" );
		final RandomAccessibleInterval< UnsignedByteType > img = copyToArrayImg( ImageJFunctions.wrapByte( imp ) );
		Bdv bdv = BdvFunctions.show( img, "img" );

		final int blockSize = 71;
		final int n = img.numDimensions();
		Result result = shiTomasi( img, blockSize, 2, 10000 );



		BdvFunctions.show( result.gaussian, "gaussian", Bdv.options().addTo( bdv ) );
		BdvFunctions.show( result.gradient, "gradient", Bdv.options().addTo( bdv ) );
		BdvFunctions.show( result.structureTensor, "structure", Bdv.options().addTo( bdv ) );
		BdvFunctions.show( result.structureTensorSum, "structureSum", Bdv.options().addTo( bdv ) );
		BdvFunctions.show( result.eigenvalues, "eigenvalues", Bdv.options().addTo( bdv ) );



		final RandomAccess< DoubleType > ev = Views.hyperSlice( result.eigenvalues, n, n - 1 ).randomAccess();
		List< Detection > candidates = new ArrayList<>();
		result.maxima.forEach( point -> {
			ev.setPosition( point );
			candidates.add( new Detection( point, ev.get().get() ) );
		} );
		candidates.sort( Comparator.comparingDouble( Detection::getValue ).reversed() );

		List< Detection > selected = new ArrayList<>();
		for ( Detection candidate : candidates )
		{
			if ( selected.stream().allMatch( s -> Util.distance( s, candidate ) > 20 ) )
				selected.add( candidate );
		}

		final ArrayImg< UnsignedByteType, ? > maxima = new ArrayImgFactory<>( new UnsignedByteType() ).create( img );
		final int span = ( blockSize - 1 ) / 2;
		final RandomAccess< Neighborhood< UnsignedByteType > > ra = new RectangleShape( span, false ).neighborhoodsRandomAccessible( Views.extendZero( maxima ) ).randomAccess();
		selected.forEach( point -> {
			ra.setPosition( point );
			ra.get().forEach( t -> t.set( Math.min( 255, t.get() + 64 ) ) );
		} );
//		final ArrayRandomAccess< UnsignedByteType > maximaRA = maxima.randomAccess();
//		result.maxima.forEach( point -> {
//			maximaRA.setPosition( point );
//			maximaRA.get().set( 255 );
//		} );

		BdvFunctions.show( maxima, "maxima", Bdv.options().addTo( bdv ) );
	}

	private static < T extends NativeType< T > > RandomAccessibleInterval< T > copyToArrayImg( final RandomAccessibleInterval< T > img )
	{
		final Img< T > copy = new ArrayImgFactory<>( Util.getTypeFromInterval( img ) ).create( img );
		LoopBuilder.setImages( img, copy ).forEachPixel( ( i, o ) -> o.set( i ) );
		return copy;
	}
}
