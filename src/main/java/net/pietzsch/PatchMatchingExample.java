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
import net.imglib2.Dimensions;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.iterator.IntervalIterator;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.LinAlgHelpers;
import net.imglib2.util.Util;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import net.pietzsch.ShiTomasiExample.Detection;

import static net.pietzsch.ShiTomasi.findMaxima;

public class PatchMatchingExample
{
	// sort...

	public static void main( String[] args ) throws ExecutionException, InterruptedException
	{
		final double[] manualTranslation = { 0.20676331652784175, -15.975192686499792, 142.8073272106439 }; // Tile3

		ImagePlus imp1 = IJ.openImage( "/Users/pietzsch/Desktop/David Chen/20190210 - Liver180712 SeeDB DAPI GFP TdT for stitch slices/Slice1/DAPI/Slice1_DAPI_Tile3.tif" );
		ImagePlus imp2 = IJ.openImage( "/Users/pietzsch/Desktop/David Chen/20190210 - Liver180712 SeeDB DAPI GFP TdT for stitch slices/Slice2/DAPI/Slice2_DAPI_Tile3.tif" );
		final RandomAccessibleInterval< UnsignedByteType > img1 = copyToArrayImg( ImageJFunctions.wrapByte( imp1 ) );
		final RandomAccessibleInterval< UnsignedByteType > img2 = copyToArrayImg( ImageJFunctions.wrapByte( imp2 ) );
		Bdv bdv = BdvFunctions.show( img1, "img1" );
		BdvFunctions.show( img2, "img2", Bdv.options().addTo( bdv ) );

		final int[] blockSize = { 71, 71, 11 };
		ShiTomasi.Result result = findMaxima( img1, blockSize, 2, 10000 );

		final int n = img1.numDimensions();
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

		// extract template
		final long[] min = new long[ n ];
		final long[] max = new long[ n ];
		Arrays.setAll( min, d -> - ( blockSize[ d ] - 1 ) / 2 );
		Arrays.setAll( max, d -> min[ d ] + blockSize[ d ] - 1 );
		final Interval templateInterval = new FinalInterval( min, max );

		final int[] searchSpan = { 40, 40, 20 };
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

		int i = 0;
		for ( Detection detection : selected )
//		Detection detection = selected.get( 1 );
		{
			++i;

			final long[] center = new long[ n ];
			detection.localize( center );
			System.out.println( "center = " + Arrays.toString( center ) );
			final RandomAccessibleInterval< UnsignedByteType > template = Views.interval( Views.translateInverse( Views.extendZero( img1 ), center ), templateInterval );
			BdvFunctions.show( template, "template " + i, Bdv.options().addTo( bdv ) );

			for ( int d = 0; d < n; ++d )
				center[ d ] -= manualTranslation[ d ];
			final RandomAccessibleInterval< UnsignedByteType > region = Views.interval( Views.translateInverse( Views.extendZero( img2 ), center ), /*searchRegion*/templateInterval );
			BdvFunctions.show( region, "region " + i, Bdv.options().addTo( bdv ) );

			final Img< DoubleType > sadImg = new ArrayImgFactory<>( new DoubleType() ).create( sadInterval );
			RandomAccessibleInterval< DoubleType > sad = Views.translate( sadImg, min );

			System.out.println( Util.printInterval( sad ) );
//			IntervalIterator iter = new IntervalIterator(  )

			if ( i >= 10)
				break;
		}
	}

	private static < T extends NativeType< T > > RandomAccessibleInterval< T > copyToArrayImg( final RandomAccessibleInterval< T > img )
	{
		final Img< T > copy = new ArrayImgFactory<>( Util.getTypeFromInterval( img ) ).create( img );
		LoopBuilder.setImages( img, copy ).forEachPixel( ( i, o ) -> o.set( i ) );
		return copy;
	}
}
