package net.pietzsch;

import bdv.util.Bdv;
import bdv.util.BdvFunctions;
import bdv.util.BdvSource;
import ij.IJ;
import ij.ImagePlus;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import mpicbg.spim.data.SpimDataException;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Util;
import net.imglib2.view.Views;
import net.pietzsch.ShiTomasiExample.Detection;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;

import static net.pietzsch.ShiTomasi.findMaxima;

public class PatchSelectionExampleBdv
{
	// sort...

	public static void main( String[] args ) throws ExecutionException, InterruptedException, SpimDataException
	{
		final double[] manualTranslation = { 0.20676331652784175, -15.975192686499792, 142.8073272106439 }; // Tile3
		final int[] expectedTranslation0 = { 0, -16, 143 };
		final int[] blockSize0 = { 71, 71, 11 };
		final int[] searchSpan0 = { 16, 16, 16 };

		final SpimData2 spimdata = new XmlIoSpimData2( null ).load( "/Users/pietzsch/Desktop/David Chen/renamed/dataset.xml" );
		final ScaleSizes scales = new ScaleSizes( spimdata, blockSize0, searchSpan0, expectedTranslation0 );
		final int level = 1;

		final RandomAccessibleInterval< UnsignedShortType > img1 = copyToArrayImg( scales.getImage( 0, level ) );
		final RandomAccessibleInterval< UnsignedShortType > img2 = copyToArrayImg( scales.getImage( 12, level ) );

		BdvSource sImg1 = BdvFunctions.show( img1, "img1" );
		Bdv bdv = sImg1;
		sImg1.setDisplayRange( 0, 255 );
		sImg1.setColor( new ARGBType( 0xff00ff ) );
		final AffineTransform3D img2Transform = new AffineTransform3D();
		img2Transform.translate( scales.scale( 0, level, manualTranslation ) );
		final BdvSource sImg2 = BdvFunctions.show( img2, "img2", Bdv.options().addTo( bdv ).sourceTransform( img2Transform ) );
		sImg2.setDisplayRange( 0, 255 );
		sImg2.setColor( new ARGBType( 0x00ff00 ) );

		final int[] blockSize = scales.blockSize( level );
		final int[] expectedTranslation = scales.expectedTranslation( level );
		ShiTomasi.Result result = findMaxima( img1, blockSize, 2, 10000 );

		final int n = img1.numDimensions();
		final long[] min = new long[ n ];
		final long[] max = new long[ n ];
		img1.min( min );
		img1.max( max );
		final long[] border = new long[ n ];
		Arrays.setAll( border, d -> -( blockSize[ d ] - 1 ) / 2 );
		final Interval expectedVisible = Intervals.expand( Intervals.intersect( img1, Intervals.translate( img1, Util.int2long( expectedTranslation ) ) ), border );

		final RandomAccess< DoubleType > ev = Views.hyperSlice( result.eigenvalues, n, n - 1 ).randomAccess();
		List< Detection > candidates = new ArrayList<>();
		result.maxima.forEach( point -> {
			if ( Intervals.contains( expectedVisible, point ) )
			{
				ev.setPosition( point );
				candidates.add( new Detection( point, ev.get().get() ) );
			}
		} );
		candidates.sort( Comparator.comparingDouble( Detection::getValue ).reversed() );

		List< Detection > selected = new ArrayList<>();
		for ( Detection candidate : candidates )
		{
			if ( selected.stream().allMatch( s -> Util.distance( s, candidate ) > 20 ) )
				selected.add( candidate );
		}

		// extract template
		Arrays.setAll( min, d -> - ( blockSize[ d ] - 1 ) / 2 );
		Arrays.setAll( max, d -> min[ d ] + blockSize[ d ] - 1 );
		final Interval templateInterval = new FinalInterval( min, max );

		int i = 0;
		final long[] center = new long[ n ];
		for ( Detection detection : selected )
		{
			++i;

			detection.localize( center );
			System.out.println( "[" + ( i - 1 ) + "]center = " + Arrays.toString( center ) );
			final RandomAccessibleInterval< UnsignedShortType > template = Views.translate( Views.interval( Views.translateInverse( Views.extendZero( img1 ), center ), templateInterval ), center );
			BdvSource ts = BdvFunctions.show( template, "template " + i, Bdv.options().addTo( bdv ) );
			ts.setDisplayRange( 0, 255 );


//			final long[] shiftedCenter = new long[ n ];
//			Arrays.setAll( shiftedCenter, d -> center[ d ] - expectedTranslation[ d ] );
//			final RandomAccessibleInterval< UnsignedShortType > region = Views.translate( Views.interval( Views.translateInverse( Views.extendZero( img2 ), shiftedCenter ), templateInterval ), center );
//			BdvFunctions.show( region, "region " + i, Bdv.options().addTo( bdv ) );

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
