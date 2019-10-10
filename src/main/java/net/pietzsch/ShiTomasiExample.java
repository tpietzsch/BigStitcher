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
import net.imglib2.Localizable;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.neighborhood.CenteredRectangleShape;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import net.imglib2.algorithm.neighborhood.RectangleShape;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Util;
import net.imglib2.view.Views;

import static net.pietzsch.ShiTomasi.findMaxima;

public class ShiTomasiExample
{
	// sort...

	public static class Detection implements Localizable
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

		final int[] blockSize = { 71, 71, 11 };
		final int n = img.numDimensions();
		ShiTomasi.Result result = findMaxima( img, blockSize, 2, 10000 );



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
		final int[] span = new int[ n ];
		Arrays.setAll( span, d -> ( blockSize[ d ] - 1 )/ 2 );
		final RandomAccess< Neighborhood< UnsignedByteType > > ra = new CenteredRectangleShape( span,false ).neighborhoodsRandomAccessible( Views.extendZero( maxima ) ).randomAccess();
		selected.forEach( point -> {
			ra.setPosition( point );
			ra.get().forEach( t -> t.set( Math.min( 255, t.get() + 64 ) ) );
		} );
//		final RandomAccess< Neighborhood< UnsignedByteType > > ra = new RectangleShape( span, false ).neighborhoodsRandomAccessible( Views.extendZero( maxima ) ).randomAccess();
//		selected.forEach( point -> {
//			ra.setPosition( point );
//			ra.get().forEach( t -> t.set( Math.min( 255, t.get() + 64 ) ) );
//		} );
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
