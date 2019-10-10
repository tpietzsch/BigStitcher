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
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.iterator.IntervalIterator;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Util;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import net.pietzsch.ShiTomasiExample.Detection;

import static net.pietzsch.ShiTomasi.findMaxima;

public class SADExample
{
	public static void main( String[] args ) throws ExecutionException, InterruptedException
	{
		final double[] manualTranslation = { 0.20676331652784175, -15.975192686499792, 142.8073272106439 }; // Tile3
		final long[] expectedTranslation = { 0, -16, 143 };
		final long[] center = { 180, 62, 152 };

		ImagePlus imp1 = IJ.openImage( "/Users/pietzsch/Desktop/David Chen/20190210 - Liver180712 SeeDB DAPI GFP TdT for stitch slices/Slice1/DAPI/Slice1_DAPI_Tile3.tif" );
		ImagePlus imp2 = IJ.openImage( "/Users/pietzsch/Desktop/David Chen/20190210 - Liver180712 SeeDB DAPI GFP TdT for stitch slices/Slice2/DAPI/Slice2_DAPI_Tile3.tif" );
		final RandomAccessibleInterval< UnsignedByteType > img1 = copyToArrayImg( ImageJFunctions.wrapByte( imp1 ) );
		final RandomAccessibleInterval< UnsignedByteType > img2 = copyToArrayImg( ImageJFunctions.wrapByte( imp2 ) );

		final int[] blockSize = { 71, 71, 11 };
		final int n = img1.numDimensions();

		// extract template
		final long[] min = new long[ n ];
		final long[] max = new long[ n ];
		Arrays.setAll( min, d -> - ( blockSize[ d ] - 1 ) / 2 );
		Arrays.setAll( max, d -> min[ d ] + blockSize[ d ] - 1 );
		final Interval templateInterval = new FinalInterval( min, max );

		final int[] searchSpan = { 10, 10, 10 };
		for ( int d = 0; d < n; ++d )
		{
			min[ d ] -= searchSpan[ d ];
			max[ d ] += searchSpan[ d ];
		}
		final Interval searchRegion = new FinalInterval( min, max );

		for ( int d = 0; d < n; ++d )
		{
			min[ d ] = -searchSpan[ d ];
			max[ d ] = searchSpan[ d ];
		}
		final Interval sadInterval = new FinalInterval( min, max );

		System.out.println( "center = " + Arrays.toString( center ) );
		final IntervalView< UnsignedByteType > template = Views.interval( Views.translateInverse( Views.extendZero( img1 ), center ), templateInterval );

		final long[] centerImg2 =  new long[ n ];
		for ( int d = 0; d < n; ++d )
			centerImg2[ d ] = center[ d ] - expectedTranslation[ d ];
		final RandomAccessibleInterval< UnsignedByteType > region = Views.interval( Views.translateInverse( Views.extendZero( img2 ), centerImg2 ), searchRegion );

		Bdv bdv = BdvFunctions.show( img1, "img1" );
		BdvFunctions.show( img2, "img2", Bdv.options().addTo( bdv ) );
		BdvFunctions.show( template, "template", Bdv.options().addTo( bdv ) );
		BdvFunctions.show( region, "region", Bdv.options().addTo( bdv ) );

		final Img< DoubleType > sadImg = new ArrayImgFactory<>( new DoubleType() ).create( sadInterval );
		RandomAccessibleInterval< DoubleType > sad = Views.translate( sadImg, min );

		final Cursor< DoubleType > out = Views.iterable( sad ).cursor();
		final long nel = Intervals.numElements( sad );
		final long[] pos = new long[ n ];
		long el = 0;
		while ( out.hasNext() )
		{
			out.fwd();
			out.localize( pos );
			for ( int d = 0; d < n; ++d )
				pos[ d ] += centerImg2[ d ];
			final IntervalView< UnsignedByteType > in1I = template;
			final Cursor< UnsignedByteType > in1 = Views.flatIterable( in1I ).cursor();
			final IntervalView< UnsignedByteType > in2I = Views.interval( Views.translateInverse( Views.extendZero( img2 ), pos ), templateInterval );
			final Cursor< UnsignedByteType > in2 = Views.flatIterable( in2I ).cursor();
			double sum = 0;
			while ( in1.hasNext() )
				sum += Math.abs( in1.next().get() - in2.next().get() );
			out.get().set( sum );
			System.out.println( String.format( "%d / %d", ++el, nel ) );
		}

		BdvFunctions.show( sadImg, "sad" );
	}

	private static < T extends NativeType< T > > RandomAccessibleInterval< T > copyToArrayImg( final RandomAccessibleInterval< T > img )
	{
		final Img< T > copy = new ArrayImgFactory<>( Util.getTypeFromInterval( img ) ).create( img );
		LoopBuilder.setImages( img, copy ).forEachPixel( ( i, o ) -> o.set( i ) );
		return copy;
	}
}
